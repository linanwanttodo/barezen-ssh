// shared/src/jvmTest/kotlin/com/barezen/ssh/BuildInfoTest.kt
package com.barezen.ssh

import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 版本号单一真相源守卫。
 *
 * 2026-10-03 起 `BuildInfo` **由 Gradle 从 gradle.properties 生成**（shared/build.gradle.kts
 * 的 generateBuildInfo），源码树里不再存第二份手写常量——CI 打 tag 时只改
 * gradle.properties 一处即可。
 *
 * 本测试锁的是「生成链路没断」：若有人把手写常量加回源码、或生成任务没挂到编译上，
 * 这里会因「源码里出现第二份版本声明」或「版本与 gradle.properties 不符」而转红。
 */
class BuildInfoTest {

    /** 从 user.dir 向上找 gradle.properties（Gradle 测试工作目录随 source set 而变，不能写死层级）。 */
    private fun findGradleProperties(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "gradle.properties")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("从 ${System.getProperty("user.dir")} 向上找不到 gradle.properties")
    }

    @Test fun generatedVersionMatchesGradleProperties() {
        val props = Properties().apply {
            findGradleProperties().inputStream().use { load(it) }
        }
        val declared = props.getProperty("barezen.version")
        assertNotNull(declared, "gradle.properties 里缺少 barezen.version")
        assertTrue(declared.isNotBlank(), "barezen.version 不得为空")
        assertEquals(
            declared.trim(),
            BuildInfo.VERSION,
            "生成的 BuildInfo.VERSION 与 gradle.properties 不一致（generateBuildInfo 没跑？）",
        )
    }

    /**
     * 源码树里**不得**再有第二份手写版本常量。
     * 曾经的 `src/commonMain/.../BuildInfo.kt` 就是一个字面量副本，与本测试的旧版互为「事后校验」；
     * 改成生成后，副本就是纯风险（改一处忘另一处）。
     */
    @Test fun noHandWrittenVersionCopyInSourceTree() {
        val stale = File("src/commonMain/kotlin/com/barezen/ssh/BuildInfo.kt")
        assertTrue(
            !stale.exists(),
            "BuildInfo 应由 Gradle 生成，源码树里不该再有 $stale",
        )
    }
}
