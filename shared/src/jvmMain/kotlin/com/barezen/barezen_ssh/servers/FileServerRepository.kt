// 骨架：Task 4 落地真实文件存储实现；本任务先建可编译空实现（内存列表）。
package com.barezen.barezen_ssh.servers

class FileServerRepository : ServerRepository {
    override fun list(): List<Server> = emptyList()
}
