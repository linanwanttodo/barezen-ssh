package com.barezen.ssh.app

import com.barezen.ssh.credentials.KeychainCredentialResolver
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
    // 平台钥匙串接线（T-1）：原生钥匙串可用时读写真实钥匙串；不可用时其 isAvailable() 为 false，
    // UI 走「系统钥匙串不可用，凭据不会保存」分支，行为与接线前（Noop）完全一致。
    credentials = KeychainCredentialResolver.platformDefault(),
).also { it.autoConnectIfConfigured() }
