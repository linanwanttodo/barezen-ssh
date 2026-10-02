// shared/src/jvmMain/java/com/barezen/ssh/credentials/GnomeKeyringStore.java
package com.barezen.ssh.credentials;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Linux/GNOME 钥匙串：封装 secret-tool（org.freedesktop.secrets 的命令行入口）。
 * 秘密经 stdin 传递，不出现在进程参数里；D-Bus 会话缺失时 isAvailable 为 false，
 * 由调用方降级到内存存储。真实 D-Bus 路径在无钥匙串环境不可测，仅测命令拼装与结果解析。
 */
public final class GnomeKeyringStore implements CredentialStore {

    public static final String LABEL = "BareZen-SSH";
    public static final String ATTRIBUTE = "barezen-id";

    private final CommandRunner runner;
    private final Map<String, String> env;

    public GnomeKeyringStore() {
        this(CommandRunner.system(), System.getenv());
    }

    public GnomeKeyringStore(CommandRunner runner, Map<String, String> env) {
        this.runner = runner;
        this.env = env;
    }

    @Override
    public void save(String id, char[] secret) {
        List<String> command = Arrays.asList("secret-tool", "store", "--label=" + LABEL, ATTRIBUTE, id);
        CommandRunner.Result result = runner.run(command, utf8(secret));
        if (result.exitCode != 0) {
            throw new CredentialStoreException(
                "secret-tool store 失败（退出码 " + result.exitCode + "）：" + result.stderr.trim());
        }
    }

    @Override
    public char[] load(String id) {
        List<String> command = Arrays.asList("secret-tool", "lookup", ATTRIBUTE, id);
        CommandRunner.Result result = runner.run(command, null);
        if (result.exitCode != 0 || result.stdout.isEmpty()) {
            return null;
        }
        return stripTrailingNewline(result.stdout).toCharArray();
    }

    @Override
    public void delete(String id) {
        List<String> command = Arrays.asList("secret-tool", "clear", ATTRIBUTE, id);
        CommandRunner.Result result = runner.run(command, null);
        if (result.exitCode != 0) {
            throw new CredentialStoreException(
                "secret-tool clear 失败（退出码 " + result.exitCode + "）：" + result.stderr.trim());
        }
    }

    @Override
    public boolean isAvailable() {
        String dbus = env.get("DBUS_SESSION_BUS_ADDRESS");
        if (dbus == null || dbus.isBlank()) {
            return false;
        }
        return runner.run(Arrays.asList("which", "secret-tool"), null).exitCode == 0;
    }

    /** 只去掉末尾一个换行；秘密本身含内部换行时原样保留。 */
    static String stripTrailingNewline(String value) {
        if (value.endsWith("\r\n")) {
            return value.substring(0, value.length() - 2);
        }
        if (value.endsWith("\n")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static byte[] utf8(char[] chars) {
        return new String(chars).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
