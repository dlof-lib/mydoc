package com.dlofpkg.massage.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.dlofpkg.massage.R;
import com.dlofpkg.massage.model.User;
import com.dlofpkg.massage.util.SessionManager;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ProfileActivity extends AppCompatActivity {

    // أقصى أبعاد للصورة قبل الترميز، لضمان بقاء حجمها صغيراً داخل Firestore
    private static final int MAX_ICON_DIMENSION = 256;

    private ImageView ivIcon;
    private TextView tvUsername, tvPlan;
    private FirebaseFirestore db;
    private String uid;

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onImagePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        db = FirebaseFirestore.getInstance();
        uid = SessionManager.getUid();

        ivIcon = findViewById(R.id.ivIcon);
        tvUsername = findViewById(R.id.tvUsername);
        tvPlan = findViewById(R.id.tvPlan);
        Button btnChangeIcon = findViewById(R.id.btnChangeIcon);
        Button btnLogout = findViewById(R.id.btnLogout);

        btnChangeIcon.setOnClickListener(v -> imagePicker.launch("image/*"));
        btnLogout.setOnClickListener(v -> {
            SessionManager.logout();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        loadProfile();
    }

    private void loadProfile() {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    User user = snapshot.toObject(User.class);
                    if (user == null) return;

                    tvUsername.setText(user.getDisplayName() + " (@" + user.getUsername() + ")");
                    tvPlan.setText(user.isPaid() ? "الخطة: مدفوعة ✅" : "الخطة: مجانية");

                    if (user.getIconBase64() != null && !user.getIconBase64().isEmpty()) {
                        byte[] bytes = Base64.decode(user.getIconBase64(), Base64.NO_WRAP);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        ivIcon.setImageBitmap(bitmap);
                    }
                });
    }

    private void onImagePicked(Uri uri) {
        if (uri == null) return;
        try {
            Bitmap original = decodeSampledBitmap(uri);
            ivIcon.setImageBitmap(original);

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            // ضغط JPEG بجودة 80% لإبقاء حجم الـ Base64 صغيراً قدر الإمكان
            original.compress(Bitmap.CompressFormat.JPEG, 80, stream);
            String base64Icon = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP);

            db.collection("users").document(uid)
                    .update("iconBase64", base64Icon)
                    .addOnSuccessListener(unused ->
                            Toast.makeText(this, "تم تحديث الصورة", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "فشل رفع الصورة: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        } catch (IOException e) {
            Toast.makeText(this, "تعذّر قراءة الصورة", Toast.LENGTH_SHORT).show();
        }
    }

    /** يقلّص أبعاد الصورة إلى حد أقصى قبل الترميز حتى لا نتجاوز حدود حجم مستند Firestore. */
    private Bitmap decodeSampledBitmap(Uri uri) throws IOException {
        InputStream input = getContentResolver().openInputStream(uri);
        Bitmap full = BitmapFactory.decodeStream(input);
        if (input != null) input.close();

        int width = full.getWidth();
        int height = full.getHeight();
        float scale = Math.min(
                (float) MAX_ICON_DIMENSION / width,
                (float) MAX_ICON_DIMENSION / height);

        if (scale >= 1f) return full;
        return Bitmap.createScaledBitmap(full, Math.round(width * scale), Math.round(height * scale), true);
    }
}
