package com.barezen.ssh.ssh.sftp

import com.barezen.ssh.ssh.SftpEntry
import com.barezen.ssh.ssh.SftpException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-3：删除与重命名的模型层行为（成功刷新、失败保留列表并报错、路径拼装正确）。 */
class SftpModelDeleteRenameTest {

    private fun newModel(fs: FakeSftpFs): SftpModel = SftpModel(
        scope = CoroutineScope(Dispatchers.Unconfined),
        fsFactory = { fs },
        io = Dispatchers.Unconfined,
        clock = { 0L },
    )

    private fun tree() = mapOf(
        "/" to listOf(
            SftpEntry("a.txt", false, 10L, 1_000L),
            SftpEntry("empty", true, 0L, 2_000L),
            SftpEntry("full", true, 0L, 3_000L),
        ),
    )

    @Test fun deleteFileUsesDeleteAndRefreshes() {
        val fs = FakeSftpFs(tree())
        val model = newModel(fs)
        val entry = model.state.value.entries.first { it.name == "a.txt" }

        model.delete(entry)

        assertEquals(listOf("/a.txt"), fs.deletes)
        assertEquals("/", model.state.value.cwd)
        assertNull(model.state.value.error)
    }

    @Test fun deleteEmptyDirectorySucceeds() {
        val fs = FakeSftpFs(tree())
        val model = newModel(fs)
        val dir = model.state.value.entries.first { it.name == "empty" }

        model.delete(dir)

        assertEquals(listOf("/empty"), fs.deletes)
        assertNull(model.state.value.error)
    }

    @Test fun deleteNonEmptyDirectorySurfacesServerError() {
        val fs = FakeSftpFs(tree())
        fs.deleteError = SftpException("目录非空")
        val model = newModel(fs)
        val dir = model.state.value.entries.first { it.name == "full" }

        model.delete(dir)

        // 列表保留上次成功结果，错误如实上报（不静默吞掉）
        assertTrue(model.state.value.error!!.contains("目录非空"))
        // 列表顺序由 EntrySorter 决定（目录优先），此处只断言「未被清空」
        assertEquals(listOf("empty", "full", "a.txt"), model.state.value.entries.map { it.name })
    }

    @Test fun renameUsesFullPathsAndRefreshes() {
        val fs = FakeSftpFs(tree())
        val model = newModel(fs)
        val entry = model.state.value.entries.first { it.name == "a.txt" }

        model.rename(entry, "b.txt")

        assertEquals(listOf("/a.txt" to "/b.txt"), fs.renames)
        assertNull(model.state.value.error)
    }

    @Test fun renameTrimsWhitespace() {
        val fs = FakeSftpFs(tree())
        val model = newModel(fs)
        val entry = model.state.value.entries.first { it.name == "a.txt" }

        model.rename(entry, "  c.txt  ")

        assertEquals(listOf("/a.txt" to "/c.txt"), fs.renames)
    }

    @Test fun renameFailureSurfacesError() {
        val fs = FakeSftpFs(tree())
        fs.renameError = SftpException("目标已存在")
        val model = newModel(fs)
        val entry = model.state.value.entries.first { it.name == "a.txt" }

        model.rename(entry, "b.txt")

        assertTrue(model.state.value.error!!.contains("目标已存在"))
    }

    @Test fun deleteInsideSubdirectoryUsesCwd() {
        val fs = FakeSftpFs(
            mapOf(
                "/" to listOf(SftpEntry("docs", true, 0L, 1L)),
                "/docs" to listOf(SftpEntry("x.md", false, 1L, 2L)),
            ),
        )
        val model = newModel(fs)
        model.enter("docs")
        val entry = model.state.value.entries.first { it.name == "x.md" }

        model.delete(entry)

        assertEquals(listOf("/docs/x.md"), fs.deletes)
    }
}
