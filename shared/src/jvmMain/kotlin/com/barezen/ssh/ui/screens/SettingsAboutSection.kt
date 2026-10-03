// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/SettingsAboutSection.kt
package com.barezen.ssh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.ssh.BuildInfo
import com.barezen.ssh.app.SettingsModel
import com.barezen.ssh.ui.components.BzCard
import com.barezen.ssh.ui.components.Muted
import com.barezen.ssh.ui.theme.BareZenMonoBody
import com.barezen.ssh.ui.theme.BareZenSpace

/** 开源组件致谢：**只列真实依赖**，不许漏也不许编。 */
private val Licences = listOf(
    Triple("sshj", "Apache-2.0", "https://github.com/hierynomus/sshj"),
    Triple("JediTerm", "Apache-2.0", "https://github.com/JetBrains/jediterm"),
    Triple("JetBrains Mono", "OFL-1.1", "https://github.com/JetBrains/JetBrainsMono"),
    Triple("Noto Sans SC", "OFL-1.1", "https://github.com/notofonts/noto-cjk"),
    Triple("Material Symbols / Icons", "Apache-2.0", "https://github.com/google/material-design-icons"),
)

/** 设置·关于（apple.html `#settings-about`）：应用卡（图标 + 名称版本 + 构建信息）+ 致谢 + 反馈。 */
@Composable
fun AboutSettingsSection(settings: SettingsModel) {
    val colors = MaterialTheme.colorScheme
    SettingsSectionScaffold("关于") {
        BzCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceContainerLow),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "BZ",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                }
                Spacer(Modifier.width(BareZenSpace.lg))
                Column {
                    Text(
                        "BareZen-SSH",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                    Text(
                        "版本 ${BuildInfo.VERSION}",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(BareZenSpace.xl))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BareZenSpace.lg),
            ) {
                MetaCell("构建号", "2026.1003.1", Modifier.weight(1f))
                MetaCell("Compose", "1.7", Modifier.weight(1f))
                MetaCell("JDK", "21", Modifier.weight(1f))
            }
        }

        BzCard(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(BareZenSpace.lg))
            Text(
                "开源组件致谢",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(BareZenSpace.sm))
            Licences.forEach { (name, licence, url) ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Muted("$licence · $url")
                }
            }
            Spacer(Modifier.height(BareZenSpace.sm))
        }

        // 反馈：仅当 feedbackUrl 非空时渲染（配置缺失就不显示，不造数）
        settings.settings.feedbackUrl?.let { url ->
            BzCard(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(BareZenSpace.lg))
                Text("反馈", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(url, style = BareZenMonoBody, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(BareZenSpace.lg))
            }
        }
    }
}

/** 关于卡的构建信息格（apple.css `.about-meta`）。 */
@Composable
private fun MetaCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
