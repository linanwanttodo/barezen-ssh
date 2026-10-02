// shared/src/jvmMain/java/com/barezen/ssh/credentials/JnaAdvapiBridge.java
package com.barezen.ssh.credentials;

import com.barezen.ssh.credentials.WindowsCredStore.Bridge;
import com.barezen.ssh.credentials.WindowsCredStore.WriteRequest;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.PointerByReference;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * advapi32 的 JNA 真实桥（批次 B1）：CredWriteW / CredReadW / CredDeleteW + CredFree，
 * CRED_TYPE_GENERIC。非 Windows 上 isNativeAvailable() 如实返回 false，不假装可用——
 * 结构与参数拼装逻辑是纯 Java，Linux 测试可全量覆盖（真调用只在 Windows 发生）。
 */
public final class JnaAdvapiBridge implements Bridge {

    /** wincred.h 的 CREDENTIALW。字段顺序必须与结构声明一致。 */
    public static class CREDENTIALW extends Structure {
        @SuppressWarnings("unused")
        public CREDENTIALW() {
            super();
        }

        public CREDENTIALW(Pointer p) {
            super(p);
        }

        public int Flags;
        public int Type;
        public String TargetName;
        public String Comment;
        public FILETIME LastWritten;
        public int CredentialBlobSize;
        public Pointer CredentialBlob;
        public int Persist;
        public int AttributeCount;
        public Pointer Attributes;
        public String TargetAlias;
        public String UserName;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("Flags", "Type", "TargetName", "Comment", "LastWritten",
                    "CredentialBlobSize", "CredentialBlob", "Persist", "AttributeCount",
                    "Attributes", "TargetAlias", "UserName");
        }
    }

    /** wincred.h 的 FILETIME（64 位 100ns 值的高低各 32 位）。 */
    public static class FILETIME extends Structure {
        public int dwLowDateTime;
        public int dwHighDateTime;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("dwLowDateTime", "dwHighDateTime");
        }
    }

    /** advapi32 最小接口；StdCall 约定。 */
    public interface Advapi32 extends com.sun.jna.win32.StdCallLibrary {
        boolean CredWriteW(CREDENTIALW credential, int flags);

        boolean CredReadW(String targetName, int type, int flags, PointerByReference credential);

        boolean CredDeleteW(String targetName, int type, int flags);

        void CredFree(Pointer buffer);
    }

    private final Advapi32 advapi32;
    private final boolean available;

    private JnaAdvapiBridge(Advapi32 advapi32, boolean available) {
        this.advapi32 = advapi32;
        this.available = available;
    }

    /** 非 Windows 返回不可用实例（零 native 加载）；Windows 加载 advapi32。 */
    public static JnaAdvapiBridge create() {
        if (!Platform.isWindows()) {
            return new JnaAdvapiBridge(null, false);
        }
        try {
            return new JnaAdvapiBridge(Native.load("advapi32", Advapi32.class), true);
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            return new JnaAdvapiBridge(null, false);
        }
    }

    @Override
    public boolean isNativeAvailable() {
        return available;
    }

    @Override
    public boolean write(WriteRequest request) {
        if (!available) {
            return false;
        }
        byte[] blob = encodeSecret(request.secret);
        CREDENTIALW cred = new CREDENTIALW();
        cred.Flags = 0;
        cred.Type = WindowsCredStore.CRED_TYPE_GENERIC;
        cred.TargetName = request.targetName;
        cred.Comment = "BareZen-SSH";
        cred.CredentialBlobSize = blob.length;
        cred.CredentialBlob = new Memory(Math.max(1, blob.length));
        cred.CredentialBlob.write(0, blob, 0, blob.length);
        cred.Persist = request.persist;
        cred.AttributeCount = 0;
        cred.Attributes = null;
        cred.TargetAlias = null;
        cred.UserName = null;
        try {
            return advapi32.CredWriteW(cred, 0);
        } finally {
            Arrays.fill(blob, (byte) 0);
        }
    }

    @Override
    public char[] read(String targetName) {
        if (!available) {
            return null;
        }
        PointerByReference ref = new PointerByReference();
        if (!advapi32.CredReadW(targetName, WindowsCredStore.CRED_TYPE_GENERIC, 0, ref)) {
            return null;
        }
        try {
            CREDENTIALW cred = new CREDENTIALW(ref.getValue());
            cred.read();
            if (cred.CredentialBlob == null || cred.CredentialBlobSize == 0) {
                return null;
            }
            byte[] blob = cred.CredentialBlob.getByteArray(0, cred.CredentialBlobSize);
            return decodeSecret(blob);
        } finally {
            advapi32.CredFree(ref.getValue());
        }
    }

    @Override
    public boolean delete(String targetName) {
        if (!available) {
            return false;
        }
        // 条目不存在时 CredDeleteW 返回 false（ERROR_NOT_FOUND）；此处只区分「系统拒绝」与「成功」，
        // 上层 delete(id) 对不可用直接短路，语义由 CredentialStore 契约兜底。
        return advapi32.CredDeleteW(targetName, WindowsCredStore.CRED_TYPE_GENERIC, 0);
    }

    /** secret 的 char[] 转 UTF-16LE 字节（CredentialBlob 按字节存储）。 */
    public static byte[] encodeSecret(char[] secret) {
        if (secret == null || secret.length == 0) {
            return new byte[0];
        }
        return new String(secret).getBytes(StandardCharsets.UTF_16LE);
    }

    /** UTF-16LE 字节转回 secret 的 char[]（读回路径）。 */
    public static char[] decodeSecret(byte[] blob) {
        if (blob == null || blob.length == 0) {
            return new char[0];
        }
        return new String(blob, StandardCharsets.UTF_16LE).toCharArray();
    }
}
