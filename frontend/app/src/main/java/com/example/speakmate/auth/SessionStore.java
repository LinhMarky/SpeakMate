package com.example.speakmate.auth;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Mã hóa phiên bằng khóa Android Keystore và loại file phiên khỏi bản sao lưu. */
public final class SessionStore {

    private static final String KEY_ALIAS = "speakmate.auth.session.v1";

    private final AtomicFile file;

    public SessionStore(Context context) {
        file = new AtomicFile(new File(context.getNoBackupFilesDir(), "auth-session.bin"));
    }

    public synchronized void save(AuthSession session) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(session.toJson().toString().getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        byte[] content = ByteBuffer.allocate(1 + iv.length + encrypted.length).put((byte) iv.length).put(iv)
                .put(encrypted).array();
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(content);
            file.finishWrite(output);
        } catch (Exception error) {
            if (output != null) {
                file.failWrite(output);
            }
            throw error;
        }
    }

    public synchronized AuthSession load() {
        if (!file.getBaseFile().exists()) {
            return null;
        }
        try {
            ByteBuffer content = ByteBuffer.wrap(file.readFully());
            int ivLength = Byte.toUnsignedInt(content.get());
            if (ivLength != 12 || content.remaining() <= ivLength + 16) {
                throw new IllegalStateException();
            }
            byte[] iv = new byte[ivLength];
            content.get(iv);
            byte[] encrypted = new byte[content.remaining()];
            content.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return AuthSession.fromJson(new JSONObject(new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)));
        } catch (Exception invalidSession) {
            clear();
            return null;
        }
    }

    public synchronized void clear() {
        file.delete();
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            return (SecretKey) store.getKey(KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
        return generator.generateKey();
    }
}
