// shared/src/jvmTest/kotlin/com/barezen/ssh/terminal/TerminalBridgeTest.kt
package com.barezen.ssh.terminal

import com.barezen.ssh.ssh.ShellChannel
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.CharBuffer
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** T-7：终端桥只读快照 + 审批后写入。真实 jediterm 缓冲区，零网络。 */
class TerminalBridgeTest {

    private class FakeShellChannel : ShellChannel {
        val written = mutableListOf<ByteArray>()
        var closed = false
        override fun write(bytes: ByteArray) { written += bytes }
        override fun resize(cols: Int, rows: Int) {}
        override fun close() { closed = true }
    }

    private fun buffer(vararg lines: String): TerminalTextBuffer {
        val buf = TerminalTextBuffer(80, 24, StyleState(), 1000)
        lines.forEach { text -> buf.screenBuffer.addNewLine(TextStyle.EMPTY, CharBuffer(text)) }
        return buf
    }

    @Test
    fun lastLinesBeforeAttachIsEmpty() {
        val bridge = TerminalBridge()
        assertTrue(bridge.lastLines(10).isEmpty())
    }

    @Test
    fun lastLinesReturnsScreenTail() {
        val bridge = TerminalBridge()
        val channel = FakeShellChannel()
        bridge.attach(buffer("line one", "line two", "line three"), channel)
        assertEquals(listOf("line two", "line three"), bridge.lastLines(2))
    }

    @Test
    fun lastLinesMergesHistoryThenScreen() {
        val buf = buffer("old line", "older line")
        buf.moveScreenLinesToHistory() // 两行进入 history，屏幕清空
        buf.screenBuffer.addNewLine(TextStyle.EMPTY, CharBuffer("new line"))
        val bridge = TerminalBridge()
        bridge.attach(buf, FakeShellChannel())
        assertEquals(listOf("old line", "older line", "new line"), bridge.lastLines(3))
    }

    @Test
    fun lastLinesTruncatesToMaxLines() {
        val bridge = TerminalBridge()
        bridge.attach(buffer("a", "b", "c"), FakeShellChannel())
        assertEquals(listOf("c"), bridge.lastLines(1))
        assertEquals(3, bridge.lastLines(200).size)
    }

    @Test
    fun writeCommandSendsCommandWithNewline() {
        val bridge = TerminalBridge()
        val channel = FakeShellChannel()
        bridge.attach(buffer("idle"), channel)
        assertTrue(bridge.writeCommand("uptime"))
        assertEquals(1, channel.written.size)
        assertEquals("uptime\n", channel.written[0].toString(Charsets.UTF_8))
    }

    @Test
    fun writeCommandWithoutChannelReturnsFalse() {
        val bridge = TerminalBridge()
        assertFalse(bridge.writeCommand("uptime"))
        bridge.attach(buffer("idle"), FakeShellChannel())
        bridge.detach()
        assertFalse(bridge.writeCommand("uptime"))
    }

    @Test
    fun detachClearsSnapshot() {
        val bridge = TerminalBridge()
        bridge.attach(buffer("hello"), FakeShellChannel())
        assertEquals(listOf("hello"), bridge.lastLines(10))
        bridge.detach()
        assertTrue(bridge.lastLines(10).isEmpty())
    }

    @Test
    fun reattachReplacesPreviousSession() {
        val bridge = TerminalBridge()
        bridge.attach(buffer("old session"), FakeShellChannel())
        bridge.attach(buffer("new session"), FakeShellChannel())
        assertEquals(listOf("new session"), bridge.lastLines(10))
    }
}
