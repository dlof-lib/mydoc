package com.dlofpkg.massage.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dlofpkg.massage.R;
import com.dlofpkg.massage.model.User;
import com.dlofpkg.massage.util.AuthUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

public class RegisterActivity extends AppCompatActivity {

    private EditText etDisplayName, etUsername, etPassword, etConfirmPassword;
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
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        progressBar = findViewById(R.id.progressBar);
        Button btnRegister = findViewById(R.id.btnRegister);

        btnRegister.setOnClickListener(v -> attemptRegister());
    }

    private void attemptRegister() {
        String displayName = etDisplayName.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirm = etConfirmPassword.getText().toString();

        if (TextUtils.isEmpty(displayName) || TextUtils.isEmpty(username)
                || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirm)) {
            Toast.makeText(this, "الرجاء تعبئة جميع الحقول", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!username.matches("^[a-zA-Z0-9_.]{3,20}$")) {
            Toast.makeText(this, "اسم المستخدم يجب أن يكون بالإنجليزية بدون مسافات (3-20 حرف)", Toast.LENGTH_LONG).show();
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
        String email = AuthUtils.usernameToEmail(username);

        // Firebase Authentication يرفض تلقائياً إن كان البريد (وبالتالي اسم
        // المستخدم) مستخدماً من قبل، فهذا يكفي كتحقق من التفرّد.
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    if (result.getUser() == null) {
                        setLoading(false);
                        return;
                    }
                    String uid = result.getUser().getUid();

                    // نخزّن اسم المستخدم كـ displayName داخل Firebase Auth نفسه
                    // حتى نقدر نقرأه فوراً من الجلسة المحلية بدون طلب إضافي لـ Firestore
                    UserProfileChangeRequest profileUpdate = new UserProfileChangeRequest.Builder()
                            .setDisplayName(username)
                            .build();
                    result.getUser().updateProfile(profileUpdate);

                    User newUser = new User(uid, username, displayName);
                    db.collection("users").document(uid).set(newUser)
                            .addOnSuccessListener(unused -> {
                                setLoading(false);
                                startActivity(new Intent(this, MainActivity.class));
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                Toast.makeText(this, "تم إنشاء الحساب لكن فشل حفظ البيانات: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "فشل إنشاء الحساب: " + friendlyError(e.getMessage()), Toast.LENGTH_LONG).show();
                });
    }

    private String friendlyError(String message) {
        if (message == null) return "خطأ غير معروف";
        if (message.contains("already in use")) return "اسم المستخدم محجوز مسبقاً";
        if (message.contains("badly formatted")) return "اسم المستخدم غير صالح";
        return message;
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
