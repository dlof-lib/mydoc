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
import com.dlofpkg.massage.util.RedKeyUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class RedKeyLoginActivity extends AppCompatActivity {

    private EditText etRedKey;
    private ProgressBar progressBar;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_red_key_login);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etRedKey = findViewById(R.id.etRedKey);
        progressBar = findViewById(R.id.progressBar);
        Button btnQuickLogin = findViewById(R.id.btnQuickLogin);

        btnQuickLogin.setOnClickListener(v -> attemptQuickLogin());
    }

    private void attemptQuickLogin() {
        String redKey = etRedKey.getText().toString().trim();
        if (TextUtils.isEmpty(redKey)) {
            Toast.makeText(this, "الرجاء إدخال المفتاح", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        String hash = RedKeyUtils.hashKey(redKey);

        db.collection("passkeys").document(hash).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        setLoading(false);
                        Toast.makeText(this, "المفتاح غير صحيح", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        String payload = snapshot.getString("payload");
                        String[] emailPassword = RedKeyUtils.decrypt(redKey, payload);
                        signIn(emailPassword[0], emailPassword[1]);
                    } catch (Exception e) {
                        setLoading(false);
                        Toast.makeText(this, "المفتاح غير صحيح", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "فشل الاتصال: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void signIn(String email, String password) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    setLoading(false);
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "تعذّر تسجيل الدخول بهذا المفتاح", Toast.LENGTH_SHORT).show();
                });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
