// shared/src/commonMain/kotlin/com/barezen/ssh/ui/theme/Type.kt
package com.barezen.ssh.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.barezen.ssh.generated.resources.JetBrainsMono_Bold
import com.barezen.ssh.generated.resources.JetBrainsMono_Medium
import com.barezen.ssh.generated.resources.JetBrainsMono_Regular
import com.barezen.ssh.generated.resources.NotoSansSC_Bold
import com.barezen.ssh.generated.resources.NotoSansSC_Medium
import com.barezen.ssh.generated.resources.NotoSansSC_Regular
import com.barezen.ssh.generated.resources.Res
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

/**
 * 字号阶（apple.css 各处 `font-size` / `line-height`）：
 * 界面正文 13sp/20.8，辅助与标签 11sp/15.4、次级 12sp/18，说明行 13sp 随正文。
 * 终端区 13sp 等宽由 JediTerm 侧接管，不在此体系内。
 */
val BareZenTypography: Typography
    @Composable get() = Typography(
        titleLarge = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
        bodySmall = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
    )

/** 等宽样式对（屏内显式引用：主机地址用 Body、延迟/指标/路径用 Small）。 */
val BareZenMonoBody: TextStyle
    @Composable get() = TextStyle(fontFamily = BareZenMonoFontFamily, fontSize = 13.sp, lineHeight = 20.sp)
val BareZenMonoSmall: TextStyle
    @Composable get() = TextStyle(fontFamily = BareZenMonoFontFamily, fontSize = 11.sp, lineHeight = 15.sp)

/** 指标大数值（apple.css `.metric-value`：等宽 30sp/600/-0.02em，配套 14sp 单位）。 */
val BareZenMonoMetric: TextStyle
    @Composable get() = TextStyle(
        fontFamily = BareZenMonoFontFamily,
        fontSize = 30.sp,
        lineHeight = 33.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
    )
