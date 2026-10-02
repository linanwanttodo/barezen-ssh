package com.barezen.ssh.ssh.sftp

import com.barezen.ssh.ssh.SftpException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 传输完成句柄（T-3）：awaitCompletion / awaitAll 的四条路径——已完成立即返回、
 * 进行中阻塞至终态、失败任务返回 Failed、未知 id 返回 null。
 */
class SftpModelCompletionTest {

    /**
     * 用真实的线程池调度器：`Dispatchers.Unconfined` 会让 upload 在调用线程上同步跑完，
     * 无法表达「任务仍在进行中」（测试自己会把 gate 锁死）。
     */
    private fun newModel(fs: FakeSftpFs): SftpModel = SftpModel(
        scope = CoroutineScope(Dispatchers.Default),
        fsFactory = { fs },
        io = Dispatchers.Default,
        clock = { 0L },
    )

    private fun uploadFile(bytes: Int): File =
        File.createTempFile("upload_", ".bin").apply { writeBytes(ByteArray(bytes) { 1 }) }

    /** 自旋等待任务登记（upload 在后台协程里创建任务条目）。 */
    private suspend fun awaitTaskId(model: SftpModel): Long {
        repeat(200) {
            model.transfers.value.lastOrNull()?.let { return it.id }
            kotlinx.coroutines.delay(10)
        }
        error("任务未在预期时间内登记")
    }

    private suspend fun awaitCount(model: SftpModel, n: Int) {
        repeat(200) {
            if (model.transfers.value.size >= n) return
            kotlinx.coroutines.delay(10)
        }
        error("任务数未达到 $n")
    }

    @Test
    fun awaitCompletionReturnsImmediatelyForFinishedTask() = runBlocking<Unit> {
        val fs = FakeSftpFs()
        val model = newModel(fs)
        val src = uploadFile(1024)

        model.upload(src.absolutePath, "a.bin")
        val id = awaitTaskId(model)
        val done = withTimeout(10_000) { model.awaitCompletion(id) }

        assertNotNull(done)
        assertEquals(TransferStatus.Done, done.status)
        src.delete()
    }

    @Test
    fun awaitCompletionBlocksUntilTerminalState() = runBlocking<Unit> {
        val gate = CountDownLatch(1)
        val fs = FakeSftpFs()
        fs.beforeUploadChunk = { gate.await() }
        val model = newModel(fs)
        val src = uploadFile(4096)

        model.upload(src.absolutePath, "b.bin")
        val id = awaitTaskId(model)
        // 门未放行：任务仍在跑，completion 未完成
        assertEquals(TransferStatus.Running, model.transfers.value.first { it.id == id }.status)
        assertFalse(model.transfers.value.first { it.id == id }.completion.isCompleted)

        gate.countDown()
        val done = withTimeout(10_000) { model.awaitCompletion(id) }
        assertEquals(TransferStatus.Done, done!!.status)
        src.delete()
    }

    @Test
    fun awaitCompletionReturnsFailedForErrorTask() = runBlocking<Unit> {
        val fs = FakeSftpFs()
        fs.uploadError = SftpException("远端磁盘已满")
        val model = newModel(fs)
        val src = uploadFile(512)

        model.upload(src.absolutePath, "c.bin")
        val id = awaitTaskId(model)
        val task = withTimeout(10_000) { model.awaitCompletion(id) }!!

        assertTrue(task.status is TransferStatus.Failed, "应为 Failed，实际 ${task.status}")
        assertTrue((task.status as TransferStatus.Failed).message.contains("磁盘已满"))
        src.delete()
    }

    @Test
    fun awaitCompletionForUnknownIdReturnsNull() = runBlocking<Unit> {
        val model = newModel(FakeSftpFs())
        assertNull(withTimeout(10_000) { model.awaitCompletion(9999L) })
    }

    @Test
    fun awaitAllReturnsEveryTaskInIdOrder() = runBlocking<Unit> {
        val fs = FakeSftpFs()
        val model = newModel(fs)
        val a = uploadFile(256)
        val b = uploadFile(512)

        model.upload(a.absolutePath, "a.bin")
        model.upload(b.absolutePath, "b.bin")
        awaitCount(model, 2)

        val all = withTimeout(10_000) { model.awaitAll() }
        assertEquals(2, all.size)
        assertEquals(all.map { it.id }.sorted(), all.map { it.id })
        assertTrue(all.all { it.status == TransferStatus.Done })
        a.delete()
        b.delete()
    }

    @Test
    fun uploadFailureStillSettlesCompletion() = runBlocking<Unit> {
        // 无论成功或失败，completion 都必须被完成，否则 await 会永久挂起
        val fs = FakeSftpFs()
        fs.uploadError = SftpException("boom")
        val model = newModel(fs)
        val src = uploadFile(256)

        model.upload(src.absolutePath, "d.bin")
        val id = awaitTaskId(model)
        val task = withTimeout(10_000) { model.awaitCompletion(id) }!!

        assertTrue(task.status is TransferStatus.Failed)
        assertTrue(model.transfers.value.first { it.id == id }.completion.isCompleted)
        src.delete()
    }
}
