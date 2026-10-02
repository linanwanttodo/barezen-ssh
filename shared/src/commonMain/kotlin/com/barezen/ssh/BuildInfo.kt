// shared/src/commonMain/kotlin/com/barezen/ssh/BuildInfo.kt
package com.barezen.ssh

/**
 * 构建期常量。
 *
 * 版本号的**单一真相源**是 `gradle.properties` 的 `barezen.version`；
 * 本常量必须与之保持一致 —— 由 `BuildInfoTest` 守卫，改一处忘另一处会变红。
 */
object BuildInfo {
    const val VERSION: String = "0.1.0"
}
