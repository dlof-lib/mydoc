package com.dlofpkg.massage.util;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * غلاف بسيط حول Firebase Authentication لمعرفة حالة تسجيل الدخول الحالية.
 * لم نعد نخزّن أي شيء يدوياً في SharedPreferences؛ Firebase Auth SDK
 * يحتفظ بجلسة المستخدم تلقائياً وبأمان بين مرات فتح التطبيق.
 *
 * ملاحظة مهمة (إصلاح): FirebaseUser.getDisplayName() يعتمد على نسخة محلية
 * مخزَّنة مؤقتاً داخل SDK، وقد تكون فارغة (null) لفترة قصيرة جداً بعد
 * التسجيل مباشرة قبل اكتمال updateProfile()، أو بعد إعادة تسجيل الدخول
 * من جهاز آخر. لتفادي ظهور "null" كاسم مؤلف عند النشر، نحتفظ بنسخة
 * احتياطية (cachedUsername) تُملأ فور توفرها من Firestore أو من نتيجة
 * التسجيل/الدخول، ونستخدمها كخيار بديل عندما يكون displayName فارغاً.
 */
public class SessionManager {

    private static volatile String cachedUsername;
    private static volatile String cachedUid;

    public static boolean isLoggedIn() {
        return FirebaseAuth.getInstance().getCurrentUser() != null;
    }

    /** يرجع uid المستخدم الحالي، أو null إن لم يكن مسجّل الدخول. */
    public static String getUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    /**
     * يرجع اسم المستخدم (username المخزَّن كـ displayName في Firebase Auth).
     * إن كان فارغاً مؤقتاً (سباق تزامن نادر بعد التسجيل)، يعود للنسخة
     * المخزَّنة محلياً (cachedUsername) إن كانت لنفس uid الحالي.
     */
    public static String getUsername() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return null;
        String name = user.getDisplayName();
        if (name != null && !name.isEmpty()) {
            cacheUsername(user.getUid(), name);
            return name;
        }
        if (user.getUid().equals(cachedUid) && cachedUsername != null) {
            return cachedUsername;
        }
        return null;
    }

    /** يُستدعى بعد التسجيل/الدخول لضمان توفر الاسم فوراً حتى لو تأخر تحديث displayName محلياً. */
    public static void cacheUsername(String uid, String username) {
        cachedUid = uid;
        cachedUsername = username;
    }

    /** يجلب username الحقيقي من Firestore ويخزّنه محلياً؛ يُستخدم كطبقة أمان إضافية عند فتح التطبيق. */
    public static void refreshUsernameFromFirestore(Runnable onDone) {
        String uid = getUid();
        if (uid == null) {
            if (onDone != null) onDone.run();
            return;
        }
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    String username = snapshot.getString("username");
                    if (username != null && !username.isEmpty()) {
                        cacheUsername(uid, username);
                    }
                    if (onDone != null) onDone.run();
                })
                .addOnFailureListener(e -> {
                    if (onDone != null) onDone.run();
                });
    }

    public static void logout() {
        cachedUsername = null;
        cachedUid = null;
        FirebaseAuth.getInstance().signOut();
    }
}
