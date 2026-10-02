package com.barezen.ssh.app

import com.barezen.ssh.servers.FileServerRepository
import com.barezen.ssh.settings.FileSettingsRepository
import com.barezen.ssh.ssh.JvmSshClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

// 注意：本项目仅 JVM 目标，不用 expect/actual，直接扩展函数：
fun AppModel.Companion.real(): AppModel = AppModel(
    repo = FileServerRepository(),
    ssh = JvmSshClient(),
    scope = CoroutineScope(Dispatchers.Default),
    settings = SettingsModel(FileSettingsRepository()).also { it.load() },
).also { it.autoConnectIfConfigured() }
