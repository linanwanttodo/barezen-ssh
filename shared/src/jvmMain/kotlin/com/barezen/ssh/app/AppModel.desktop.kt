package com.barezen.ssh.app

import com.barezen.ssh.credentials.KeychainCredentialResolver
import com.barezen.ssh.servers.FileServerRepository
import com.barezen.ssh.settings.FileSettingsRepository
import com.barezen.ssh.ssh.JvmSshClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// 注意：本项目仅 JVM 目标，不用 expect/actual，直接扩展函数：
fun AppModel.Companion.real(): AppModel {
    // 会话级资源的载体协程作用域：隧道管理器本身不起协程，作用域只为将来可能的会话级后台任务预留，
    // 用 SupervisorJob 保证单个会话的失败不会连坐其他会话（与 AppModel 的 scope 同性质）。
    val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    return AppModel(
        repo = FileServerRepository(),
        ssh = JvmSshClient(),
        scope = CoroutineScope(Dispatchers.Default),
        settings = SettingsModel(FileSettingsRepository()).also { it.load() },
        // 平台钥匙串接线（T-1）：原生钥匙串可用时读写真实钥匙串；不可用时其 isAvailable() 为 false，
        // UI 走「系统钥匙串不可用，凭据不会保存」分支，行为与接线前（Noop）完全一致。
        credentials = KeychainCredentialResolver.platformDefault(),
        // 隧道所有权上移到会话（T-4 刀4）：每条会话连上时创建一个 JvmSessionResources，
        // 切标签不再销毁它（Host 只订阅），关标签/断开由注册表统一释放。
        resourcesFactory = { session -> JvmSessionResources(sessionScope, session) },
    ).also { it.autoConnectIfConfigured() }
}
