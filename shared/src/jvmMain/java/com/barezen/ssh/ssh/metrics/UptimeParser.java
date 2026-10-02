// shared/src/jvmMain/java/com/barezen/ssh/ssh/metrics/UptimeParser.java
package com.barezen.ssh.ssh.metrics;

/** /proc/uptime 纯解析器：取第一字段（开机秒数）；空输入或非法数字返回 null。 */
public final class UptimeParser {

    private UptimeParser() {
    }

    public static Uptime parse(String text) {
        if (text == null) {
            return null;
        }
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 1 || parts[0].isEmpty()) {
            return null;
        }
        try {
            return new Uptime(Double.parseDouble(parts[0]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
