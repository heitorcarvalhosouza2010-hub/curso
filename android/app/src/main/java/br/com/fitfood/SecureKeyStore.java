package br.com.fitfood;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecureKeyStore {
    private static final String ALIAS = "fitfood_ai_key";
    private static final String PREFERENCES = "fitfood_secure";
    private static final String VALUE = "encrypted_api_key";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final int IV_LENGTH = 12;

    private SecureKeyStore() {
    }

    static void save(Context context, String value) throws GeneralSecurityException {
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        ByteBuffer payload = ByteBuffer.allocate(iv.length + encrypted.length);
        payload.put(iv);
        payload.put(encrypted);
        preferences(context).edit()
                .putString(VALUE, Base64.encodeToString(payload.array(), Base64.NO_WRAP))
                .apply();
    }

    static String load(Context context) throws GeneralSecurityException {
        String encoded = preferences(context).getString(VALUE, null);
        if (encoded == null) {
            return null;
        }
        byte[] payload;
        try {
            payload = Base64.decode(encoded, Base64.NO_WRAP);
        } catch (IllegalArgumentException exception) {
            throw new GeneralSecurityException("A chave salva está inválida.", exception);
        }
        if (payload.length <= IV_LENGTH) {
            throw new GeneralSecurityException("A chave salva está inválida.");
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload);
        byte[] iv = new byte[IV_LENGTH];
        buffer.get(iv);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    static void delete(Context context) throws GeneralSecurityException {
        preferences(context).edit().remove(VALUE).apply();
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
            keyStore.load(null);
            if (keyStore.containsAlias(ALIAS)) {
                keyStore.deleteEntry(ALIAS);
            }
        } catch (java.io.IOException | java.security.cert.CertificateException exception) {
            throw new GeneralSecurityException("Não foi possível remover a chave segura.", exception);
        }
    }

    private static SecretKey getOrCreateKey() throws GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        try {
            keyStore.load(null);
            if (keyStore.containsAlias(ALIAS)) {
                return (SecretKey) keyStore.getKey(ALIAS, null);
            }
            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            generator.init(new KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            return generator.generateKey();
        } catch (java.io.IOException | java.security.cert.CertificateException
                 | java.security.UnrecoverableKeyException exception) {
            throw new GeneralSecurityException("Não foi possível acessar a chave segura.", exception);
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
