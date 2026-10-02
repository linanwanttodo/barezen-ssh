// shared/src/jvmTest/kotlin/com/barezen/ssh/app/UiScaleTest.kt
package com.barezen.ssh.app

import androidx.compose.ui.unit.Density
import com.barezen.ssh.ui.theme.scaledDensity
import kotlin.test.Test
import kotlin.test.assertEquals

class UiScaleTest {
    @Test fun scaleMultipliesDensityOnly() {
        val base = Density(density = 2f, fontScale = 1.3f)
        val scaled = scaledDensity(base, 1.5f)
        assertEquals(3f, scaled.density)
        assertEquals(1.3f, scaled.fontScale, "fontScale 不得被缩放影响，否则字号被缩放两次")
    }

    @Test fun scaleOneIsIdentity() {
        val base = Density(density = 1f, fontScale = 1f)
        assertEquals(1f, scaledDensity(base, 1.0f).density)
    }
}
