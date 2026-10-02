// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/StreamPumpTest.kt
package com.barezen.ssh.ssh

import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamPumpTest {
    @Test fun pumpsAllChunksThenSignalsClose() {
        val chunks = mutableListOf<ByteArray>()
        val closed = CountDownLatch(1)
        var closeErr: Throwable? = null
        StreamPump { chunks.add(it) }.apply {
            start(ByteArrayInputStream("hello world".toByteArray())) { e -> closeErr = e; closed.countDown() }
        }
        assertTrue(closed.await(3, TimeUnit.SECONDS))
        assertEquals("hello world", chunks.joinToString("") { String(it) })
        assertEquals(null, closeErr)
    }

    @Test fun propagatesStreamError() {
        val closed = CountDownLatch(1)
        var closeErr: Throwable? = null
        val failing = object : java.io.InputStream() {
            override fun read(): Int = throw java.io.IOException("boom")
        }
        StreamPump { }.apply { start(failing) { e -> closeErr = e; closed.countDown() } }
        assertTrue(closed.await(3, TimeUnit.SECONDS))
        assertEquals("boom", closeErr?.message)
    }
}
