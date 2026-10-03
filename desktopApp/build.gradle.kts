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
        mainClass = "com.barezen.ssh.MainKt"

        nativeDistributions {
            // 每个平台产出其原生支持的格式（jpackage 只能在宿主平台打包，不能交叉产出）：
            //   Linux   -> deb（包管理器）+ AppImage（免安装单文件）
            //   macOS   -> dmg（拖拽安装）+ pkg（安装器，可写 /Applications、支持升级链）
            //   Windows -> msi
            // AppImage 是 Linux 专属、pkg 仅 macOS 有——故在错平台上对应任务自动 SKIP。
            targetFormats(
                TargetFormat.Deb,
                TargetFormat.AppImage,
                TargetFormat.Dmg,
                TargetFormat.Pkg,
                TargetFormat.Msi,
            )
            packageName = "BareZen-SSH"
            // 版本单一真相源在 gradle.properties；不要在这里硬编码
            packageVersion = providers.gradleProperty("barezen.version").get()
            // jpackage 需要显式模块清单（无模块化 descriptor 的 classpath jar 应用）
            includeAllModules = true
            description = "BareZen-SSH：跨平台 SSH 客户端（终端 / SFTP / 端口转发 / 主机监控 / AI 运维侧栏）"
            vendor = "BareZen"
            // 图标：Compose 1.12 的 DSL 既无 linuxIcon/macIcon 也无 jpackageArgs，
            // 故图标由各打包路径自行处理：
            //   - AppImage：packageSingleAppImage 把 icon_256.png 复制进 AppDir 根（appimagetool 要求）
            //   - deb/dmg/msi：jpackage 用 compose 内置的默认图标；换图标需升级 compose 或改用
            //     jpackage 直接调用（见 docs/STATUS 3.6 的已知限制）。
            // 图标：jpackage 各自要 PNG(linux/dmg) / ICNS(mac) / ICO(windows)。
            // 这个 compose DSL 版本没有 linuxIcon/macIcon 属性，改用 jpackage 原生参数
            // （见下方 jpackageArgs），并把 AppDir 阶段缺的图标在 packageSingleAppImage 里补。
        }
    }
}

/**
 * 合成单文件 AppImage。
 *
 * 为什么要自己写：Compose 1.12 的 `packageAppImage` 只跑 jpackage 的 **createAppImage** 阶段，
 * 产物是**一个目录**（`AbstractJPackageTask.getAppImage()` 返回 DirectoryProperty），
 * 并不会调 appimagetool 合成单文件。而 AppImage 的分发意义正在于「单文件、免安装」，
 * 只给目录等于没做。
 *
 * 故：先让 compose 产出 AppDir，再自行调用 appimagetool 合成。
 * appimagetool 需自行提供（PATH 中或 -PappimagetoolPath= 指向），缺失时**明确失败**
 * 而不是静默产出一个目录了事。
 */
// 目录/文件路径在**配置期**就解析成纯 java.io.File：
// 捕获 DirectoryProperty/RegularFile provider 进 doLast 闭包同样会破坏配置缓存。
val appImageDirPath = File(
    layout.buildDirectory.get().asFile,
    "compose/binaries/main-release/app/BareZen-SSH",
).absolutePath
val appImageOutPath = File(
    layout.buildDirectory.get().asFile,
    "compose/binaries/main-release/app/BareZen-SSH.AppImage",
).absolutePath

// 配置期捕获：doLast 里访问 project 会与配置缓存冲突（本项目 configuration-cache=true）。
// 名称与 nativeDistributions.packageName 一致；两处同源在此显式对齐，避免隐式耦合。
val appImageAppName = "BareZen-SSH"
val appImageExecName = "bin/" + appImageAppName
// 资源路径用纯 String 在配置期定好：任何 Gradle 脚本对象（layout/project/file()）
// 捕获进闭包都会让配置缓存报 "cannot serialize Gradle script object references"。
// 运行时再由 File 解析成普通 java.io.File，不持有任何 Gradle 对象。
val appIconRelPath = "src/main/resources/icons/icon_256.png"

val packageSingleAppImage by tasks.registering {
    description = "把 AppDir 合成单文件 AppImage（需 appimagetool）"
    group = "compose desktop"
    // 本任务在执行期读 PATH / 起外部进程（appimagetool），与配置缓存的模型不合：
    // 声明为不兼容，让 Gradle 每次重跑而不是尝试缓存。
    // 打包本就低频，这个取舍是划算的——不为省几秒而把构建脚本写成一堆取巧的常量捕获。
    notCompatibleWithConfigurationCache("执行期解析 appimagetool 路径并启动外部进程")
    dependsOn("packageReleaseAppImage")
    inputs.dir(appImageDirPath).optional()
    outputs.file(appImageOutPath)
    doLast {
        val dir = File(appImageDirPath)
        if (!dir.isDirectory) {
            throw GradleException("AppDir 不存在：$dir（packageReleaseAppImage 未产出？）")
        }
        // jpackage 的 AppDir 阶段**不生成** .desktop（那是 deb 的 jpackage 阶段才做的），
        // 而 appimagetool 强制要求 AppDir 根下有 .desktop，故这里补一份。
        val execPath = "${dir.absolutePath}/$appImageExecName"
        val desktopFile = File(dir, "$appImageAppName.desktop")
        if (!desktopFile.exists()) {
            desktopFile.writeText(
                """
                [Desktop Entry]
                Type=Application
                Name=BareZen-SSH
                Comment=跨平台 SSH 客户端（终端 / SFTP / 端口转发 / 主机监控 / AI 运维侧栏）
                Exec=$execPath %f
                Icon=$appImageAppName
                Terminal=false
                Categories=Network;
                Keywords=ssh;sftp;terminal;remote;
                """.trimIndent()
            )
            logger.lifecycle("[AppImage] 已补写 AppDir 根下的 ${desktopFile.name}（jpackage AppDir 阶段不产出它）")
        }
        // 同理，AppDir 阶段也不产出图标；appimagetool 要求 <AppName>.png 在 AppDir 根下。
        val appIcon = File(appIconRelPath)
        val iconTarget = File(dir, "$appImageAppName.png")
        if (appIcon.isFile && !iconTarget.exists()) {
            appIcon.copyTo(iconTarget, overwrite = true)
            logger.lifecycle("[AppImage] 已复制应用图标到 AppDir 根下（jpackage AppDir 阶段不产出它）")
        }
        // 定位 appimagetool：优先 -PappimagetoolPath，其次 PATH。
        // 整段内联在 doLast 内、只操作 java.io.File —— 捕获任何 Gradle 脚本对象
        // （含顶层函数引用）都会让配置缓存报 "cannot serialize Gradle script object references"。
        val explicit = (findProperty("appimagetoolPath") as String?)?.takeIf { it.isNotBlank() }
        val tool: File? = if (explicit != null) {
            val f = File(explicit)
            if (f.canExecute()) f else null
        } else {
            (System.getenv("PATH") ?: "").split(File.pathSeparator)
                .firstNotNullOfOrNull { d ->
                    if (d.isBlank()) null else File(d, "appimagetool").takeIf { it.canExecute() }
                }
        }
        if (tool == null) {
            throw GradleException(
                "找不到 appimagetool，无法合成单文件 AppImage。" +
                    "Compose 的 packageAppImage 只产出目录，必须由 appimagetool 合成单文件。" +
                    "解决办法：下载 AppImageKit 的 appimagetool-x86_64.AppImage 并 chmod +x 放入 PATH，" +
                    "或显式指定 -PappimagetoolPath=/path/to/appimagetool"
            )
        }
        val out = File(appImageOutPath)
        out.parentFile.mkdirs()
        // 用 ProcessBuilder 而非 Gradle 的 exec {}：后者在 doLast 闭包里拿不到 Project，
        // 且与配置缓存不兼容。
        val pb = ProcessBuilder(
            tool.absolutePath,
            dir.absolutePath,          // AppDir
            out.absolutePath,          // 输出的 .AppImage
        ).redirectErrorStream(true)
        pb.directory(out.parentFile)
        logger.lifecycle("[AppImage] 调用 appimagetool：${tool.absolutePath}")
        val proc = pb.start()
        val log = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        if (proc.exitValue() != 0 || !out.isFile) {
            throw GradleException("appimagetool 失败（退出码 ${proc.exitValue()}）：\n$log")
        }
        logger.lifecycle("[AppImage] 单文件 AppImage 已生成：${out.name}（${out.length() / 1024 / 1024} MB）")
    }
}

