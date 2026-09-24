// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/theme/Type.kt
package com.barezen.barezen_ssh.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import barezen_ssh.shared.generated.resources.Res
import barezen_ssh.shared.generated.resources.JetBrainsMono_Regular
import barezen_ssh.shared.generated.resources.JetBrainsMono_Medium
import barezen_ssh.shared.generated.resources.JetBrainsMono_Bold
import org.jetbrains.compose.resources.Font

val BareZenFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.JetBrainsMono_Regular, FontWeight.Normal),
        Font(Res.font.JetBrainsMono_Medium, FontWeight.Medium),
        Font(Res.font.JetBrainsMono_Bold, FontWeight.Bold),
    )

val BareZenTypography = Typography()
