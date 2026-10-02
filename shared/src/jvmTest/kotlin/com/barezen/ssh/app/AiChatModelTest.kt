// shared/src/jvmTest/kotlin/com/barezen/ssh/app/AiChatModelTest.kt
package com.barezen.ssh.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-7：AI 对话模型。fake transport 零网络，SSE 解析/重试/中断/上下文全在进程内。 */
class AiChatModelTest {

    private val json = Json { ignoreUnknownKeys = true }

    private class FakeTransport(
        private val responder: (attempt: Int) -> Flow<String>,
    ) : AiTransport {
        val calls = mutableListOf<AiRequest>()
        override fun stream(request: AiRequest): Flow<String> {
            calls += request
            return responder(calls.size - 1)
        }
    }

    private fun configOf() = AiConfig("https://api.example.com/v1", "test-model", "sk-test")

    /** 单条增量 → 一条 SSE data 事件（含尾部空行）。 */
    private fun sse(content: String): String {
        val wire = AiChatChunk(choices = listOf(AiChatChoice(delta = AiChatDelta(content))))
        return "data: " + json.encodeToString(wire) + "\n\n"
    }

    private fun model(
        scope: CoroutineScope,
        transport: AiTransport,
        config: AiConfig? = configOf(),
    ) = AiChatModel(scope, transport) { config }

    // ---- SSE 解析 ----

    @Test
    fun parserAssemblesAcrossChunks() {
        val parser = SseParser()
        val payload = "data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\n\n"
        // 故意从行中间切开：跨块行缓冲必须能拼回
        val cut = payload.length / 2
        val events = parser.feed(payload.take(cut)) + parser.feed(payload.drop(cut)) + parser.finish()
        assertEquals(1, events.size)
        val data = events[0] as SseEvent.Data
        assertContains(data.payload, "\"hi\"")
    }

    @Test
    fun parserJoinsMultilineDataAndStopsAtDone() {
        val parser = SseParser()
        val events = parser.feed(": keep-alive\n\n") +
            parser.feed("data: line1\ndata: line2\n\n") +
            // event:/retry: 字段行本身被忽略（元数据），其后的 data 行照常分发（SSE 规范）
            parser.feed("event: ping\nretry: 3000\ndata: ping-data\n\n") +
            parser.feed("data: [DONE]\n\n") +
            parser.finish()
        assertEquals(
            listOf<SseEvent>(SseEvent.Data("line1\nline2"), SseEvent.Data("ping-data"), SseEvent.Done),
            events,
        )
    }

    @Test
    fun parserFinishFlushesPendingLine() {
        val parser = SseParser()
        assertEquals(0, parser.feed("data: no-trailing-newline").size)
        val events = parser.finish()
        assertEquals(listOf<SseEvent>(SseEvent.Data("no-trailing-newline")), events)
    }

    // ---- 发送与流式渲染 ----

    @Test
    fun streamsDeltaIntoAssistantBubble() = runTest {
        val transport = FakeTransport { flow { emit(sse("ls -la")); emit(sse(" /home")); emit("data: [DONE]\n\n") } }
        val chat = model(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), transport)
        chat.send("看下家目录")
        assertEquals(listOf(AiRole.USER, AiRole.ASSISTANT), chat.messages.map { it.role })
        assertEquals("ls -la /home", chat.messages[1].text)
        assertFalse(chat.messages[1].streaming)
        assertFalse(chat.isStreaming)
        assertNull(chat.error)
        assertEquals(1, transport.calls.size)
    }

    @Test
    fun sendsSystemPromptAndHistoryToWire() = runTest {
        val transport = FakeTransport { flow { emit(sse("ok")); emit("data: [DONE]\n\n") } }
        val chat = model(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), transport)
        chat.send("第一条", listOf("proc running", "disk 80%"))
        chat.send("第二条")
        assertEquals(2, transport.calls.size)
        val first = transport.calls[0]
        assertContains(first.system, "最后 2 行")
        assertContains(first.system, "proc running\ndisk 80%")
        assertEquals(1, first.history.size)
        assertEquals(AiRole.USER, first.history[0].role)
        assertEquals("第一条", first.history[0].content)
        // 第二次发送时首轮对话进入 history
        val second = transport.calls[1]
        assertEquals(3, second.history.size) // user1 + assistant1 + user2
        assertContains(second.history[1].content, "ok")
    }

    @Test
    fun terminalContextRecordedAsAttachedLines() = runTest {
        val transport = FakeTransport { flow { emit("data: [DONE]\n\n") } }
        val chat = model(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), transport)
        chat.send("帮我看看", listOf("a", "b", "c"))
        assertEquals(3, chat.messages[0].attachedLines)
        chat.send("不再附带")
        assertEquals(0, chat.messages[2].attachedLines)
    }

    // ---- 失败与重试 ----

    @Test
    fun networkFailureRetriesOnceThenSucceeds() = runTest {
        val transport = FakeTransport { attempt ->
            if (attempt == 0) flow { throw java.io.IOException("connection reset") }
            else flow { emit(sse("recovered")); emit("data: [DONE]\n\n") }
        }
        val chat = model(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), transport)
        chat.send("重试场景")
        assertEquals("recovered", chat.messages[1].text)
        assertNull(chat.error)
        assertEquals(2, transport.calls.size)
    }

    @Test
    fun twoFailuresReportAndDropEmptyBubble() = runTest {
        val transport = FakeTransport { flow { throw java.io.IOException("timeout") } }
        val chat = model(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), transport)
        chat.send("两次失败")
        assertEquals(1, chat.messages.size) // 空的流式占位被移除
        assertEquals(AiRole.USER, chat.messages[0].role)
        assertContains(chat.error ?: "", "网络错误")
        assertContains(chat.error ?: "", "已重试 1 次")
        assertFalse(chat.isStreaming)
        assertEquals(2, transport.calls.size)
    }

    @Test
    fun partialTextKeptAfterTwoFailures() = runTest {
        val transport = FakeTransport { flow { emit(sse("半截")); throw java.io.IOException("boom") } }
        val chat = model(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), transport)
        chat.send("半截场景")
        // 两次尝试各写入一次「半截」，已接收部分必须保留而非清空
        assertContains(chat.messages[1].text, "半截")
        assertFalse(chat.messages[1].streaming)
        assertContains(chat.error ?: "", "网络错误")
    }

    @Test
    fun apiErrorInStreamIsSurfaced() = runTest {
        val transport = FakeTransport { flow { emit("data: {\"error\":{\"message\":\"bad key\"}}\n\n") } }
        val chat = model(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), transport)
        chat.send("坏 key")
        assertContains(chat.error ?: "", "bad key")
        assertEquals(1, chat.messages.size)
    }

    // ---- 中断与会话 ----

    @Test
    fun interruptionKeepsReceivedText() = runTest {
        val transport = FakeTransport {
            flow {
                emit(sse("已收到的部分"))
                awaitCancellation() // 流被对端挂住，等待用户中断
            }
        }
        val chat = model(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), transport)
        chat.send("中断场景")
        assertTrue(chat.isStreaming)
        assertEquals("已收到的部分", chat.messages[1].text)
        chat.stopStreaming()
        advanceUntilIdle()
        assertFalse(chat.isStreaming)
        assertEquals("已收到的部分", chat.messages[1].text) // 中断不丢已接收文本
        assertNull(chat.error) // 主动中断不算错误
        assertFalse(chat.messages[1].streaming)
    }

    @Test
    fun newSessionClearsEverything() = runTest {
        val transport = FakeTransport { flow { emit(sse("x")); emit("data: [DONE]\n\n") } }
        val chat = model(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), transport)
        chat.send("旧会话")
        assertEquals(2, chat.messages.size)
        chat.newSession()
        assertTrue(chat.messages.isEmpty())
        assertNull(chat.error)
        assertFalse(chat.isStreaming)
    }

    // ---- 未配置 ----

    @Test
    fun noConfigSetsErrorAndSendsNothing() = runTest {
        val transport = FakeTransport { flow { emit("data: [DONE]\n\n") } }
        val chat = model(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), transport, config = null)
        chat.send("未配置场景")
        assertEquals(0, chat.messages.size)
        assertContains(chat.error ?: "", "未配置")
        assertEquals(0, transport.calls.size)
        assertFalse(chat.isStreaming)
    }

    @Test
    fun streamingSendIsIgnored() = runTest {
        val transport = FakeTransport { flow { emit(sse("a")); awaitCancellation() } }
        val chat = model(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), transport)
        chat.send("第一条")
        chat.send("第二条") // isStreaming 中：直接忽略
        assertEquals(1, transport.calls.size)
        assertEquals(2, chat.messages.size)
    }

    // ---- 命令块检测 ----

    @Test
    fun extractCommandsFromFencedBlock() {
        val text = "看这个：\n```bash\nuptime\ndf -h\n```\n完毕"
        assertEquals(listOf("uptime", "df -h"), extractCommands(text))
    }

    @Test
    fun extractCommandsFromPromptLines() {
        val text = "先看磁盘：\n$ df -h\n说明文字\n$ free -m"
        assertEquals(listOf("df -h", "free -m"), extractCommands(text))
    }

    @Test
    fun extractCommandsDeduplicatesInOrder() {
        val text = "```\nuptime\n```\n$ uptime"
        assertEquals(listOf("uptime"), extractCommands(text))
    }

    @Test
    fun extractCommandsEmptyForPlainProse() {
        assertEquals(emptyList(), extractCommands("这只是普通说明，没有命令。"))
    }
}
