// shared/src/jvmMain/java/com/barezen/ssh/credentials/CredentialStoreException.java
package com.barezen.ssh.credentials;

/** 凭据存取失败（底层钥匙串拒绝、命令非零退出等）；message 面向用户原样展示。 */
public class CredentialStoreException extends RuntimeException {
    public CredentialStoreException(String message) {
        super(message);
    }

    public CredentialStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
