package com.reyzerbit.suiteqlcli;

import com.github.javakeyring.Keyring;
import com.github.javakeyring.PasswordAccessException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM encrypted file. The 256-bit key is randomly generated on first use and kept in the OS credential store
 * (Windows Credential Manager / macOS Keychain); it never touches disk. File layout: 12-byte IV followed by ciphertext+tag.
 */
public class SecureStore
{
    private static final String SERVICE = "nsqli";
    private static final String ACCOUNT = "master-key";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final Path file;

    public SecureStore(Path file)
    {
        this.file = file;
    }

    public boolean exists()
    {
        return Files.exists(file);
    }

    public byte[] read() throws Exception
    {
        byte[] blob = Files.readAllBytes(file);
        byte[] key = loadKey(false);

        if (key == null)
        {
            throw new IllegalStateException("The encryption key for " + file + " was not found in the OS credential store. The profile file cannot be decrypted.");
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES));
        return cipher.doFinal(blob, IV_BYTES, blob.length - IV_BYTES);
    }

    public void write(byte[] plain) throws Exception
    {
        byte[] key = loadKey(true);
        byte[] iv = new byte[IV_BYTES];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(plain);

        byte[] blob = Arrays.copyOf(iv, IV_BYTES + encrypted.length);
        System.arraycopy(encrypted, 0, blob, IV_BYTES, encrypted.length);

        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, blob);
        restrictPermissions(tmp);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private byte[] loadKey(boolean create) throws Exception
    {
        try (Keyring keyring = Keyring.create())
        {
            try
            {
                return Base64.getDecoder().decode(keyring.getPassword(SERVICE, ACCOUNT));
            }
            catch (PasswordAccessException notFound)
            {
                if (!create) return null;

                // Never mint a new key over an existing file: that would orphan the stored profiles.
                if (exists()) throw new IllegalStateException("The encryption key is missing from the OS credential store but " + file + " exists.");

                byte[] key = new byte[32];
                new SecureRandom().nextBytes(key);
                keyring.setPassword(SERVICE, ACCOUNT, Base64.getEncoder().encodeToString(key));
                return key;
            }
        }
    }

    private void restrictPermissions(Path path) throws IOException
    {
        try
        {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        }
        catch (UnsupportedOperationException ignored) {} // Windows: relies on the user-profile ACL
    }
}
