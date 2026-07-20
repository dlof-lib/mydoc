package com.dlofpkg.massage.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dlofpkg.massage.R;
import com.dlofpkg.massage.model.User;
import com.dlofpkg.massage.util.RedKeyUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText etDisplayName, etUsername, etEmail, etPassword, etConfirmPassword;
    private ProgressBar progressBar;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etDisplayName = findViewById(R.id.etDisplayName);
        etUsername = findViewById(R.id.etUsername);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        progressBar = findViewById(R.id.progressBar);
        Button btnRegister = findViewById(R.id.btnRegister);

        btnRegister.setOnClickListener(v -> attemptRegister());
    }

    private void attemptRegister() {
        String displayName = etDisplayName.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirm = etConfirmPassword.getText().toString();

        if (TextUtils.isEmpty(displayName) || TextUtils.isEmpty(username)
                || TextUtils.isEmpty(email) || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirm)) {
            Toast.makeText(this, "الرجاء تعبئة جميع الحقول", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!username.matches("^[a-zA-Z0-9_.]{3,20}$")) {
            Toast.makeText(this, "اسم المستخدم يجب أن يكون بالإنجليزية بدون مسافات (3-20 حرف)", Toast.LENGTH_LONG).show();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "الرجاء إدخال بريد إلكتروني صحيح", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "كلمة المرور يجب ألا تقل عن 6 أحرف", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.equals(confirm)) {
            Toast.makeText(this, "كلمتا المرور غير متطابقتين", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        // فحص مبدئي وسريع (قبل حتى إنشاء الحساب) لتوفير تجربة مستخدم أفضل:
        // إن كان الاسم محجوزاً بوضوح، لا داعي لإنشاء حساب Auth ثم حذفه.
        // الضمان الحقيقي وغير القابل للتحايل يبقى قاعدة الأمان في Firestore
        // (مجموعة usernames)، التي تُطبَّق بعد هذا الفحص كخط دفاع نهائي.
        String usernameKey = username.toLowerCase();
        db.collection("usernames").document(usernameKey).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        setLoading(false);
                        Toast.makeText(this, "الاسم @" + username + " مستخدم بالفعل من قبل حساب آخر", Toast.LENGTH_LONG).show();
                        return;
                    }
                    createAccount(displayName, username, usernameKey, email, password);
                })
                .addOnFailureListener(e -> createAccount(displayName, username, usernameKey, email, password));
    }

    private void createAccount(String displayName, String username, String usernameKey, String email, String password) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser firebaseUser = result.getUser();
                    if (firebaseUser == null) {
                        setLoading(false);
                        return;
                    }
                    String uid = firebaseUser.getUid();

                    // نحجز اسم المستخدم أولاً، قبل أي شيء آخر. قاعدة الأمان في
                    // Firestore تسمح بإنشاء هذا المستند فقط إن لم يكن موجوداً
                    // مسبقاً لاسم آخر (create يُرفض تلقائياً إن كان المستند
                    // موجوداً أصلاً، ويتحوّل لعملية update المرفوضة بلا شروط)،
                    // لذا هذا الحجز آمن من التكرار حتى لو حاول شخصان التسجيل
                    // بنفس الاسم في اللحظة ذاتها.
                    Map<String, Object> reservation = new HashMap<>();
                    reservation.put("uid", uid);
                    db.collection("usernames").document(usernameKey).set(reservation)
                            .addOnSuccessListener(unused ->
                                    finishRegistration(firebaseUser, uid, username, displayName, email, password))
                            .addOnFailureListener(e -> {
                                // الاسم أُخذ للتو من شخص آخر بين لحظة الفحص وهذه اللحظة:
                                // نتراجع بحذف حساب Auth الذي أنشأناه للتو حتى لا يبقى حساباً
                                // "شبحياً" بدون بيانات مستخدم مرتبطة به.
                                setLoading(false);
                                firebaseUser.delete();
                                Toast.makeText(this, "الاسم @" + username + " أصبح مستخدماً للتو من حساب آخر، الرجاء اختيار اسم مختلف", Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "فشل إنشاء الحساب: " + friendlyError(e.getMessage()), Toast.LENGTH_LONG).show();
                });
    }

    private void finishRegistration(FirebaseUser firebaseUser, String uid, String username, String displayName, String email, String password) {
        // نخزّن الاسم فوراً في الذاكرة المحلية حتى لو تأخر تحديث displayName
        // داخل SDK؛ هذا يمنع ظهور مؤلف فارغ عند نشر أول منشور مباشرة بعد التسجيل.
        com.dlofpkg.massage.util.SessionManager.cacheUsername(uid, username);

        UserProfileChangeRequest profileUpdate = new UserProfileChangeRequest.Builder()
                .setDisplayName(username)
                .build();

        User newUser = new User(uid, username, displayName);

        // ننتظر اكتمال تحديث الاسم في Firebase Auth قبل إنشاء مستند Firestore،
        // حتى لا يحدث سباق تزامن بين الاثنين.
        firebaseUser.updateProfile(profileUpdate)
                .addOnCompleteListener(profileTask ->
                        db.collection("users").document(uid).set(newUser)
                                .addOnSuccessListener(unused -> createRedKeyThenContinue(email, password))
                                .addOnFailureListener(e -> {
                                    setLoading(false);
                                    Toast.makeText(this, "تم إنشاء الحساب لكن فشل حفظ البيانات: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                }));
    }

    /** ينشئ مفتاح المرور الأحمر تلقائياً بعد التسجيل، ثم يعرضه للمستخدم مرة واحدة. */
    private void createRedKeyThenContinue(String email, String password) {
        String redKey = RedKeyUtils.generateKey();
        String hash = RedKeyUtils.hashKey(redKey);
        String payload = RedKeyUtils.encrypt(redKey, email, password);

        db.collection("passkeys").document(hash)
                .set(java.util.Collections.singletonMap("payload", payload))
                .addOnSuccessListener(unused -> db.collection("users").document(auth.getCurrentUser().getUid())
                        .update("activeKeyHash", hash)
                        .addOnSuccessListener(u2 -> {
                            setLoading(false);
                            Intent intent = new Intent(this, RedKeyDisplayActivity.class);
                            intent.putExtra("red_key", redKey);
                            intent.putExtra("is_new", true);
                            startActivity(intent);
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            setLoading(false);
                            Intent intent = new Intent(this, RedKeyDisplayActivity.class);
                            intent.putExtra("red_key", redKey);
                            startActivity(intent);
                            finish();
                        }))
                .addOnFailureListener(e -> {
                    // فشل توليد المفتاح الأحمر لا يجب أن يمنع إتمام التسجيل نفسه؛
                    // يبقى بإمكان المستخدم الدخول بالبريد وكلمة المرور، ويقدر يولّد
                    // مفتاحاً لاحقاً من صفحة الملف الشخصي.
                    setLoading(false);
                    Toast.makeText(this, "تم إنشاء الحساب، لكن تعذّر توليد المفتاح الأحمر الآن. يمكنك توليده لاحقاً من الملف الشخصي.", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                });
    }

    private String friendlyError(String message) {
        if (message == null) return "خطأ غير معروف";
        if (message.contains("already in use")) return "هذا البريد الإلكتروني مستخدم مسبقاً";
        if (message.contains("badly formatted")) return "صيغة البريد الإلكتروني غير صحيحة";
        return message;
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
