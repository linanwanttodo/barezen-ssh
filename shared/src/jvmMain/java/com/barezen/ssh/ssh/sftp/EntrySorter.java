// shared/src/jvmMain/java/com/barezen/ssh/ssh/sftp/EntrySorter.java
package com.barezen.ssh.ssh.sftp;

import com.barezen.ssh.ssh.SftpEntry;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 目录项排序纯函数：目录在前；同组内隐藏文件（. 开头）排最后；名称按不区分大小写字母序。
 * 返回新列表，不修改入参。
 */
public final class EntrySorter {

    private static final Comparator<SftpEntry> ORDER =
            Comparator.comparing((SftpEntry e) -> !e.isDirectory())
                    .thenComparing(EntrySorter::isHidden)
                    .thenComparing(SftpEntry::getName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(SftpEntry::getName);

    private EntrySorter() {
    }

    public static List<SftpEntry> sorted(List<SftpEntry> entries) {
        return entries.stream().sorted(ORDER).collect(Collectors.toList());
    }

    static boolean isHidden(SftpEntry entry) {
        return entry.getName().startsWith(".");
    }
}
