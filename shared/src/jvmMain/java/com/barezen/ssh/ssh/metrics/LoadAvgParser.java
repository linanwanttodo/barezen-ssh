// shared/src/jvmMain/java/com/barezen/ssh/ssh/metrics/LoadAvgParser.java
package com.barezen.ssh.ssh.metrics;

/** /proc/loadavg 纯解析器：String 进、不可变 LoadAvg 出；不足三段或非法数字返回 null。 */
public final class LoadAvgParser {

    private LoadAvgParser() {
    }

    public static LoadAvg parse(String text) {
        if (text == null) {
            return null;
        }
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 3) {
            return null;
        }
        try {
            double load1 = Double.parseDouble(parts[0]);
            double load5 = Double.parseDouble(parts[1]);
            double load15 = Double.parseDouble(parts[2]);
            return new LoadAvg(load1, load5, load15);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
