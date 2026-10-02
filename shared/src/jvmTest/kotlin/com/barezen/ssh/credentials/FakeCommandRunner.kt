// shared/src/jvmTest/kotlin/com/barezen/ssh/credentials/FakeCommandRunner.kt
package com.barezen.ssh.credentials

/**
 * 测试用命令执行器：记录每次调用，并按脚本逐条回放结果。
 * 未命中脚本时抛 AssertionError，避免测试静默走错分支。
 */
class FakeCommandRunner : CommandRunner {

    class Invocation(val command: List<String>, val stdin: ByteArray?)

    class Scripted(val exitCode: Int, val stdout: String = "", val stderr: String = "")

    val invocations = mutableListOf<Invocation>()
    private val scripts = ArrayDeque<Scripted>()

    /** 预置下一条命令的回放结果。 */
    fun enqueue(exitCode: Int, stdout: String = "", stderr: String = ""): FakeCommandRunner {
        scripts.addLast(Scripted(exitCode, stdout, stderr))
        return this
    }

    fun lastCommand(): List<String> = invocations.last().command.map { it }
    fun lastStdin(): String? = invocations.last().stdin?.toString(Charsets.UTF_8)
    fun commandCount(): Int = invocations.size

    override fun run(command: List<String>, stdin: ByteArray?): CommandRunner.Result {
        invocations.add(Invocation(command.map { it }, stdin))
        val script = scripts.removeFirstOrNull()
            ?: throw AssertionError("未预置命令结果：$command")
        return CommandRunner.Result(script.exitCode, script.stdout, script.stderr)
    }
}
