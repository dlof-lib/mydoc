package com.dlofpkg.massage.util;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * "مفتاح المرور الأحمر" — وسيلة دخول سريعة بديلة عن كتابة البريد وكلمة المرور
 * في كل مرة. الفكرة:
 *
 * 1) نولّد مفتاحاً عشوائياً قوياً (16 حرف/رقم) يُعرض للمستخدم مرة واحدة فقط.
 * 2) نشفّر (البريد + كلمة المرور) بخوارزمية AES باستخدام هذا المفتاح نفسه
 *    كمفتاح تشفير، ونخزّن الناتج المشفّر في Firestore تحت معرّف = بصمة
 *    SHA-256 للمفتاح (وليس المفتاح نفسه، حتى لو تسرّبت قاعدة البيانات).
 * 3) عند "الدخول بالمفتاح"، نحسب بصمة المفتاح المُدخَل لنجد السجل، ثم نفك
 *    التشفير بنفس المفتاح للحصول على البريد وكلمة المرور، ثم نسجّل الدخول
 *    عبر Firebase Authentication العادي بهما.
 *
 * ⚠️ من يملك هذا المفتاح "الأحمر" فعلياً يقدر يدخل للحساب مباشرة بدون كلمة
 * مرور إضافية — لذلك يجب معاملته مثل كلمة مرور حساسة جداً ولا يُشارك أبداً.
 */
public class RedKeyUtils {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // بدون أحرف/أرقام متشابهة (O,0,I,1)
    private static final SecureRandom RANDOM = new SecureRandom();

    /** يولّد مفتاحاً بصيغة سهلة القراءة مثل: X7K9-QP2M-4RT8-WJ3N */
    public static String generateKey() {
        StringBuilder sb = new StringBuilder();
        for (int group = 0; group < 4; group++) {
            if (group > 0) sb.append('-');
            for (int i = 0; i < 4; i++) {
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
        }
        return sb.toString();
    }

    /** بصمة المفتاح، تُستخدم كمعرّف مستند في Firestore بدلاً من المفتاح نفسه. */
    public static String hashKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalize(key).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** يشفّر "email|password" بالمفتاح. الناتج: base64(iv):base64(cipherText) */
    public static String encrypt(String key, String email, String password) {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(normalize(key).getBytes(StandardCharsets.UTF_8));
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));

            String plain = email + "|" + password;
            byte[] cipherBytes = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                    Base64.encodeToString(cipherBytes, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException("فشل تشفير بيانات المفتاح", e);
        }
    }

    /** يرجع {email, password} أو يرمي استثناء إذا كان المفتاح خاطئاً / البيانات تالفة. */
    public static String[] decrypt(String key, String payload) throws Exception {
        String[] parts = payload.split(":");
        if (parts.length != 2) throw new IllegalArgumentException("بيانات غير صالحة");

        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP);

        byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                .digest(normalize(key).getBytes(StandardCharsets.UTF_8));
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));

        String plain = new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        return plain.split("\\|", 2);
    }

    private static String normalize(String key) {
        return key.trim().toUpperCase().replace(" ", "");
    }
}
