// shared/src/jvmMain/java/com/barezen/ssh/credentials/MacKeychainStore.java
package com.barezen.ssh.credentials;

import java.util.Arrays;
import java.util.List;

/**
 * macOS 钥匙串：封装 security 命令（generic password 条目）。
 * 保存用 add-generic-password 的 -w 无值形态，秘密从 stdin 读入，避免出现在进程列表；
 * -U 允许覆盖已有条目。
 */
public final class MacKeychainStore implements CredentialStore {

    public static final String SERVICE = "BareZen-SSH";

    private final CommandRunner runner;

    public MacKeychainStore() {
        this(CommandRunner.system());
    }

    public MacKeychainStore(CommandRunner runner) {
        this.runner = runner;
    }

    @Override
    public void save(String id, char[] secret) {
        List<String> command = Arrays.asList(
            "security", "add-generic-password", "-U", "-s", SERVICE, "-a", id, "-w");
        CommandRunner.Result result = runner.run(command, utf8(secret));
        if (result.exitCode != 0) {
            throw new CredentialStoreException(
                "security add-generic-password 失败（退出码 " + result.exitCode + "）：" + result.stderr.trim());
        }
    }

    @Override
    public char[] load(String id) {
        List<String> command = Arrays.asList(
            "security", "find-generic-password", "-s", SERVICE, "-a", id, "-w");
        CommandRunner.Result result = runner.run(command, null);
        if (result.exitCode != 0 || result.stdout.isEmpty()) {
            return null;
        }
        return GnomeKeyringStore.stripTrailingNewline(result.stdout).toCharArray();
    }

    @Override
    public void delete(String id) {
        List<String> command = Arrays.asList(
            "security", "delete-generic-password", "-s", SERVICE, "-a", id);
        CommandRunner.Result result = runner.run(command, null);
        if (result.exitCode != 0) {
            throw new CredentialStoreException(
                "security delete-generic-password 失败（退出码 " + result.exitCode + "）：" + result.stderr.trim());
        }
    }

    @Override
    public boolean isAvailable() {
        return runner.run(Arrays.asList("which", "security"), null).exitCode == 0;
    }

    private static byte[] utf8(char[] chars) {
        return new String(chars).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
