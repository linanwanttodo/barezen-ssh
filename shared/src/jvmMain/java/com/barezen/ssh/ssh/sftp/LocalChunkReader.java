// shared/src/jvmMain/java/com/barezen/ssh/ssh/sftp/LocalChunkReader.java
package com.barezen.ssh.ssh.sftp;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * 本地文件分块读取：为 SftpFs.upload 的 nextChunk(offset) 按需提供 64KiB 块，
 * 超出文件末尾返回 null。非线程安全，单任务独享。
 */
public final class LocalChunkReader implements AutoCloseable {

    public static final int CHUNK_SIZE = 64 * 1024;

    private final RandomAccessFile file;
    private final long size;

    public LocalChunkReader(String path) throws IOException {
        this.file = new RandomAccessFile(path, "r");
        this.size = file.length();
    }

    /** 文件总字节数。 */
    public long size() {
        return size;
    }

    /** 返回 offset 起的下一块（末块不足 64KiB 时为余量）；offset 越界返回 null。 */
    public byte[] read(long offset) throws IOException {
        if (offset < 0 || offset >= size) {
            return null;
        }
        int len = (int) Math.min(CHUNK_SIZE, size - offset);
        byte[] buf = new byte[len];
        file.seek(offset);
        file.readFully(buf);
        return buf;
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}
