// shared/src/jvmMain/java/com/barezen/ssh/ssh/sftp/TransferThrottler.java
package com.barezen.ssh.ssh.sftp;

/**
 * 传输进度节流纯逻辑：距上次发出达到 intervalMs，或累计字节达到 byteInterval 时才允许发出。
 * 首次调用恒发出（保证任务有初始进度）。线程安全。
 */
public final class TransferThrottler {

    private final long intervalMs;
    private final long byteInterval;
    private long lastEmitMs = Long.MIN_VALUE;
    private long accumulatedBytes = 0;

    public TransferThrottler(long intervalMs, long byteInterval) {
        this.intervalMs = intervalMs;
        this.byteInterval = byteInterval;
    }

    /**
     * 累计 [deltaBytes] 并判断是否应发出进度。发出即重置计时与字节累计。
     */
    public synchronized boolean shouldEmit(long nowMs, long deltaBytes) {
        accumulatedBytes += deltaBytes;
        boolean first = lastEmitMs == Long.MIN_VALUE;
        if (first || nowMs - lastEmitMs >= intervalMs || accumulatedBytes >= byteInterval) {
            lastEmitMs = nowMs;
            accumulatedBytes = 0;
            return true;
        }
        return false;
    }
}
