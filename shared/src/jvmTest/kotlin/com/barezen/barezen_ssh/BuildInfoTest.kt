// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/BuildInfoTest.kt
package com.barezen.barezen_ssh

import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 版本号单一真相源守卫：gradle.properties 的 barezen.version 必须与 BuildInfo.VERSION 一致。
 * 任一处被改而另一处没跟上 → 本测试变红。
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

    @Test fun versionMatchesGradleProperties() {
        val props = Properties().apply {
            findGradleProperties().inputStream().use { load(it) }
        }
        val declared = props.getProperty("barezen.version")
        assertNotNull(declared, "gradle.properties 里缺少 barezen.version")
        assertTrue(declared.isNotBlank(), "barezen.version 不得为空")
        assertEquals(declared.trim(), BuildInfo.VERSION, "gradle.properties 与 BuildInfo.VERSION 不一致")
    }
}
