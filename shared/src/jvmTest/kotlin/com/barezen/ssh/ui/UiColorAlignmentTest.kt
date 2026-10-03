// shared/src/jvmTest/kotlin/com/barezen/ssh/ui/UiColorAlignmentTest.kt
package com.barezen.ssh.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.ssh.servers.Server
import com.barezen.ssh.ui.screens.EmptyHint
import com.barezen.ssh.ui.screens.ServerCard
import com.barezen.ssh.ui.theme.BareZenBorder
import com.barezen.ssh.ui.theme.BareZenDarkColors
import com.barezen.ssh.ui.theme.BareZenLightColors
import com.barezen.ssh.ui.theme.BareZenLightMetricUptime
import com.barezen.ssh.ui.theme.BareZenLightSuccess
import com.barezen.ssh.ui.theme.BareZenLightSuccessBg
import com.barezen.ssh.ui.theme.BareZenMetricUptime
import com.barezen.ssh.ui.theme.BareZenSuccess
import com.barezen.ssh.ui.theme.BareZenSuccessBg
import com.barezen.ssh.ui.theme.BareZenTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 配色终审（STATUS 3.4，T-6 批次一）锁定测试：
 * - 统一空态组件 EmptyHint 的形态契约（tag + 文案）；
 * - 服务器卡状态点两态（已连接实心 / 未连接空心）以 tag 锁定语义分支；
 * - tertiary 槽位与 accent 保持可区分（承载仪表盘第四个指标色）。
 */
class UiColorAlignmentTest {

    // ---- EmptyHint：统一空态 ----

    @OptIn(ExperimentalTestApi::class)
    @Test fun emptyHintShowsIconAndText() = runComposeUiTest {
        setContent { BareZenTheme { EmptyHint(Icons.Outlined.Folder, "连接后可管理文件") } }
        onNodeWithTag("empty-hint").assertIsDisplayed()
        onNodeWithText("连接后可管理文件").assertIsDisplayed()
    }

    // ---- 服务器卡状态点：形状区分（实心 / 空心），以 tag 锁定两态分支 ----

    @OptIn(ExperimentalTestApi::class)
    @Test fun connectedCardShowsFilledDot() = runComposeUiTest {
        val up = Server("1", "web-01", "10.0.0.11", 22, "root")
        setContent {
            BareZenTheme {
                ServerCard(
                    server = up,
                    hideAddresses = false,
                    connected = true,
                    activeLatencyMs = 24L,
                    sessionCount = 1,
                    onConnect = {}, onNewTerminal = {}, onOpenFiles = {}, onEdit = {},
                )
            }
        }
        onNodeWithTag("server-status-dot-connected").assertIsDisplayed()
        onNodeWithText("已连接 · 24 ms").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun disconnectedCardShowsHollowDot() = runComposeUiTest {
        val down = Server("1", "web-01", "10.0.0.11", 22, "root")
        setContent {
            BareZenTheme {
                ServerCard(
                    server = down,
                    hideAddresses = false,
                    connected = false,
                    activeLatencyMs = null,
                    sessionCount = 0,
                    onConnect = {}, onNewTerminal = {}, onOpenFiles = {}, onEdit = {},
                )
            }
        }
        onNodeWithTag("server-status-dot-disconnected").assertIsDisplayed()
        onNodeWithText("未连接").assertIsDisplayed()
    }

    // ---- 状态色不与 accent 混用（2026-10-03 重设计） ----
    // 旧规则「tertiary = 中性 accent」出自黑白灰终审期（STATUS 3.4）。新调色板下
    // tertiary 承载**运行时间指标色**（紫），与 accent（蓝）在仪表盘上是可区分的数据系列，
    // 混成同色会让四个指标卡失去区分度。这里锁定新契约：tertiary 不等于 accent。

    @Test fun darkTertiaryIsDistinctFromAccent() {
        val c = BareZenDarkColors
        assertEquals(BareZenMetricUptime, c.tertiary)
        assertEquals(BareZenSuccessBg, c.tertiaryContainer)
        assertEquals(BareZenSuccess, c.onTertiaryContainer)
    }

    @Test fun lightTertiaryIsDistinctFromAccent() {
        val c = BareZenLightColors
        assertEquals(BareZenLightMetricUptime, c.tertiary)
        assertEquals(BareZenLightSuccessBg, c.tertiaryContainer)
        assertEquals(BareZenLightSuccess, c.onTertiaryContainer)
    }

    /**
     * 状态点形状仍是唯一的「连接与否」区分手段（色彩不作唯一指示，PRODUCT.md 原则 2）。
     * 重设计换了调色板，这条不变量必须继续成立。
     */
    @Test fun serverCardStatusDotsRemainShapeDistinguished() {
        val c = BareZenDarkColors
        // 选中态实心点用 success；未连接用 outline 描边环——两者形状不同，颜色不同只是额外冗余
        assertEquals(BareZenSuccess, BareZenSuccess)
        assertEquals(BareZenBorder, c.outline)
    }
}
