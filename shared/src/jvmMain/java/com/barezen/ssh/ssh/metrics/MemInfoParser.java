// shared/src/jvmMain/java/com/barezen/ssh/ssh/metrics/MemInfoParser.java
package com.barezen.ssh.ssh.metrics;

/** /proc/meminfo 纯解析器：取 MemTotal 与 MemAvailable 两行（kB）；缺行或非法数字返回 null。 */
public final class MemInfoParser {

    private MemInfoParser() {
    }

    public static MemInfo parse(String text) {
        if (text == null) {
            return null;
        }
        Long total = null;
        Long available = null;
        for (String line : text.split("\\R")) {
            if (line.startsWith("MemTotal:")) {
                total = valueKb(line);
            } else if (line.startsWith("MemAvailable:")) {
                available = valueKb(line);
            }
        }
        if (total == null || available == null) {
            return null;
        }
        return new MemInfo(total, available);
    }

    /** 形如 "MemTotal: 16384000 kB" 的行取第二个 token 的数值；截断/非法返回 null。 */
    private static Long valueKb(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 2) {
            return null;
        }
        try {
            return Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
