// shared/src/jvmMain/java/com/barezen/ssh/credentials/WindowsCredStore.java
package com.barezen.ssh.credentials;

/**
 * Windows 凭据管理器：advapi32 的 CredWriteW/CredReadW/CredDeleteW（CRED_TYPE_GENERIC）。
 *
 * 默认构造已接线 {@link JnaAdvapiBridge}（JNA，批次 B1）：Windows 上 isAvailable()
 * 返回真实状态，非 Windows 如实报告不可用并降级。参数拼装与结果映射已穷举测试；
 * 真调用只在 Windows 发生。
 */
public final class WindowsCredStore implements CredentialStore {

    /** 与 wincred.h 一致的常量。 */
    public static final int CRED_TYPE_GENERIC = 1;
    public static final int CRED_PERSIST_LOCAL_MACHINE = 2;

    /** CredWriteW 参数拼装结果；secret 由调用方持有，实现层不得长期保留引用。 */
    public static final class WriteRequest {
        public final String targetName;
        public final char[] secret;
        public final int persist;

        public WriteRequest(String targetName, char[] secret, int persist) {
            this.targetName = targetName;
            this.secret = secret;
            this.persist = persist;
        }
    }

    /** advapi32 最小桥接；实现类负责把参数落到 CREDENTIALW 结构。 */
    public interface Bridge {
        boolean isNativeAvailable();

        /** @return 写入成功返回 true；被拒绝或失败返回 false。 */
        boolean write(WriteRequest request);

        /** @return 命中返回秘密；未命中返回 null。 */
        char[] read(String targetName);

        /** @return 删除成功或条目不存在返回 true；被拒绝返回 false。 */
        boolean delete(String targetName);
    }

    /** 默认 bridge：native 未接入，所有操作如实报告不可用。 */
    public static final Bridge UNAVAILABLE = new Bridge() {
        @Override
        public boolean isNativeAvailable() {
            return false;
        }

        @Override
        public boolean write(WriteRequest request) {
            return false;
        }

        @Override
        public char[] read(String targetName) {
            return null;
        }

        @Override
        public boolean delete(String targetName) {
            return false;
        }
    };

    private final Bridge bridge;

    public WindowsCredStore() {
        this(JnaAdvapiBridge.create());
    }

    public WindowsCredStore(Bridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void save(String id, char[] secret) {
        requireAvailable();
        boolean ok = bridge.write(new WriteRequest(id, secret.clone(), CRED_PERSIST_LOCAL_MACHINE));
        if (!ok) {
            throw new CredentialStoreException("Windows 凭据管理器写入失败：" + id);
        }
    }

    @Override
    public char[] load(String id) {
        if (!bridge.isNativeAvailable()) {
            return null;
        }
        char[] value = bridge.read(id);
        if (value == null || value.length == 0) {
            return null;
        }
        return value;
    }

    @Override
    public void delete(String id) {
        if (!bridge.isNativeAvailable()) {
            return;
        }
        bridge.delete(id);
    }

    @Override
    public boolean isAvailable() {
        return bridge.isNativeAvailable();
    }

    private void requireAvailable() {
        if (!bridge.isNativeAvailable()) {
            throw new CredentialStoreException("Windows 凭据管理器不可用（native bridge 未接入）");
        }
    }
}
