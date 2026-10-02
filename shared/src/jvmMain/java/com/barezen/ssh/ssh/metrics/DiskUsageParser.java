// shared/src/jvmMain/java/com/barezen/ssh/ssh/metrics/DiskUsageParser.java
package com.barezen.ssh.ssh.metrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * df -P（POSIX 输出）纯解析器：跳过表头与 tmpfs/devtmpfs/overlay 等伪文件系统，
 * 保留根分区与真实磁盘挂载点；单行畸形则跳过该行，不影响其余行。
 */
public final class DiskUsageParser {

    /** 以第一列（文件系统名/设备名）识别的伪文件系统。 */
    private static final Set<String> PSEUDO = new HashSet<>(Arrays.asList(
            "tmpfs", "devtmpfs", "udev", "overlay", "proc", "sysfs", "cgroup", "cgroup2",
            "squashfs", "efivarfs", "binfmt_misc", "debugfs", "tracefs", "configfs",
            "bpf", "hugetlbfs", "mqueue", "shm", "none", "securityfs", "pstore", "fusectl"));

    private DiskUsageParser() {
    }

    public static List<DiskUsage> parseDf(String text) {
        if (text == null) {
            return Collections.emptyList();
        }
        List<DiskUsage> out = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] tok = trimmed.split("\\s+");
            if (tok.length < 6 || tok[0].equals("Filesystem") || PSEUDO.contains(tok[0])) {
                continue;
            }
            Long total = parseLong(tok[1]);
            Long used = parseLong(tok[2]);
            Long available = parseLong(tok[3]);
            Integer capacity = parseIntPct(tok[4]);
            if (total == null || used == null || available == null || capacity == null) {
                continue;
            }
            // df -P 的挂载点可能含空格：第 5 列之后的所有 token 重组为挂载点
            String mount = String.join(" ", Arrays.copyOfRange(tok, 5, tok.length));
            out.add(new DiskUsage(tok[0], total, used, available, capacity, mount));
        }
        return Collections.unmodifiableList(out);
    }

    private static Long parseLong(String token) {
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 容量列形如 "33%"；缺百分号或非法数字返回 null。 */
    private static Integer parseIntPct(String token) {
        if (token == null || !token.endsWith("%")) {
            return null;
        }
        try {
            return Integer.parseInt(token.substring(0, token.length() - 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
