plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

compose.resources {
    packageOfResClass = "com.barezen.ssh.generated.resources"
}

// ---- 版本号生成：让 gradle.properties 成为**真正**的单一真相源 ----
// 此前 BuildInfo.VERSION 是手写的 const val，靠 BuildInfoTest 断言它与 gradle.properties
// 一致——但那是「两份副本 + 事后校验」，CI 里一改版本就得同时改两处，忘了就变红。
// 改为构建期生成：读 gradle.properties 写出 BuildInfo.kt，源码里不再存第二份。
// CI 打 tag 时只改 gradle.properties 一处即可，改完自动反映到「关于」页与 Release 产物名。
val barezenVersion: String = providers.gradleProperty("barezen.version").get()
val generateBuildInfo by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/buildinfo/kotlin")
    val version = barezenVersion
    inputs.property("version", version)
    outputs.dir(outputDir)
    doLast {
        val pkgDir = outputDir.get().asFile.resolve("com/barezen/ssh")
        pkgDir.mkdirs()
        pkgDir.resolve("BuildInfo.kt").writeText(
            """
// 由 Gradle 从 gradle.properties 的 barezen.version 生成，**不要手改**（下次构建会覆盖）。
// 重新生成本文件：./gradlew :shared:generateBuildInfo
package com.barezen.ssh

/** 构建期常量：版本号的单一真相源是 gradle.properties 的 barezen.version。 */
object BuildInfo {
    const val VERSION: String = "$version"
}
""".trimStart() + "\n"
        )
    }
}

kotlin {
    jvm()

    sourceSets {
        // 生成的 BuildInfo.kt 并入 commonMain：commonMain 里不再手写版本常量
        commonMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/buildinfo/kotlin"))
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.materialIcons.extended)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serializationJson)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.compose.uiTest)
        }
        jvmMain.dependencies {
            implementation(libs.sshj)
            implementation(libs.jediterm.ui)
            implementation(libs.jediterm.core)
            implementation(libs.jna)
        }
        jvmTest.dependencies {
            implementation(libs.sshd.core)
            implementation(libs.sshd.sftp)
            // 测试期静音 SLF4J “No SLF4J providers were found” 噪声，保持测试输出干净
            implementation(libs.slf4j.nop)
            implementation(compose.desktop.currentOs)
        }
    }
}

// 编译前必须先生成 BuildInfo.kt：commonMain 依赖它，故挂成所有 Kotlin 编译的前置。
tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }.configureEach {
    dependsOn(generateBuildInfo)
}
