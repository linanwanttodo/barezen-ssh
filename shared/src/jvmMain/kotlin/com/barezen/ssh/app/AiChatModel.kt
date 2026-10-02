// shared/src/jvmMain/kotlin/com/barezen/ssh/app/AiChatModel.kt（T-7：P6 AI 运维侧栏）
package com.barezen.ssh.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.barezen.ssh.credentials.CredentialStore
import com.barezen.ssh.credentials.CredentialStoreException
import com.barezen.ssh.credentials.CredentialStores
import com.barezen.ssh.credentials.KeychainCredentialResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * AI 运维侧栏（T-7）对话模型：OpenAI 兼容 /v1/chat/completions 的流式对话。
 *
 * 边界（T-7 任务书硬安全边界）：
 * - **不自行取终端内容**：上下文由 UI 层从 [com.barezen.ssh.terminal.TerminalBridge]
 *   取只读快照后经 [send] 传入；本类只负责把它并进 system 消息；
 * - **命令注入不经本类**：解析出的候选命令由 UI 走审批对话框后直接写 ShellChannel；
 * - 网络失败重试一次，再失败即报错；流被中断时保留已接收文本，不报错；
 * - 传输抽象为 [AiTransport]，测试零真实网络。
 */
class AiChatModel(
    private val scope: CoroutineScope,
    private val transport: AiTransport,
    /** 每次发送时求值：endpoint/model 来自设置、key 来自钥匙串；任一缺失返回 null（未配置）。 */
    val configProvider: () -> AiConfig?,
) {
    var messages by mutableStateOf<List<AiMessage>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var isStreaming by mutableStateOf(false)
        private set

    private var job: Job? = null

    /** 开新会话：中断在途流、清空消息与错误。 */
    fun newSession() {
        job?.cancel()
        job = null
        messages = emptyList()
        error = null
        isStreaming = false
    }

    /** 用户主动中断流式输出：已接收文本保留（T-7），不算错误。 */
    fun stopStreaming() {
        job?.cancel()
    }

    /**
     * 发送一条用户消息。[terminalContext] 为活动会话滚动缓冲区的只读快照
     *（UI 层决定是否附带），非空时并入 system 消息；随消息记录行数供 UI 显示
     * 「已附带终端输出 N 行」。
     */
    fun send(userText: String, terminalContext: List<String>? = null) {
        if (isStreaming) return
        val config = configProvider()
        if (config == null) {
            error = "未配置：请先在设置-智能助手填写 API endpoint、模型名称与 API key"
            return
        }
        error = null
        val system = buildSystemPrompt(terminalContext)
        messages += AiMessage(AiRole.USER, userText, attachedLines = terminalContext?.size ?: 0)
        messages += AiMessage(AiRole.ASSISTANT, "", streaming = true)
        val history = messages.dropLast(1)
            .filter { it.text.isNotBlank() }
            .map { AiTurn(it.role, it.text) }
        isStreaming = true
        job = scope.launch {
            var attempts = 0
            while (true) {
                attempts++
                try {
                    val parser = SseParser()
                    transport.stream(AiRequest(config.chatUrl, config.key, config.model, system, history))
                        .collect { chunk ->
                            parser.feed(chunk).forEach { event -> dispatch(event) }
                        }
                    parser.finish().forEach { event -> dispatch(event) }
                    finishLast()
                    break
                } catch (e: CancellationException) {
                    // 中断/新会话：保留已接收文本，不报错
                    finishLast()
                    break
                } catch (e: Exception) {
                    if (attempts >= 2) {
                        // 两次都失败：空回复的占位气泡移除，非空保留已接收部分
                        if (messages.lastOrNull()?.text.isNullOrBlank()) {
                            messages = messages.dropLast(1)
                        } else {
                            finishLast()
                        }
                        isStreaming = false
                        error = describe(e)
                        break
                    }
                    // 网络失败重试一次
                }
            }
        }
    }

    private fun dispatch(event: SseEvent) {
        when (event) {
            is SseEvent.Data -> appendDelta(event.payload)
            SseEvent.Done -> Unit
        }
    }

    private fun appendDelta(payload: String) {
        if (payload.isEmpty()) return
        val parsed = runCatching { AI_JSON.decodeFromString<AiChatChunk>(payload) }.getOrNull() ?: return
        parsed.error?.message?.let { throw AiHttpException(200, it) }
        val content = parsed.choices.firstOrNull()?.delta?.content ?: return
        if (content.isEmpty()) return
        val last = messages.lastOrNull() ?: return
        if (last.role != AiRole.ASSISTANT || !last.streaming) return
        messages = messages.dropLast(1) + last.copy(text = last.text + content)
    }

    private fun finishLast() {
        val last = messages.lastOrNull()
        if (last != null && last.streaming) {
            messages = messages.dropLast(1) + last.copy(streaming = false)
        }
        isStreaming = false
    }

    private fun describe(e: Exception): String = when (e) {
        is AiHttpException -> "API 错误（HTTP ${e.status}）：${e.bodySnippet.take(200)}"
        else -> "网络错误：${e.message ?: e::class.simpleName}（已重试 1 次）"
    }

    private companion object {
        /**
         * system 提示词：要求模型把可执行命令放进代码块或「$ 」提示符行，
         * 这样 extractCommands 才能识别出注入候选（审批流的前提）。
         */
        const val SYSTEM_PROMPT =
            "你是 BareZen-SSH 终端里的运维助手。回答保持简短、可直接执行。" +
                "给出可执行命令时，用单独的代码块或以提示符 + 空格开头的行书写，" +
                "便于用户审阅后注入终端。"

        fun buildSystemPrompt(terminalContext: List<String>?): String {
            if (terminalContext.isNullOrEmpty()) return SYSTEM_PROMPT
            return SYSTEM_PROMPT +
                "\n\n以下是当前终端会话的最后 " + terminalContext.size + " 行输出，供参考：\n" +
                terminalContext.joinToString("\n")
        }

        val AI_JSON = Json { ignoreUnknownKeys = true }
    }
}

/** 一次发送所需的完整配置；endpoint 已收敛为无尾斜杠的 http(s) 前缀（填到 /v1）。 */
data class AiConfig(val endpoint: String, val model: String, val key: String) {
    /** OpenAI 兼容约定：endpoint 填到 /v1，请求地址拼接 /chat/completions。 */
    val chatUrl: String
        get() = endpoint.trimEnd('/') + "/chat/completions"
}

enum class AiRole(val wire: String) { USER("user"), ASSISTANT("assistant") }

/** 侧栏消息。attachedLines：发送时附带的终端输出行数（0 = 未附带）。 */
data class AiMessage(
    val role: AiRole,
    val text: String,
    val streaming: Boolean = false,
    val attachedLines: Int = 0,
)

/** 传输请求：url 为完整请求地址（含 /chat/completions）。 */
class AiRequest(
    val url: String,
    val key: String,
    val model: String,
    val system: String,
    val history: List<AiTurn>,
)

data class AiTurn(val role: AiRole, val content: String)

/**
 * 传输抽象：返回 SSE 原始行流（保留空行与换行）。实现不得抛出检测型异常以外的
 * 信息约束；网络层失败抛 IOException，服务端返回非 2xx 抛 [AiHttpException]。
 */
interface AiTransport {
    fun stream(request: AiRequest): Flow<String>
}

class AiHttpException(val status: Int, val bodySnippet: String) :
    RuntimeException("HTTP $status: $bodySnippet")

/** SSE 事件：Data = 一条 data 载荷（多行 data 已按规范以 \n 连接）；Done = [DONE] 终止。 */
sealed interface SseEvent {
    data class Data(val payload: String) : SseEvent
    data object Done : SseEvent
}

/**
 * 容错 SSE 解析器（逐字符块喂入）：
 * - 跨块行缓冲（chunk 可能切断任意行）；
 * - 同一事件的多条 data 行按 SSE 规范以 \n 连接，空行结束一个事件；
 * - 忽略注释（: 开头）与 event:/id:/retry: 字段；
 * - data 载荷 [DONE] 映射为 [SseEvent.Done]；
 * - finish() 冲洗行缓冲中未换行结尾的残余。
 */
class SseParser {
    private val pendingLine = StringBuilder()
    private val dataLines = mutableListOf<String>()

    fun feed(chunk: String): List<SseEvent> {
        val events = mutableListOf<SseEvent>()
        val combined = pendingLine.toString() + chunk
        pendingLine.setLength(0)
        val lines = combined.split("\n")
        for (i in 0 until lines.size - 1) {
            handleLine(lines[i], events)
        }
        pendingLine.append(lines.last())
        return events
    }

    fun finish(): List<SseEvent> {
        val events = mutableListOf<SseEvent>()
        if (pendingLine.isNotEmpty()) {
            handleLine(pendingLine.toString(), events)
            pendingLine.setLength(0)
        }
        flushEvent(events)
        return events
    }

    private fun handleLine(rawLine: String, out: MutableList<SseEvent>) {
        val line = rawLine.trimEnd('\r')
        when {
            line.isEmpty() -> flushEvent(out)
            line.startsWith(":") -> Unit
            line.startsWith("data:") -> dataLines += line.removePrefix("data:").removePrefix(" ")
        }
    }

    private fun flushEvent(out: MutableList<SseEvent>) {
        if (dataLines.isEmpty()) return
        val payload = dataLines.joinToString("\n")
        dataLines.clear()
        out += if (payload == "[DONE]") SseEvent.Done else SseEvent.Data(payload)
    }
}

// ---- OpenAI 兼容线上协议 DTO（只取本产品用到的字段；未知字段一律忽略） ----

@Serializable
internal data class AiWireRequest(
    val model: String,
    val messages: List<AiWireMessage>,
    val stream: Boolean = true,
)

@Serializable
internal data class AiWireMessage(val role: String, val content: String)

@Serializable
internal data class AiChatChunk(
    val choices: List<AiChatChoice> = emptyList(),
    val error: AiApiError? = null,
)

@Serializable
internal data class AiChatChoice(val delta: AiChatDelta = AiChatDelta())

@Serializable
internal data class AiChatDelta(val content: String? = null)

@Serializable
internal data class AiApiError(val message: String? = null)

/** 生产传输：java.net.http.HttpClient + InputStream 逐行读。阻塞调用压在 IO 线程。 */
class JavaAiHttpTransport(
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
) : AiTransport {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun stream(request: AiRequest): Flow<String> = flow {
        val wire = AiWireRequest(
            model = request.model,
            messages = buildList {
                add(AiWireMessage("system", request.system))
                request.history.forEach { add(AiWireMessage(it.role.wire, it.content)) }
            },
        )
        val httpRequest = HttpRequest.newBuilder(URI.create(request.url))
            .header("Authorization", "Bearer " + request.key)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(wire)))
            .build()
        val response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) {
            val text = response.body().use { it.readNBytes(4096).toString(Charsets.UTF_8) }
            throw AiHttpException(response.statusCode(), text.take(300))
        }
        response.body().use { stream ->
            val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
            while (true) {
                val line = reader.readLine() ?: break
                emit(line + "\n")
            }
        }
    }.flowOn(Dispatchers.IO)
}

// ---- 命令块检测（注入候选） ----

private val FENCED_BLOCK = Regex("""```[\w+-]*\r?\n([\s\S]*?)```""")
private val PROMPT_LINE = Regex("""^[$]\s+(.+)$""")

/**
 * 从 AI 回复中提取注入候选命令（T-7：检测代码块/单行命令）：
 * - 围栏代码块（``` ... ```）内的每个非空行；
 * - 去掉代码块后正文里「$ 」提示符开头的行。
 * 结果按出现顺序去重；不含任何候选时返回空列表。
 */
fun extractCommands(text: String): List<String> {
    val commands = mutableListOf<String>()
    FENCED_BLOCK.findAll(text).forEach { match ->
        match.groupValues[1].lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { commands += it }
    }
    val rest = FENCED_BLOCK.replace(text, "")
    rest.lines().forEach { raw ->
        PROMPT_LINE.matchEntire(raw.trim())?.let { m ->
            val cmd = m.groupValues[1].trim()
            if (cmd.isNotEmpty()) commands += cmd
        }
    }
    return commands.distinct()
}

/**
 * AI API key 存储（jvmMain 实现，T-1 模式）：原生钥匙串可用则读写真钥匙串，
 * 否则降级内存存储，且 [AiKeyStore.keychainAvailable] 如实为 false，UI 明示
 * 「仅保存在内存」。key 只存钥匙串，绝不进 settings.json。
 */
class PlatformAiKeyStore(
    private val store: CredentialStore,
    private val nativeAvailable: Boolean,
) : AiKeyStore {

    override fun loadKey(): String? = try {
        store.load(AiKeyStore.KEY_ID)?.let { String(it) }?.takeIf { it.isNotBlank() }
    } catch (e: CredentialStoreException) {
        null
    }

    override fun saveKey(key: String) {
        try {
            store.save(AiKeyStore.KEY_ID, key.toCharArray())
        } catch (e: CredentialStoreException) {
            // 钥匙串拒绝写入：静默降级，下次 load 为 null 即可被发现
        }
    }

    override fun deleteKey() {
        try {
            store.delete(AiKeyStore.KEY_ID)
        } catch (e: CredentialStoreException) {
            // 幂等语义：条目缺失或底层不可用都视为已删除
        }
    }

    override val keychainAvailable: Boolean = nativeAvailable

    companion object {
        /** 平台入口：选型与 KeychainCredentialResolver 同源（复用其探测逻辑）。 */
        fun platformDefault(): PlatformAiKeyStore = PlatformAiKeyStore(
            store = CredentialStores.platformDefault(),
            nativeAvailable = KeychainCredentialResolver.probeNativeStore() != null,
        )
    }
}
