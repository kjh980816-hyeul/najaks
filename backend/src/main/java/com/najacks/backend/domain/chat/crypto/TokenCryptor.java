package com.najacks.backend.domain.chat.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 대칭 암호화. payload 포맷: [12B IV][ciphertext+tag] Base64.
 * 키는 Base64 32B (AES-256).
 */
@Component
@Slf4j
public class TokenCryptor {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();

    @Value("${app.crypto.master-key:}")
    private String masterKeyBase64;

    private SecretKeySpec keySpec;

    private SecretKeySpec keySpec() {
        if (keySpec != null) return keySpec;
        if (masterKeyBase64 == null || masterKeyBase64.isBlank()) {
            throw new IllegalStateException("app.crypto.master-key 미설정");
        }
        byte[] key = Base64.getDecoder().decode(masterKeyBase64);
        if (key.length != 32) {
            throw new IllegalStateException("마스터 키는 Base64 32B (AES-256) 이어야 함. 현재 " + key.length + "B");
        }
        keySpec = new SecretKeySpec(key, "AES");
        return keySpec;
    }

    public boolean isConfigured() {
        return masterKeyBase64 != null && !masterKeyBase64.isBlank();
    }

    public String encrypt(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ct.length);
            buf.put(iv).put(ct);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new RuntimeException("토큰 암호화 실패", e);
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) return null;
        try {
            byte[] all = Base64.getDecoder().decode(encrypted);
            byte[] iv = new byte[IV_LEN];
            byte[] ct = new byte[all.length - IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            System.arraycopy(all, IV_LEN, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, keySpec(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("토큰 복호화 실패", e);
        }
    }
}
