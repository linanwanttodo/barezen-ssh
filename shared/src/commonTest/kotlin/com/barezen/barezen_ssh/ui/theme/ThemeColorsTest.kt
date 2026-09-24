// shared/src/commonTest/kotlin/com/barezen/barezen_ssh/ui/theme/ThemeColorsTest.kt
package com.barezen.barezen_ssh.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeColorsTest {
    @Test fun primaryMatchesSpec() { assertEquals(Color(0xFF81D5CB), BareZenPrimary) }
    @Test fun onPrimaryMatchesSpec() { assertEquals(Color(0xFF003733), BareZenOnPrimary) }
    @Test fun primaryContainerMatchesSpec() { assertEquals(Color(0xFF00504A), BareZenPrimaryContainer) }
    @Test fun secondaryContainerMatchesSpec() { assertEquals(Color(0xFF324B48), BareZenSecondaryContainer) }
    @Test fun surfaceMatchesSpec() { assertEquals(Color(0xFF101413), BareZenSurface) }
    @Test fun onSurfaceMatchesSpec() { assertEquals(Color(0xFFE0E3E1), BareZenOnSurface) }
    @Test fun scLowMatchesSpec() { assertEquals(Color(0xFF191C1C), BareZenSurfaceContainerLow) }
    @Test fun scMatchesSpec() { assertEquals(Color(0xFF1D2020), BareZenSurfaceContainer) }
    @Test fun scHighMatchesSpec() { assertEquals(Color(0xFF272B2A), BareZenSurfaceContainerHigh) }
    @Test fun scHighestMatchesSpec() { assertEquals(Color(0xFF323535), BareZenSurfaceContainerHighest) }
    @Test fun outlineMatchesSpec() { assertEquals(Color(0xFF899391), BareZenOutline) }
    @Test fun outlineVariantMatchesSpec() { assertEquals(Color(0xFF3F4947), BareZenOutlineVariant) }
    @Test fun tertiaryMatchesSpec() { assertEquals(Color(0xFFAEC9E6), BareZenTertiary) }
    @Test fun errorMatchesSpec() { assertEquals(Color(0xFFFFB4AB), BareZenError) }
}
