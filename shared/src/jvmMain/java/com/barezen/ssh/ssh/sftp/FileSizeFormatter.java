// shared/src/jvmMain/java/com/barezen/ssh/ssh/sftp/FileSizeFormatter.java
package com.barezen.ssh.ssh.sftp;

import java.util.Locale;

/** 文件大小人类可读格式：1024 进制，B/KB/MB/GB，二级单位一位小数并去掉尾随 .0；超大值封顶 GB。 */
public final class FileSizeFormatter {

    private static final long UNIT = 1024L;
    private static final String[] UNITS = {"KB", "MB", "GB"};

    private FileSizeFormatter() {
    }

    public static String format(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        if (bytes < UNIT) {
            return bytes + " B";
        }
        double value = bytes;
        int unitIndex = -1;
        while (unitIndex < UNITS.length - 1 && value >= UNIT) {
            value /= UNIT;
            unitIndex++;
        }
        String text = String.format(Locale.ROOT, "%.1f", value);
        if (text.endsWith(".0")) {
            text = text.substring(0, text.length() - 2);
        }
        return text + " " + UNITS[unitIndex];
    }
}
