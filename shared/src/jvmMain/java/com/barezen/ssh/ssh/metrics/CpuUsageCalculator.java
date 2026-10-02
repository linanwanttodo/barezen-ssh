// shared/src/jvmMain/java/com/barezen/ssh/ssh/metrics/CpuUsageCalculator.java
package com.barezen.ssh.ssh.metrics;

/**
 * /proc/stat 首行（"cpu  user nice system idle iowait ..."）两次采样的 CPU 占用率纯计算：
 * busy 增量 / 总增量，结果夹在 0-100；任一采样缺失或非法、总增量不大于 0 返回 null。
 */
public final class CpuUsageCalculator {

    private static final int IDX_IDLE = 3;
    private static final int IDX_IOWAIT = 4;

    private CpuUsageCalculator() {
    }

    public static Double delta(String firstLine1, String firstLine2) {
        long[] a = counters(firstLine1);
        long[] b = counters(firstLine2);
        if (a == null || b == null) {
            return null;
        }
        long totalDelta = 0;
        long idleDelta = 0;
        for (int i = 0; i < b.length; i++) {
            long d = b[i] - (i < a.length ? a[i] : 0);
            totalDelta += d;
            if (i == IDX_IDLE || i == IDX_IOWAIT) {
                idleDelta += d;
            }
        }
        if (totalDelta <= 0) {
            return null;
        }
        double pct = (totalDelta - idleDelta) * 100.0 / totalDelta;
        if (pct < 0.0) {
            return 0.0;
        }
        if (pct > 100.0) {
            return 100.0;
        }
        return pct;
    }

    /** 首行解析为计数器数组；非 "cpu" 行、截断或非法数字返回 null。 */
    private static long[] counters(String line) {
        if (line == null) {
            return null;
        }
        String[] tok = line.trim().split("\\s+");
        if (tok.length < 2 || !tok[0].equals("cpu")) {
            return null;
        }
        long[] values = new long[tok.length - 1];
        for (int i = 1; i < tok.length; i++) {
            try {
                values[i - 1] = Long.parseLong(tok[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return values;
    }
}
