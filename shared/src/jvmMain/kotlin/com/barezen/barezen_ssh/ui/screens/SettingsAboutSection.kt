// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsAboutSection.kt
package com.barezen.barezen_ssh.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.barezen_ssh.BuildInfo
import com.barezen.barezen_ssh.app.SettingsModel

/** 开源组件致谢（设计 §8.6）：**只列真实依赖**，不许漏也不许编。 */
private val Licences = listOf(
    Triple("sshj", "Apache-2.0", "https://github.com/hierynomus/sshj"),
    Triple("JediTerm", "Apache-2.0", "https://github.com/JetBrains/jediterm"),
    Triple("JetBrains Mono", "OFL-1.1", "https://github.com/JetBrains/JetBrainsMono"),
    Triple("Noto Sans SC", "OFL-1.1", "https://github.com/notofonts/noto-cjk"),
    Triple("Material Symbols / Icons", "Apache-2.0", "https://github.com/google/material-design-icons"),
)

/** 设置·关于分类（设计 §8.6/§12）：真实本地版本 + 开源致谢 + 可配置反馈入口。 */
@Composable
fun AboutSettingsSection(settings: SettingsModel) {
    SettingsSectionScaffold("关于") {
        Text(
            "BareZen-SSH ${BuildInfo.VERSION}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Text(
            "开源组件致谢",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
        Licences.forEach { (name, licence, url) ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    "$licence · $url",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 反馈：仅当 feedbackUrl 非空时渲染（配置缺失就不显示，不造数）
        settings.settings.feedbackUrl?.let { url ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("反馈", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(url, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
