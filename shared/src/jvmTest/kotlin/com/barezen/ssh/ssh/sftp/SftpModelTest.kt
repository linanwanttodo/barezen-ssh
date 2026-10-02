// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/sftp/SftpModelTest.kt
package com.barezen.ssh.ssh.sftp

import com.barezen.ssh.ssh.SftpEntry
import com.barezen.ssh.ssh.SftpException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SftpModelTest {

    private fun newModel(
        fs: FakeSftpFs,
        clock: () -> Long = { 0L },
    ): SftpModel {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        return SftpModel(
            scope = scope,
            fsFactory = { fs },
            io = Dispatchers.Unconfined,
            clock = clock,
        )
    }

    private fun rootTree() = mapOf(
        "/" to listOf(
            SftpEntry("zeta.txt", false, 10L, 1_000L),
            SftpEntry("docs", true, 0L, 2_000L),
            SftpEntry(".bashrc", false, 5L, 3_000L),
            SftpEntry("alpha", true, 0L, 4_000L),
        ),
        "/docs" to listOf(SftpEntry("readme.md", false, 20L, 5_000L)),
    )

    // ---- 目录浏览 ----

    @Test fun initListsRootSortedDirsFirst() {
        val fs = FakeSftpFs(rootTree())
        val model = newModel(fs)
        assertEquals("/", fs.listedDirs.first())
        assertEquals("/", model.state.value.cwd)
        assertEquals(listOf("alpha", "docs", "zeta.txt", ".bashrc"), model.state.value.entries.map { it.name })
    }

    @Test fun enterDirectoryPushesPath() {
        val fs = FakeSftpFs(rootTree())
        val model = newModel(fs)
        model.enter("docs")
        assertEquals("/docs", model.state.value.cwd)
        assertEquals(listOf("readme.md"), model.state.value.entries.map { it.name })
    }

    @Test fun enterDotDotReturnsToParent() {
        val fs = FakeSftpFs(rootTree())
        val model = newModel(fs)
        model.enter("docs")
        model.enter("..")
        assertEquals("/", model.state.value.cwd)
    }

    @Test fun enterDotDotAtRootStaysAtRoot() {
        val fs = FakeSftpFs(rootTree())
        val model = newModel(fs)
        model.enter("..")
        assertEquals("/", model.state.value.cwd)
    }

    @Test fun enterFileIsIgnored() {
        val fs = FakeSftpFs(rootTree())
        val model = newModel(fs)
        model.enter("zeta.txt")
        assertEquals("/", model.state.value.cwd)
    }

    @Test fun enterUnknownNameIsIgnored() {
        val fs = FakeSftpFs(rootTree())
        val model = newModel(fs)
        model.enter("nope")
        assertEquals("/", model.state.value.cwd)
    }

    @Test fun gotoAbsolutePath() {
        val fs = FakeSftpFs(rootTree())
        val model = newModel(fs)
        model.goto("/docs/")
        assertEquals("/docs", model.state.value.cwd)
        assertEquals(listOf("readme.md"), model.state.value.entries.map { it.name })
    }

    @Test fun homeReturnsToRoot() {
        val fs = FakeSftpFs(rootTree())
        val model = newModel(fs)
        model.goto("/docs")
        model.home()
        assertEquals("/", model.state.value.cwd)
    }

    @Test fun listFailureSetsErrorAndKeepsEntries() {
        val fs = FakeSftpFs(rootTree(), listError = SftpException("permission denied"))
        val model = newModel(fs)
        model.enter("docs")
        assertEquals("permission denied", model.state.value.error)
    }

    @Test fun successfulListClearsPreviousError() {
        val fs = FakeSftpFs(rootTree(), listError = SftpException("boom"))
        val model = newModel(fs)
        // init 列根目录失败
        assertEquals("boom", model.state.value.error)
        fs.listError = null
        model.refresh()
        assertNull(model.state.value.error)
    }

    @Test fun mkdirCreatesAndRefreshes() {
        val fs = FakeSftpFs(rootTree())
        val model = newModel(fs)
        model.mkdir("newdir")
        assertEquals(listOf("/newdir"), fs.mkdirs)
        // mkdir 后重新列过一次当前目录（最后一次 listed 为 "/"）
        assertEquals("/", fs.listedDirs.last())
    }

    // ---- 传输：下载 ----

    @Test fun downloadProgressesToDone() {
        val fs = FakeSftpFs(rootTree())
        val model = newModel(fs)
        runBlocking { model.download("/docs/readme.md", "/tmp/readme.md", totalBytes = 100L) }
        val task = model.transfers.value.single()
        assertEquals(100L, task.totalBytes)
        assertEquals(100L, task.transferredBytes)
        assertTrue(task.status is com.barezen.ssh.ssh.sftp.TransferStatus.Done)
    }

    @Test fun downloadAccumulatesChunkBytes() {
        val fs = FakeSftpFs(
            rootTree(),
            downloadChunks = listOf(ByteArray(30), ByteArray(30), ByteArray(40)),
        )
        val model = newModel(fs)
        model.download("/f.bin", "/tmp/f.bin", totalBytes = 100L)
        val task = model.transfers.value.single()
        assertEquals(100L, task.transferredBytes)
    }

    @Test fun downloadFailureMarksTaskFailed() {
        val fs = FakeSftpFs(rootTree(), downloadError = SftpException("no such file"))
        val model = newModel(fs)
        model.download("/missing", "/tmp/x", totalBytes = 10L)
        val task = model.transfers.value.single()
        val failed = task.status as com.barezen.ssh.ssh.sftp.TransferStatus.Failed
        assertEquals("no such file", failed.message)
    }

    @Test fun cancelBetweenChunksStopsTask() {
        val fs = FakeSftpFs(
            rootTree(),
            downloadChunks = listOf(ByteArray(10), ByteArray(10), ByteArray(10)),
        )
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val model = SftpModel(scope, { fs }, Dispatchers.Unconfined)
        fs.beforeChunk = { index ->
            if (index == 1) {
                val running = model.transfers.value.first()
                model.cancel(running.id)
            }
        }
        model.download("/f.bin", "/tmp/f.bin", totalBytes = 30L)
        val task = model.transfers.value.single()
        val failed = task.status as com.barezen.ssh.ssh.sftp.TransferStatus.Failed
        assertEquals("已取消", failed.message)
        assertEquals(10L, task.transferredBytes)
        scope.cancel()
    }

    // ---- 传输：上传 ----

    @Test fun uploadSendsChunksInOrderAndCompletes() {
        val fs = FakeSftpFs(rootTree())
        val sink = mutableListOf<ByteArray>()
        fs.uploadSink = sink
        val model = newModel(fs)
        val local = Files.createTempFile("upload", ".bin").toFile()
        local.writeBytes(ByteArray(150_000))
        try {
            model.upload(local.absolutePath, "up.bin")
            val task = model.transfers.value.single()
            assertEquals(150_000L, task.totalBytes)
            assertEquals(150_000L, task.transferredBytes)
            assertTrue(task.status is TransferStatus.Done)
            assertEquals("/up.bin", fs.uploadTargets.single().first)
            assertEquals(150_000L, fs.uploadTargets.single().second)
            // 64KiB 分块：65536 + 65536 + 18928
            assertEquals(listOf(65_536, 65_536, 18_928), sink.map { it.size })
        } finally {
            local.delete()
        }
    }

    @Test fun uploadMissingLocalFileMarksTaskFailed() {
        val fs = FakeSftpFs(rootTree())
        val model = newModel(fs)
        model.upload("/nonexistent/path.bin", "x.bin")
        val task = model.transfers.value.single()
        assertTrue(task.status is TransferStatus.Failed)
    }

    @Test fun uploadRemoteFailureMarksTaskFailed() {
        val fs = FakeSftpFs(rootTree(), uploadError = SftpException("disk full"))
        val sink = mutableListOf<ByteArray>()
        fs.uploadSink = sink
        val model = newModel(fs)
        val local = Files.createTempFile("upload", ".bin").toFile()
        local.writeBytes(ByteArray(70_000))
        try {
            model.upload(local.absolutePath, "up.bin")
            val task = model.transfers.value.single()
            assertEquals("disk full", (task.status as TransferStatus.Failed).message)
        } finally {
            local.delete()
        }
    }

    // ---- 多任务并发 ----

    @Test fun concurrentTransfersAreTrackedIndependently() {
        val fs = FakeSftpFs(rootTree())
        val model = newModel(fs)
        runBlocking {
            model.download("/a", "/tmp/a", totalBytes = 10L)
            model.download("/b", "/tmp/b", totalBytes = 20L)
        }
        val tasks = model.transfers.value
        assertEquals(2, tasks.size)
        assertEquals(setOf(10L, 20L), tasks.map { it.totalBytes }.toSet())
        assertTrue(tasks.all { it.status is TransferStatus.Done })
    }
}
