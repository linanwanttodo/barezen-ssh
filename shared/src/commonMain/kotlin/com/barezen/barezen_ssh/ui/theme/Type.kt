// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/theme/Type.kt
package com.barezen.barezen_ssh.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import barezen_ssh.shared.generated.resources.JetBrainsMono_Bold
import barezen_ssh.shared.generated.resources.JetBrainsMono_Medium
import barezen_ssh.shared.generated.resources.JetBrainsMono_Regular
import barezen_ssh.shared.generated.resources.NotoSansSC_Bold
import barezen_ssh.shared.generated.resources.NotoSansSC_Medium
import barezen_ssh.shared.generated.resources.NotoSansSC_Regular
import barezen_ssh.shared.generated.resources.Res
import org.jetbrains.compose.resources.Font

/** UI 正文字族：Noto Sans SC 随包分发（OFL，Sans/SubsetOTF/SC）。 */
val BareZenUiFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.NotoSansSC_Regular, FontWeight.Normal),
        Font(Res.font.NotoSansSC_Medium, FontWeight.Medium),
        Font(Res.font.NotoSansSC_Bold, FontWeight.Bold),
        // 静态 OTF 无 600：SemiBold 显式复用 Bold 字面，避免合成加粗
        Font(Res.font.NotoSansSC_Bold, FontWeight.SemiBold),
    )

/** 等宽字族：终端外的主机地址、延迟、指标数值（设计包 §3 等宽行）。 */
val BareZenMonoFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.JetBrainsMono_Regular, FontWeight.Normal),
        Font(Res.font.JetBrainsMono_Medium, FontWeight.Medium),
        Font(Res.font.JetBrainsMono_Bold, FontWeight.Bold),
    )

/** 设计包 §3 字号阶（行高按倍率换算为字面值，保证与测试同字面量）。 */
val BareZenTypography: Typography
    @Composable get() = Typography(
        titleLarge = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 24.sp, lineHeight = 31.2.sp, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 18.sp, lineHeight = 25.2.sp, fontWeight = FontWeight.Bold),
        titleSmall = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 14.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
        bodySmall = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 11.sp, lineHeight = 15.4.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
        labelSmall = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 11.sp, lineHeight = 15.4.sp, fontWeight = FontWeight.Medium),
    )

/** 等宽样式对（屏内显式引用：主机地址用 Body、延迟/指标用 Small）。 */
val BareZenMonoBody: TextStyle
    @Composable get() = TextStyle(fontFamily = BareZenMonoFontFamily, fontSize = 13.sp, lineHeight = 19.5.sp)
val BareZenMonoSmall: TextStyle
    @Composable get() = TextStyle(fontFamily = BareZenMonoFontFamily, fontSize = 11.sp, lineHeight = 15.4.sp)
