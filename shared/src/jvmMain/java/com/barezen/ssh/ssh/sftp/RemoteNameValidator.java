// shared/src/jvmMain/java/com/barezen/ssh/ssh/sftp/RemoteNameValidator.java
package com.barezen.ssh.ssh.sftp;

/**
 * 远端条目名校验：拒绝空名、路径分隔符、以及 "." / ".." 这类目录导航名。
 * 纯函数，无 IO；UI 在发出 rename 请求前用它做前置拦截，避免把非法名送到服务端。
 */
public final class RemoteNameValidator {

    private RemoteNameValidator() {
    }

    /** 合法返回 null，非法返回面向用户的中文原因。 */
    public static String validate(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "名称不能为空";
        }
        String n = name.trim();
        if (n.equals(".") || n.equals("..")) {
            return "名称不能是 . 或 ..";
        }
        if (n.indexOf('/') >= 0) {
            return "名称不能包含 /";
        }
        if (n.indexOf('\\') >= 0) {
            return "名称不能包含 \\";
        }
        return null;
    }

    /** 便捷判定。 */
    public static boolean isValid(String name) {
        return validate(name) == null;
    }
}
