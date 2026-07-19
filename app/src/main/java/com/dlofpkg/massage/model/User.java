package com.dlofpkg.massage.model;

/**
 * يمثل مستخدم منصة MyDoc.
 * يُخزَّن كل مستخدم في Firestore داخل مجموعة "users" باستخدام
 * الـ UID القادم من Firebase Authentication كمعرّف للمستند (Document ID).
 * لم نعد نخزّن كلمة المرور هنا إطلاقاً — Firebase Authentication يتولى
 * ذلك بشكل آمن (تشفير + salt) على خوادم Google.
 */
public class User {

    private String uid;
    private String username;     // اسم مستخدم فريد يظهر للآخرين، يُستخدم أيضاً لبناء بريد داخلي للمصادقة
    private String displayName;
    private String iconBase64;   // صورة البروفايل مرسلة كـ Base64 مباشرة إلى Firestore
    private boolean paid;        // هل الحساب على خطة مدفوعة داخل التطبيق
    private long createdAt;

    public User() {
        // مطلوب من Firestore لإعادة بناء الكائن تلقائياً
    }

    public User(String uid, String username, String displayName) {
        this.uid = uid;
        this.username = username;
        this.displayName = displayName;
        this.iconBase64 = "";
        this.paid = false;
        this.createdAt = System.currentTimeMillis();
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getIconBase64() { return iconBase64; }
    public void setIconBase64(String iconBase64) { this.iconBase64 = iconBase64; }

    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
