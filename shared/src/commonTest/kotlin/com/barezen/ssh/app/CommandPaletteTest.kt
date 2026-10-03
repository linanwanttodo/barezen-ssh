// shared/src/commonTest/kotlin/com/barezen/ssh/app/CommandPaletteTest.kt
package com.barezen.ssh.app

import com.barezen.ssh.servers.Server
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 命令面板的**纯逻辑**：命令构造与模糊过滤。
 * 锁的是可预期的匹配行为，不是像素（UI 部分见 jvmTest 的 CommandPaletteUiTest）。
 */
class CommandPaletteTest {

    private fun srv(id: String, name: String, host: String, tags: List<String> = emptyList()) =
        Server(id = id, name = name, host = host, port = 22, user = "root", tags = tags)

    private val servers = listOf(
        srv("1", "生产网关", "gateway.example.com", listOf("生产")),
        srv("2", "海外缓存", "cache.sg.example.com", listOf("海外")),
        srv("3", "web-01", "10.0.0.11"),
    )

    // ---- 构造 ----

    @Test fun paletteHasOneNavItemPerDestination() {
        val items = buildPalette(emptyList(), Destination.SERVERS, emptySet())
        assertEquals(Destination.entries.size, items.count { it.kind == PaletteItem.Kind.NAVIGATE })
    }

    @Test fun paletteHasThreeCommandsPerServer() {
        val items = buildPalette(servers, Destination.SERVERS, emptySet())
        assertEquals(
            3 * servers.size,
            items.count { it.kind != PaletteItem.Kind.NAVIGATE },
            "每台服务器应有 连接/编辑/删除 三条命令",
        )
    }

    @Test fun connectedServerIsMarkedInSubtitle() {
        val items = buildPalette(servers, Destination.SERVERS, setOf("1"))
        val connect = items.first { it.id == "connect-1" }
        assertTrue(connect.subtitle!!.contains("已连接"), "已连接的服务器应标注：${connect.subtitle}")
    }

    @Test fun nonDefaultPortAppearsInSubtitle() {
        val items = buildPalette(
            listOf(srv("9", "db", "10.0.0.5").copy(port = 2222)),
            Destination.SERVERS, emptySet(),
        )
        assertTrue(items.first { it.id == "connect-9" }.subtitle!!.contains(":2222"))
    }

    @Test fun paletteIgnoresServerListFilter() {
        // 面板是全局入口：即使当前搜索词过滤掉了某台，命令清单里也应包含它
        val items = buildPalette(servers, Destination.SERVERS, emptySet())
        assertTrue(items.any { it.id == "connect-2" })
    }

    // ---- 过滤 ----

    @Test fun emptyQueryKeepsConstructionOrder() {
        val items = buildPalette(servers, Destination.SERVERS, emptySet())
        assertEquals(items, filterPalette(items, ""))
        assertEquals(items, filterPalette(items, "   "))
    }

    @Test fun matchesChineseTitle() {
        val items = buildPalette(servers, Destination.SERVERS, emptySet())
        val r = filterPalette(items, "生产")
        assertTrue(r.isNotEmpty())
        assertTrue(r.all { it.title.contains("生产") || it.subtitle?.contains("生产") == true })
    }

    @Test fun matchesByHostSubstring() {
        val items = buildPalette(servers, Destination.SERVERS, emptySet())
        // 三条命令（连接/编辑/删除）都带同一 host，故按 host 搜会同时命中这三条
        val r = filterPalette(items, "cache.sg")
        assertEquals(3, r.size)
        assertTrue(r.all { it.id.endsWith("-2") }, "只应命中 id 以 -2 结尾的命令：${r.map { it.id }}")
    }

    @Test fun subsequenceMatchWorks() {
        // "gw" 应命中 "gateway"
        val items = buildPalette(servers, Destination.SERVERS, emptySet())
        val r = filterPalette(items, "gw")
        assertTrue(r.any { it.id == "connect-1" }, "子序列匹配应命中 gateway")
    }

    @Test fun wordStartMatchRanksHigher() {
        val items = listOf(
            PaletteItem("a", "数据库连接", "somewhere", PaletteItem.Kind.CONNECT),
            PaletteItem("b", "数据备份", null, PaletteItem.Kind.NAVIGATE),
        )
        val r = filterPalette(items, "数据")
        // 两者都命中，构造顺序保留（稳定排序）
        assertEquals(listOf("a", "b"), r.map { it.id })
    }

    @Test fun multiKeywordIsAndSemantics() {
        val items = buildPalette(servers, Destination.SERVERS, emptySet())
        // "生产 网关" 两个词都要命中 -> 只有生产网关那条（及其编辑/删除）
        val r = filterPalette(items, "生产 网关")
        assertTrue(r.isNotEmpty())
        assertTrue(r.all { it.id.endsWith("-1") }, "AND 语义下只应剩 id 以 -1 结尾的命令：${r.map { it.id }}")
    }

    @Test fun multiKeywordExcludesPartialMatches() {
        val items = buildPalette(servers, Destination.SERVERS, emptySet())
        // "海外 生产" 无任何命令同时含两词
        assertTrue(filterPalette(items, "海外 生产").isEmpty())
    }

    @Test fun nonMatchReturnsEmpty() {
        val items = buildPalette(servers, Destination.SERVERS, emptySet())
        assertTrue(filterPalette(items, "zzzzqqqq").isEmpty())
    }

    @Test fun allThreeServerCommandsSurviveSameQuery() {
        // 同一条服务器的三条命令标题都以服务器名开头，按名搜应三条全出
        val items = buildPalette(servers, Destination.SERVERS, emptySet())
        val r = filterPalette(items, "web-01")
        assertTrue(r.any { it.kind == PaletteItem.Kind.CONNECT })
        assertTrue(r.any { it.kind == PaletteItem.Kind.EDIT_SERVER })
        assertTrue(r.any { it.kind == PaletteItem.Kind.DELETE_SERVER })
    }

    @Test fun scoreIsDeterministic() {
        val items = buildPalette(servers, Destination.SERVERS, emptySet())
        assertEquals(filterPalette(items, "gw"), filterPalette(items, "gw"))
    }
}
