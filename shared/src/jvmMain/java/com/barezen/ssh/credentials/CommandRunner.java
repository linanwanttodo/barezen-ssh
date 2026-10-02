// shared/src/jvmMain/java/com/barezen/ssh/credentials/CommandRunner.java
package com.barezen.ssh.credentials;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 外部命令执行抽象：凭据存储实现用它包一层，测试注入假实现以穷举命令拼装与结果解析。
 * 生产实现是真实子进程调用。
 */
public interface CommandRunner {

    Result run(List<String> command, byte[] stdin);

    final class Result {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public Result(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    static CommandRunner system() {
        return (command, stdin) -> {
            Process process;
            try {
                process = new ProcessBuilder(command).start();
            } catch (IOException e) {
                return new Result(-1, "", e.getMessage() == null ? e.toString() : e.getMessage());
            }
            Future<String> stderrFuture;
            try (OutputStream out = process.getOutputStream()) {
                if (stdin != null) {
                    out.write(stdin);
                }
                out.flush();
            } catch (IOException ignored) {
                // 子进程可能提前退出关闭了 stdin；后续按退出码收口
            }
            ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "barezen-cmd-stderr");
                t.setDaemon(true);
                return t;
            });
            try {
                stderrFuture = pool.submit(() -> readAll(process.getErrorStream()));
                String stdout = readAll(process.getInputStream());
                String stderr;
                try {
                    stderr = stderrFuture.get();
                } catch (Exception e) {
                    stderr = e.getMessage() == null ? e.toString() : e.getMessage();
                }
                int code;
                try {
                    code = process.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                    return new Result(-1, stdout, "interrupted");
                }
                return new Result(code, stdout, stderr);
            } finally {
                pool.shutdown();
            }
        };
    }

    private static String readAll(InputStream in) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int n;
            while ((n = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, n);
            }
        } catch (IOException e) {
            sb.append(e.getMessage() == null ? e.toString() : e.getMessage());
        }
        return sb.toString();
    }
}
