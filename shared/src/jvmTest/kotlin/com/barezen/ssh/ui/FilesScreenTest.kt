// shared/src/jvmTest/kotlin/com/barezen/ssh/ui/FilesScreenTest.kt
package com.barezen.ssh.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.ssh.ssh.SftpEntry
import com.barezen.ssh.ssh.SftpException
import com.barezen.ssh.ssh.sftp.FakeSftpFs
import com.barezen.ssh.ssh.sftp.SftpModel
import com.barezen.ssh.ui.screens.FilesScreen
import com.barezen.ssh.ui.theme.BareZenTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

class FilesScreenTest {

    private val rootTree = mapOf(
        "/" to listOf(
            SftpEntry("zeta.txt", false, 1_500L, 1_760_000_000_000L),
            SftpEntry("docs", true, 0L, 1_760_000_000_000L),
        ),
        "/docs" to listOf(SftpEntry("readme.md", false, 20L, 1_760_000_000_000L)),
    )

    // ---- 未连接态：整屏提示 + 全部禁用 ----

    @OptIn(ExperimentalTestApi::class)
    @Test fun disconnectedShowsHintAndDisablesActions() = runComposeUiTest {
        setContent { BareZenTheme { FilesScreen() } }
        onNodeWithText("连接后可管理文件").assertIsDisplayed()
        onNodeWithText("上传").assertIsNotEnabled()
        onNodeWithText("刷新").assertIsNotEnabled()
        onNodeWithText("新建文件夹").assertIsNotEnabled()
    }

    // ---- 已连接：列表渲染 ----

    @OptIn(ExperimentalTestApi::class)
    @Test fun connectedListsEntriesWithNamesAndSizes() = runComposeUiTest {
        val model = newModel(FakeSftpFs(rootTree))
        setContent { BareZenTheme { FilesScreen(model) } }
        onNodeWithText("docs").assertIsDisplayed()
        onNodeWithText("zeta.txt").assertIsDisplayed()
        onNodeWithText("1.5 KB").assertIsDisplayed()   // 1500B -> 1.4648KB 四舍五入
        onNodeWithText("/").assertIsDisplayed()
        onNodeWithText("上传").assertIsEnabled()
        onNodeWithText("刷新").assertIsEnabled()
        onNodeWithText("新建文件夹").assertIsEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun clickDirectoryEntersIt() = runComposeUiTest {
        val model = newModel(FakeSftpFs(rootTree))
        setContent { BareZenTheme { FilesScreen(model) } }
        onNodeWithText("docs").performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("readme.md").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("readme.md").assertIsDisplayed()
        onNodeWithText("/docs").assertIsDisplayed()
    }

    // ---- 传输任务：进行中（可取消）与完成态 ----

    @OptIn(ExperimentalTestApi::class)
    @Test fun transferShowsRunningThenDone() = runComposeUiTest {
        val latch = CountDownLatch(1)
        val fs = FakeSftpFs(rootTree, downloadChunks = listOf(ByteArray(50), ByteArray(50)))
        fs.beforeChunk = { index -> if (index == 1) latch.await(5, TimeUnit.SECONDS) }
        val model = SftpModel(CoroutineScope(Dispatchers.IO), { fs }, Dispatchers.IO)
        val local = Files.createTempFile("dl", ".bin").toFile()
        try {
            model.download("/docs/readme.md", local.absolutePath, totalBytes = 100L)
            setContent { BareZenTheme { FilesScreen(model) } }
            waitUntil(timeoutMillis = 10_000) {
                onAllNodesWithText("取消").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("传输任务").assertIsDisplayed()
            latch.countDown()
            waitUntil(timeoutMillis = 10_000) {
                onAllNodesWithText("完成").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("完成").assertIsDisplayed()
            onNodeWithText("100%").assertIsDisplayed()
        } finally {
            latch.countDown()
            local.delete()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun transferFailureShowsMessage() = runComposeUiTest {
        val fs = FakeSftpFs(rootTree, downloadError = SftpException("no such file"))
        val model = newModel(fs)
        model.download("/missing", Files.createTempFile("dl", ".bin").toFile().absolutePath, totalBytes = 10L)
        setContent { BareZenTheme { FilesScreen(model) } }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("失败：no such file").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("失败：no such file").assertIsDisplayed()
    }

    // ---- 列表失败：错误条 ----

    @OptIn(ExperimentalTestApi::class)
    @Test fun listErrorShowsBanner() = runComposeUiTest {
        val fs = FakeSftpFs(rootTree, listError = SftpException("permission denied"))
        val model = newModel(fs)
        setContent { BareZenTheme { FilesScreen(model) } }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("错误：permission denied").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("错误：permission denied").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun emptyDirectoryShowsPlaceholder() = runComposeUiTest {
        val emptyModel = newModel(FakeSftpFs(mapOf("/" to emptyList())))
        setContent { BareZenTheme { FilesScreen(emptyModel) } }
        onNodeWithText("空目录").assertIsDisplayed()
        assertTrue(onAllNodesWithText("zeta.txt").fetchSemanticsNodes().isEmpty())
    }

    private fun newModel(fs: FakeSftpFs): SftpModel =
        SftpModel(
            scope = CoroutineScope(Dispatchers.Unconfined),
            fsFactory = { fs },
            io = Dispatchers.Unconfined,
        )
}
