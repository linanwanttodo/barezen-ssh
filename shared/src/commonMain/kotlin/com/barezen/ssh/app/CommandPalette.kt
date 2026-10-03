// shared/src/commonMain/kotlin/com/barezen/ssh/app/CommandPalette.kt
package com.barezen.ssh.app

import com.barezen.ssh.servers.Server

/**
 * 命令面板（⌘K / Ctrl+K）。
 *
 * 设计取舍：命令是**由当前状态派生**的纯函数，不另存一份命令表——
 * 服务器列表、导航目的地这些数据本来就在模型里，复制一份必然过期。
 * 面板只负责「把当前可见的东西收集起来 + 过滤 + 上报选了哪条」。
 *
 * 模糊匹配是**子序列匹配**（不是编辑距离）：输入 "gw" 命中 "gateway"，
 * 且匹配分数按「连续命中」「越靠前越优先」「词首命中加成」排序，
 * 使最相关的命令排在最前。
 */
data class PaletteItem(
    /** 稳定 id：测试与选中态定位用，不依赖显示文案。 */
    val id: String,
    val title: String,
    val subtitle: String?,
    /** 导航类命令的目标；服务器类命令的 id 即服务器 id。 */
    val kind: Kind,
) {
    enum class Kind {
        /** 切换到某个界面。 */
        NAVIGATE,
        /** 在某台服务器上新建终端。 */
        CONNECT,
        /** 编辑某台服务器。 */
        EDIT_SERVER,
        /** 删除某台服务器（破坏性，需二次确认）。 */
        DELETE_SERVER,
    }
}

/**
 * 由模型当前状态构造命令清单。
 *
 * @param servers 全部服务器（未经搜索/标签过滤——面板是全局入口，
 *   不该被服务器页当前的筛选条件限制，否则用户「搜不到」本该能连的机器）。
 * @param current 当前所在界面，用于标注「已在此」。
 */
fun buildPalette(
    servers: List<Server>,
    current: Destination,
    connectedIds: Set<String>,
): List<PaletteItem> = buildList {
    Destination.entries.forEach { dest ->
        add(
            PaletteItem(
                id = "nav-${dest.name}",
                title = "转到${dest.label}",
                subtitle = "界面",
                kind = PaletteItem.Kind.NAVIGATE,
            ),
        )
    }
    servers.forEach { s ->
        val tag = s.tags.firstOrNull()
        add(
            PaletteItem(
                id = "connect-${s.id}",
                title = "连接 ${s.name}",
                subtitle = buildString {
                    append(s.host)
                    if (s.port != 22) append(":${s.port}")
                    if (s.id in connectedIds) append(" · 已连接")
                    if (tag != null) append(" · $tag")
                },
                kind = PaletteItem.Kind.CONNECT,
            ),
        )
        add(
            PaletteItem(
                id = "edit-${s.id}",
                title = "编辑 ${s.name}",
                subtitle = s.host,
                kind = PaletteItem.Kind.EDIT_SERVER,
            ),
        )
        add(
            PaletteItem(
                id = "delete-${s.id}",
                title = "删除 ${s.name}",
                subtitle = s.host,
                kind = PaletteItem.Kind.DELETE_SERVER,
            ),
        )
    }
}

/**
 * 过滤 + 排序。空查询时保持构造顺序（导航在前）。
 *
 * 多关键词以空格分隔，全部命中才算匹配（AND 语义）——
 * 「prod web」应当只留下同时带这两个词的命令，而不是任一命中。
 */
fun filterPalette(items: List<PaletteItem>, query: String): List<PaletteItem> {
    val terms = query.trim().split(' ').filter { it.isNotBlank() }
    if (terms.isEmpty()) return items
    return items
        .mapNotNull { item ->
            // 全部关键词都要命中（AND）；取各词最低分作为该命令的总分——
            // 最低分代表「最弱的那个约束」，用它排序才不会让某条命令靠一个强词虚高。
            var worst: Int? = null
            for (t in terms) {
                val s = fuzzyScore(item, t) ?: return@mapNotNull null
                worst = if (worst == null) s else minOf(worst, s)
            }
            worst?.let { item to it }
        }
        // 分数越高越前；同分保持原顺序（sortedByDescending 是稳定排序）
        .sortedByDescending { it.second }
        .map { it.first }
}

/**
 * 子序列匹配得分；`null` 表示不匹配。
 *
 * 记分：连续命中每多一位 +8、词首命中 +10、命中位置越靠前越好（上限衰减）。
 * 未命中的字符允许跳过，故 "gw" 能命中 "gateway"。
 */
private fun fuzzyScore(item: PaletteItem, term: String): Int? {
    val haystack = (item.title + " " + (item.subtitle ?: "")).lowercase()
    val needle = term.lowercase()
    var score = 0
    var hi = 0
    var streak = 0
    for (c in needle) {
        val found = haystack.indexOf(c, hi)
        if (found < 0) return null
        // 词首（行首或前一个字符是非字母数字）额外加权
        val atWordStart = found == 0 || !haystack[found - 1].isLetterOrDigit()
        score += 8
        if (atWordStart) score += 10
        streak = if (found == hi && found > 0) streak + 1 else 0
        score += streak * 8
        // 靠前的命中更值钱
        score += ((haystack.length - found).coerceAtMost(40)) / 8
        hi = found + 1
    }
    return score
}
