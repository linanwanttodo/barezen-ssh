// shared/src/jvmMain/java/com/barezen/ssh/ssh/sftp/SftpPaths.java
package com.barezen.ssh.ssh.sftp;

/** SFTP 绝对路径纯工具：拼接、取上级、取末段。根目录一律归一为 "/"。 */
public final class SftpPaths {

    private SftpPaths() {
    }

    /** 拼接父目录与单段名称；parent 为 null/空按根目录处理，容忍多余尾斜杠。 */
    public static String join(String parent, String name) {
        String base = normalizeDir(parent);
        if (name == null || name.isEmpty()) {
            return base;
        }
        if (base.equals("/")) {
            return "/" + name;
        }
        return base + "/" + name;
    }

    /** 上级目录；根目录的上级仍是根目录。容忍尾斜杠与 null/空。 */
    public static String parentOf(String path) {
        String p = stripTrailing(path);
        if (p.equals("/")) {
            return "/";
        }
        int idx = p.lastIndexOf('/');
        return idx <= 0 ? "/" : p.substring(0, idx);
    }

    /** 末段名称；根目录返回 "/"，裸段原样返回。 */
    public static String nameOf(String path) {
        String p = stripTrailing(path);
        if (p.equals("/")) {
            return "/";
        }
        int idx = p.lastIndexOf('/');
        return idx < 0 ? p : p.substring(idx + 1);
    }

    private static String normalizeDir(String path) {
        String p = stripTrailing(path);
        return p.isEmpty() ? "/" : p;
    }

    private static String stripTrailing(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String p = path;
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
