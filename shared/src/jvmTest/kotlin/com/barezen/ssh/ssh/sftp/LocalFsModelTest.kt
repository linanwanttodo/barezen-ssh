// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/sftp/LocalFsModelTest.kt
package com.barezen.ssh.ssh.sftp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 本地目录浏览：进出目录、排序、错误如实上报。
 * 用真实临时目录（不 mock 文件系统）——浏览逻辑薄，真实 IO 反而更能暴露路径/权限问题。
 */
class LocalFsModelTest {

    private val tempDirs = mutableListOf<File>()

    private fun makeTree(): File {
        val root = Files.createTempDirectory("bz-local").toFile()
        tempDirs += root
        File(root, "docs").mkdirs()
        File(root, "src").mkdirs()
        File(root, ".hidden").mkdirs()
        File(root, "notes.txt").writeText("hello")
        File(root, "docs/readme.md").writeText("x".repeat(1500))
        return root
    }

    @AfterTest
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
        tempDirs.clear()
    }

    /**
     * 触发一次初始加载并等它落定。
     * 用 `refresh()` 而不是 `enter(".")`——后者会把 cwd 解析成 `当前目录/.`，
     * 再等它回到 `..` 时基准就错了。
     */
    private fun loaded(model: LocalFsModel): LocalFsState = runBlocking {
        model.refresh()
        var guard = 0
        while (guard++ < 200) {
            kotlinx.coroutines.delay(5)
            if (model.state.value.entries.isNotEmpty()) break
        }
        model.state.value
    }

    @Test fun listsEntriesWithDirectoriesFirst(): Unit = runBlocking {
        val root = makeTree()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val model = LocalFsModel(scope, Dispatchers.Unconfined, root)
        val st = loaded(model)
        val dirs = st.entries.filter { it.isDirectory }.map { it.name }
        val files = st.entries.filter { !it.isDirectory }.map { it.name }
        // 目录在前（docs / src / .hidden），文件在后（notes.txt）
        assertEquals("notes.txt", files.single())
        assertTrue(dirs.containsAll(listOf("docs", "src", ".hidden")), "实际：$dirs")
        // 目录段必须连续排在文件段之前
        val firstFileIdx = st.entries.indexOfFirst { !it.isDirectory }
        assertTrue(
            st.entries.take(firstFileIdx).all { it.isDirectory },
            "首个文件之前必须全是目录",
        )
    }

    @Test fun hiddenEntriesSortAfterVisibleOnes(): Unit = runBlocking {
        val root = makeTree()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val model = LocalFsModel(scope, Dispatchers.Unconfined, root)
        val st = loaded(model)
        val dirNames = st.entries.filter { it.isDirectory }.map { it.name }
        // 隐藏目录（.hidden）应排在可见目录之后
        assertTrue(
            dirNames.indexOf(".hidden") > dirNames.indexOf("docs"),
            "隐藏项应靠后，实际顺序：$dirNames",
        )
    }

    @Test fun entersChildDirectory(): Unit = runBlocking {
        val root = makeTree()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val model = LocalFsModel(scope, Dispatchers.Unconfined, root)
        loaded(model)
        model.enter("docs")
        val st = model.state.value
        assertTrue(st.cwd.endsWith("docs"), "应进入 docs，实际 cwd=${st.cwd}")
        assertEquals(listOf("readme.md"), st.entries.map { it.name })
    }

    @Test fun parentNavigationStaysInsideRoot(): Unit = runBlocking {
        val root = makeTree()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        // 从 docs 进到根：先加载 docs，再 enter("..")
        val model = LocalFsModel(scope, Dispatchers.Unconfined, File(root, "docs"))
        loaded(model)
        model.enter("..")
        assertEquals(
            root.canonicalPath,
            File(model.state.value.cwd).canonicalPath,
            "从 docs 上级应回到临时根目录",
        )
        assertTrue(model.state.value.entries.any { it.name == "docs" })
    }

    @Test fun unreadableDirectoryReportsErrorInsteadOfFailingSilently(): Unit = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        // 指向一个不存在的路径
        val model = LocalFsModel(scope, Dispatchers.Unconfined, File("/definitely/not/here/bz"))
        model.openPath("/definitely/not/here/bz")
        assertNotNull(model.state.value.error, "读不到目录应如实上报错误")
    }

    @Test fun canGoUpIsFalseAtFilesystemRoot(): Unit = runBlocking {
        val root = File("/")
        val st = LocalFsState(cwd = root.absolutePath)
        assertTrue(!st.canGoUp, "根目录不应还能再往上")
    }

    @Test fun canGoUpIsTrueForNestedDirectory(): Unit {
        assertTrue(LocalFsState(cwd = "/home/user/docs").canGoUp)
    }

    @Test fun fileSizeIsReadForRegularFiles(): Unit = runBlocking {
        val root = makeTree()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val model = LocalFsModel(scope, Dispatchers.Unconfined, File(root, "docs"))
        model.enter(".")
        val st = model.state.value
        val readme = st.entries.first()
        assertEquals(1500L, readme.size)
        assertTrue(!readme.isDirectory)
    }

    @Test fun errorIsClearable(): Unit = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val model = LocalFsModel(scope, Dispatchers.Unconfined, File("/nope/bz"))
        model.openPath("/nope/bz")
        assertNotNull(model.state.value.error)
        model.dismissError()
        assertEquals(null, model.state.value.error)
    }
}
