// shared/src/jvmTest/kotlin/com/barezen/ssh/ui/FilesScreenDeleteRenameTest.kt
package com.barezen.ssh.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.ssh.ssh.SftpEntry
import com.barezen.ssh.ssh.SftpException
import com.barezen.ssh.ssh.sftp.FakeSftpFs
import com.barezen.ssh.ssh.sftp.SftpModel
import com.barezen.ssh.ui.screens.FILE_DELETE_CONFIRM_TAG
import com.barezen.ssh.ui.screens.FILE_DELETE_DIALOG_TAG
import com.barezen.ssh.ui.screens.FILE_DELETE_PREFIX
import com.barezen.ssh.ui.screens.FILE_RENAME_CONFIRM_TAG
import com.barezen.ssh.ui.screens.FILE_RENAME_DIALOG_TAG
import com.barezen.ssh.ui.screens.FILE_RENAME_INPUT_TAG
import com.barezen.ssh.ui.screens.FILE_RENAME_PREFIX
import com.barezen.ssh.ui.screens.FilesScreen
import com.barezen.ssh.ui.theme.BareZenTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T-3：文件列表的删除确认与重命名校验（Compose UI 层）。
 *
 * 列表行与对话框都存在「删除」「a.txt」等文本，一律用 testTag 定位以避开多节点歧义
 * （CONVENTIONS 第 4.3 节）。
 */
class FilesScreenDeleteRenameTest {

    private val tree = mapOf(
        "/" to listOf(
            SftpEntry("a.txt", false, 10L, 1_760_000_000_000L),
            SftpEntry("empty", true, 0L, 1_760_000_000_000L),
            SftpEntry("full", true, 0L, 1_760_000_000_000L),
        ),
    )

    private fun newModel(fs: FakeSftpFs): SftpModel = SftpModel(
        scope = CoroutineScope(Dispatchers.Unconfined),
        fsFactory = { fs },
        io = Dispatchers.Unconfined,
    )

    // ---- 删除 ----

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun deleteFileAsksForConfirmationBeforeDeleting() = runComposeUiTest {
        val fs = FakeSftpFs(tree)
        val model = newModel(fs)
        setContent { BareZenTheme { FilesScreen(model) } }

        onNodeWithTag(FILE_DELETE_PREFIX + "a.txt").performClick()

        onNodeWithTag(FILE_DELETE_DIALOG_TAG).assertIsDisplayed()
        onNodeWithText("删除文件").assertIsDisplayed()
        assertTrue(fs.deletes.isEmpty(), "弹出确认框时不应已删除：${fs.deletes}")

        onNodeWithTag(FILE_DELETE_CONFIRM_TAG).performClick()
        waitUntil(timeoutMillis = 5_000) { fs.deletes.isNotEmpty() }
        assertEquals(listOf("/a.txt"), fs.deletes)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun deleteDirectoryExplainsEmptyOnlyRule() = runComposeUiTest {
        val fs = FakeSftpFs(tree)
        val model = newModel(fs)
        setContent { BareZenTheme { FilesScreen(model) } }

        onNodeWithTag(FILE_DELETE_PREFIX + "full").performClick()

        onNodeWithText("删除文件夹").assertIsDisplayed()
        onNodeWithText("仅能删除空目录；非空目录需先清空其中的内容。").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun deleteEmptyDirectorySucceeds() = runComposeUiTest {
        val fs = FakeSftpFs(tree)
        val model = newModel(fs)
        setContent { BareZenTheme { FilesScreen(model) } }

        onNodeWithTag(FILE_DELETE_PREFIX + "empty").performClick()
        onNodeWithTag(FILE_DELETE_CONFIRM_TAG).performClick()

        waitUntil(timeoutMillis = 5_000) { fs.deletes.isNotEmpty() }
        assertEquals(listOf("/empty"), fs.deletes)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun deleteNonEmptyDirectorySurfacesServerError() = runComposeUiTest {
        val fs = FakeSftpFs(tree)
        fs.deleteError = SftpException("目录非空")
        val model = newModel(fs)
        setContent { BareZenTheme { FilesScreen(model) } }

        onNodeWithTag(FILE_DELETE_PREFIX + "full").performClick()
        onNodeWithTag(FILE_DELETE_CONFIRM_TAG).performClick()

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("错误：目录非空").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("错误：目录非空").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun deleteCancelKeepsEntry() = runComposeUiTest {
        val fs = FakeSftpFs(tree)
        val model = newModel(fs)
        setContent { BareZenTheme { FilesScreen(model) } }

        onNodeWithTag(FILE_DELETE_PREFIX + "a.txt").performClick()
        onNodeWithText("取消").performClick()

        onNodeWithText("a.txt").assertIsDisplayed()
        assertTrue(fs.deletes.isEmpty(), "取消后不得删除")
    }

    // ---- 重命名 ----

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun renameValidNameIssuesRename() = runComposeUiTest {
        val fs = FakeSftpFs(tree)
        val model = newModel(fs)
        setContent { BareZenTheme { FilesScreen(model) } }

        onNodeWithTag(FILE_RENAME_PREFIX + "a.txt").performClick()
        onNodeWithTag(FILE_RENAME_DIALOG_TAG).assertIsDisplayed()

        onNodeWithTag(FILE_RENAME_INPUT_TAG).performTextClearance()
        onNodeWithTag(FILE_RENAME_INPUT_TAG).performTextInput("b.txt")
        onNodeWithTag(FILE_RENAME_CONFIRM_TAG).performClick()

        waitUntil(timeoutMillis = 5_000) { fs.renames.isNotEmpty() }
        assertEquals(listOf("/a.txt" to "/b.txt"), fs.renames)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun renameRejectsSlashName() = runComposeUiTest {
        val fs = FakeSftpFs(tree)
        val model = newModel(fs)
        setContent { BareZenTheme { FilesScreen(model) } }

        onNodeWithTag(FILE_RENAME_PREFIX + "a.txt").performClick()
        onNodeWithTag(FILE_RENAME_INPUT_TAG).performTextClearance()
        onNodeWithTag(FILE_RENAME_INPUT_TAG).performTextInput("bad/name")

        onNodeWithText("名称不能包含 /").assertIsDisplayed()
        onNodeWithTag(FILE_RENAME_CONFIRM_TAG).assertIsNotEnabled()
        assertTrue(fs.renames.isEmpty(), "非法名不得发出请求")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun renameRejectsEmptyName() = runComposeUiTest {
        val fs = FakeSftpFs(tree)
        val model = newModel(fs)
        setContent { BareZenTheme { FilesScreen(model) } }

        onNodeWithTag(FILE_RENAME_PREFIX + "a.txt").performClick()
        onNodeWithTag(FILE_RENAME_INPUT_TAG).performTextClearance()

        onNodeWithText("名称不能为空").assertIsDisplayed()
        onNodeWithTag(FILE_RENAME_CONFIRM_TAG).assertIsNotEnabled()
        assertTrue(fs.renames.isEmpty())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun renameDisabledWhenNameUnchanged() = runComposeUiTest {
        val fs = FakeSftpFs(tree)
        val model = newModel(fs)
        setContent { BareZenTheme { FilesScreen(model) } }

        onNodeWithTag(FILE_RENAME_PREFIX + "a.txt").performClick()
        onNodeWithTag(FILE_RENAME_CONFIRM_TAG).assertIsNotEnabled()
    }
}
