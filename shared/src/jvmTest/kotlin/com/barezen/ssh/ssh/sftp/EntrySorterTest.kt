// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/sftp/EntrySorterTest.kt
package com.barezen.ssh.ssh.sftp

import com.barezen.ssh.ssh.SftpEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EntrySorterTest {

    private fun dir(name: String) = SftpEntry(name, true, 0L, 0L)
    private fun file(name: String) = SftpEntry(name, false, 1L, 0L)

    // ---- 混合：目录在前、隐藏排最后、名称字母序 ----

    @Test fun mixedSort() {
        val sorted = EntrySorter.sorted(
            listOf(file("zeta"), dir("m"), file("beta"), dir("a"), file(".hidden"), dir(".config")),
        )
        // 目录在前（隐藏目录随后），文件在后（隐藏文件最后）
        assertEquals(listOf("a", "m", ".config", "beta", "zeta", ".hidden"), sorted.map { it.name })
    }

    @Test fun directoriesBeforeFilesEvenAfterFilesAlphabetically() {
        val sorted = EntrySorter.sorted(listOf(file("aaa"), dir("zzz")))
        assertEquals(listOf("zzz", "aaa"), sorted.map { it.name })
    }

    // ---- 全目录 / 全文件 ----

    @Test fun allDirectoriesSortedByName() {
        val sorted = EntrySorter.sorted(listOf(dir("c"), dir("a"), dir("b")))
        assertEquals(listOf("a", "b", "c"), sorted.map { it.name })
    }

    @Test fun allFilesSortedByName() {
        val sorted = EntrySorter.sorted(listOf(file("c"), file("a"), file("b")))
        assertEquals(listOf("a", "b", "c"), sorted.map { it.name })
    }

    // ---- 空表 ----

    @Test fun emptyListStaysEmpty() = assertTrue(EntrySorter.sorted(emptyList()).isEmpty())

    // ---- 隐藏文件（. 开头）在所属分组内排最后 ----

    @Test fun hiddenFilesGoLastAmongFiles() {
        val sorted = EntrySorter.sorted(listOf(file(".a"), file("z"), file("m")))
        assertEquals(listOf("m", "z", ".a"), sorted.map { it.name })
    }

    @Test fun hiddenDirectoriesGoLastAmongDirectories() {
        val sorted = EntrySorter.sorted(listOf(dir(".cache"), dir("etc"), dir("home")))
        assertEquals(listOf("etc", "home", ".cache"), sorted.map { it.name })
    }

    @Test fun onlyHiddenEntriesKeepsNameOrder() {
        val sorted = EntrySorter.sorted(listOf(file(".z"), dir(".a"), file(".m")))
        assertEquals(listOf(".a", ".m", ".z"), sorted.map { it.name })
    }

    // ---- 名称大小写不敏感，同字母异大小写按原序稳定 ----

    @Test fun caseInsensitiveNameOrder() {
        val sorted = EntrySorter.sorted(listOf(file("Beta"), file("alpha"), file("CHARLIE")))
        assertEquals(listOf("alpha", "Beta", "CHARLIE"), sorted.map { it.name })
    }

    @Test fun singleEntryUnchanged() {
        val sorted = EntrySorter.sorted(listOf(file("x")))
        assertEquals(listOf("x"), sorted.map { it.name })
    }
}
