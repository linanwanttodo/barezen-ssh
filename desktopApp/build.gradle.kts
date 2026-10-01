import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    // 运行期静音 jediterm 的 SLF4J “No SLF4J providers” 噪声（jvmTest 已有同款 nop，见 shared/build.gradle.kts）
    implementation(libs.slf4j.nop)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.barezen.barezen_ssh.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "BareZen-SSH"
            // 版本单一真相源在 gradle.properties；不要在这里硬编码
            packageVersion = providers.gradleProperty("barezen.version").get()
        }
    }
}