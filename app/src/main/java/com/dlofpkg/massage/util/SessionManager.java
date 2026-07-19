package com.dlofpkg.massage.util;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * غلاف بسيط حول Firebase Authentication لمعرفة حالة تسجيل الدخول الحالية.
 * لم نعد نخزّن أي شيء يدوياً في SharedPreferences؛ Firebase Auth SDK
 * يحتفظ بجلسة المستخدم تلقائياً وبأمان بين مرات فتح التطبيق.
 */
public class SessionManager {

    public static boolean isLoggedIn() {
        return FirebaseAuth.getInstance().getCurrentUser() != null;
    }

    /** يرجع uid المستخدم الحالي، أو null إن لم يكن مسجّل الدخول. */
    public static String getUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    /** يرجع اسم المستخدم (المخزَّن كـ displayName في Firebase Auth عند التسجيل). */
    public static String getUsername() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getDisplayName() : null;
    }

    public static void logout() {
        FirebaseAuth.getInstance().signOut();
    }
}
