package com.dlofpkg.massage.util;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * تحويل أي ملف (صورة بروفايل أو ملف كود) يختاره المستخدم إلى نص Base64
 * لإرساله مباشرة كحقل نصي داخل مستند Firestore، بدون استخدام Firebase Storage.
 *
 * تنبيه مهم: الحد الأقصى لحجم مستند Firestore هو 1 ميجابايت تقريباً،
 * لذا يجب إبقاء الصور والملفات صغيرة (يُفضّل ضغط الصورة قبل الإرسال).
 */
public class Base64Utils {

    // حد أقصى احترازي: 700 كيلوبايت قبل الترميز، لضمان البقاء تحت حد Firestore
    public static final long MAX_FILE_SIZE_BYTES = 700 * 1024;

    public static String uriToBase64(ContentResolver resolver, Uri uri) throws IOException {
        InputStream inputStream = resolver.openInputStream(uri);
        if (inputStream == null) {
            throw new IOException("تعذّر فتح الملف المحدد");
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int bytesRead;
        long total = 0;
        while ((bytesRead = inputStream.read(chunk)) != -1) {
            total += bytesRead;
            if (total > MAX_FILE_SIZE_BYTES) {
                inputStream.close();
                throw new IOException("الملف كبير جداً. الحد الأقصى تقريباً 700 كيلوبايت");
            }
            buffer.write(chunk, 0, bytesRead);
        }
        inputStream.close();
        return Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP);
    }
}
