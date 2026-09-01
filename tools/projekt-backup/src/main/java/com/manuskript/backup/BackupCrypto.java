package com.manuskript.backup;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM um eine ZIP-Datei. Entschlüsseln nur über dieses Plugin.
 */
public final class BackupCrypto {

    static final byte[] MAGIC = "MSK1".getBytes(StandardCharsets.US_ASCII);
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int KEY_BITS = 256;
    private static final int ITERATIONS = 210_000;
    private static final int GCM_TAG_BITS = 128;
    private static final int BUFFER = 64 * 1024;

    private BackupCrypto() {
    }

    public static void encrypt(Path source, Path target, char[] password) throws Exception {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Passwort fehlt");
        }
        byte[] salt = random(SALT_LEN);
        byte[] iv = random(IV_LEN);
        SecretKey key = key(password, salt);
        try (InputStream in = Files.newInputStream(source);
             OutputStream out = Files.newOutputStream(target)) {
            out.write(MAGIC);
            out.write(salt);
            out.write(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            try (CipherOutputStream cipherOut = new CipherOutputStream(out, cipher)) {
                in.transferTo(cipherOut);
            }
        } finally {
            destroy(key);
        }
    }

    public static void decrypt(Path source, Path target, char[] password) throws Exception {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Passwort fehlt");
        }
        try (InputStream in = Files.newInputStream(source);
             OutputStream out = Files.newOutputStream(target)) {
            byte[] header = in.readNBytes(MAGIC.length + SALT_LEN + IV_LEN);
            if (header.length < MAGIC.length + SALT_LEN + IV_LEN) {
                throw new IllegalArgumentException("Datei ist kein verschlüsseltes Backup");
            }
            for (int i = 0; i < MAGIC.length; i++) {
                if (header[i] != MAGIC[i]) {
                    throw new IllegalArgumentException("Datei ist kein verschlüsseltes Backup");
                }
            }
            byte[] salt = Arrays.copyOfRange(header, MAGIC.length, MAGIC.length + SALT_LEN);
            byte[] iv = Arrays.copyOfRange(header, MAGIC.length + SALT_LEN, MAGIC.length + SALT_LEN + IV_LEN);
            SecretKey key = key(password, salt);
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
                try (CipherInputStream cipherIn = new CipherInputStream(in, cipher)) {
                    byte[] buffer = new byte[BUFFER];
                    int read;
                    while ((read = cipherIn.read(buffer)) >= 0) {
                        out.write(buffer, 0, read);
                    }
                }
            } catch (java.io.IOException e) {
                if (e.getCause() instanceof AEADBadTagException) {
                    throw new IllegalArgumentException("Passwort falsch oder Datei beschädigt", e);
                }
                throw e;
            } finally {
                destroy(key);
            }
        }
    }

    private static SecretKey key(char[] password, byte[] salt) throws GeneralSecurityException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
        byte[] encoded = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(encoded, "AES");
    }

    private static byte[] random(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private static void destroy(SecretKey key) {
        if (key == null) {
            return;
        }
        try {
            byte[] encoded = key.getEncoded();
            if (encoded != null) {
                Arrays.fill(encoded, (byte) 0);
            }
        } catch (Exception ignored) {
            // ignore
        }
    }
}
