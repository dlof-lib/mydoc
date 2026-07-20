package com.dlofpkg.massage.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.dlofpkg.massage.R;
import com.dlofpkg.massage.model.User;
import com.dlofpkg.massage.util.RedKeyUtils;
import com.dlofpkg.massage.util.SessionManager;
import com.dlofpkg.massage.util.SocialActions;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ProfileActivity extends AppCompatActivity {

    // أقصى أبعاد للصورة قبل الترميز، لضمان بقاء حجمها صغيراً داخل Firestore
    private static final int MAX_ICON_DIMENSION = 256;

    private ImageView ivIcon;
    private TextView tvUsername, tvPlan, tvFollowersCount, tvFollowingCount;
    private Button btnChangeIcon, btnGenerateRedKey, btnLogout, btnFollow;
    private FirebaseFirestore db;

    private String myUid;      // المستخدم الحالي المسجّل دخوله
    private String viewedUid;  // صاحب الملف الشخصي المعروض حالياً (قد يكون نفس myUid)
    private boolean isOwnProfile;
    private boolean isFollowing = false;

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onImagePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        db = FirebaseFirestore.getInstance();
        myUid = SessionManager.getUid();

        String extraUid = getIntent().getStringExtra("uid");
        viewedUid = (extraUid != null) ? extraUid : myUid;
        isOwnProfile = viewedUid.equals(myUid);

        ivIcon = findViewById(R.id.ivIcon);
        tvUsername = findViewById(R.id.tvUsername);
        tvPlan = findViewById(R.id.tvPlan);
        tvFollowersCount = findViewById(R.id.tvFollowersCount);
        tvFollowingCount = findViewById(R.id.tvFollowingCount);
        btnChangeIcon = findViewById(R.id.btnChangeIcon);
        btnGenerateRedKey = findViewById(R.id.btnGenerateRedKey);
        btnLogout = findViewById(R.id.btnLogout);
        btnFollow = findViewById(R.id.btnFollow);

        if (isOwnProfile) {
            btnChangeIcon.setOnClickListener(v -> imagePicker.launch("image/*"));
            btnGenerateRedKey.setOnClickListener(v -> promptPasswordThenGenerateKey());
            btnLogout.setOnClickListener(v -> {
                SessionManager.logout();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
        } else {
            // نخفي الإجراءات الخاصة بصاحب الحساب فقط عند عرض ملف شخص آخر
            btnChangeIcon.setVisibility(View.GONE);
            btnGenerateRedKey.setVisibility(View.GONE);
            btnLogout.setVisibility(View.GONE);
            btnFollow.setVisibility(View.VISIBLE);
            btnFollow.setOnClickListener(v -> toggleFollow());
        }

        tvFollowersCount.setOnClickListener(v -> openUserList("followers"));
        tvFollowingCount.setOnClickListener(v -> openUserList("following"));

        loadProfile();
    }

    private void openUserList(String mode) {
        Intent intent = new Intent(this, UserListActivity.class);
        intent.putExtra("uid", viewedUid);
        intent.putExtra("mode", mode);
        startActivity(intent);
    }

    private void loadProfile() {
        db.collection("users").document(viewedUid).get()
                .addOnSuccessListener(snapshot -> {
                    User user = snapshot.toObject(User.class);
                    if (user == null) return;

                    tvUsername.setText(user.getDisplayName() + " (@" + user.getUsername() + ")");
                    tvPlan.setText(user.isPaid() ? "الخطة: مدفوعة ✅" : "الخطة: مجانية");
                    tvFollowersCount.setText(user.getFollowersCount() + " متابع");
                    tvFollowingCount.setText(user.getFollowingCount() + " يتابع");

                    if (user.getIconBase64() != null && !user.getIconBase64().isEmpty()) {
                        byte[] bytes = Base64.decode(user.getIconBase64(), Base64.NO_WRAP);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        ivIcon.setImageBitmap(bitmap);
                    }
                });

        if (!isOwnProfile && myUid != null) {
            SocialActions.isFollowing(myUid, viewedUid, following -> runOnUiThread(() -> {
                isFollowing = following;
                updateFollowButton();
            }));
        }
    }

    private void updateFollowButton() {
        btnFollow.setText(isFollowing ? "إلغاء المتابعة" : "متابعة");
        btnFollow.setBackgroundTintList(getColorStateList(isFollowing ? R.color.text_gray : R.color.primary));
    }

    private void toggleFollow() {
        if (myUid == null) return;
        btnFollow.setEnabled(false);
        if (isFollowing) {
            SocialActions.unfollow(myUid, viewedUid, (success, error) -> runOnUiThread(() -> {
                btnFollow.setEnabled(true);
                if (success) {
                    isFollowing = false;
                    updateFollowButton();
                    loadProfile();
                } else {
                    Toast.makeText(this, "فشلت العملية", Toast.LENGTH_SHORT).show();
                }
            }));
        } else {
            SocialActions.follow(myUid, viewedUid, (success, error) -> runOnUiThread(() -> {
                btnFollow.setEnabled(true);
                if (success) {
                    isFollowing = true;
                    updateFollowButton();
                    loadProfile();
                } else {
                    Toast.makeText(this, "فشلت العملية", Toast.LENGTH_SHORT).show();
                }
            }));
        }
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

            db.collection("users").document(myUid)
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

    /**
     * Firebase لا يعطينا كلمة المرور الحالية مطلقاً (لأنها غير مخزَّنة كنص أصلاً)،
     * لذا لتوليد مفتاح أحمر جديد نحتاج نطلب من المستخدم إدخالها مرة أخرى، ونتحقق
     * منها عبر reauthenticate قبل استخدامها في تشفير المفتاح.
     */
    private void promptPasswordThenGenerateKey() {
        EditText input = new EditText(this);
        input.setHint("أدخل كلمة المرور الحالية للتأكيد");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle("تأكيد كلمة المرور")
                .setMessage("لتوليد مفتاح أحمر جديد، أدخل كلمة مرور حسابك الحالية للتأكيد. أي مفتاح قديم سيتوقف عن العمل.")
                .setView(input)
                .setPositiveButton("تأكيد", (dialog, which) -> {
                    String password = input.getText().toString();
                    if (password.isEmpty()) {
                        Toast.makeText(this, "الرجاء إدخال كلمة المرور", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    reauthenticateAndGenerateKey(password);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void reauthenticateAndGenerateKey(String password) {
        com.google.firebase.auth.FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null || firebaseUser.getEmail() == null) return;

        String email = firebaseUser.getEmail();
        AuthCredential credential = EmailAuthProvider.getCredential(email, password);

        firebaseUser.reauthenticate(credential)
                .addOnSuccessListener(unused -> generateAndStoreRedKey(email, password))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "كلمة المرور غير صحيحة", Toast.LENGTH_SHORT).show());
    }

    private void generateAndStoreRedKey(String email, String password) {
        String redKey = RedKeyUtils.generateKey();
        String newHash = RedKeyUtils.hashKey(redKey);
        String payload = RedKeyUtils.encrypt(redKey, email, password);

        db.collection("users").document(myUid).get()
                .addOnSuccessListener(snapshot -> {
                    String oldHash = snapshot.getString("activeKeyHash");
                    if (oldHash != null && !oldHash.isEmpty()) {
                        // نحذف المفتاح القديم فعلياً حتى لا يبقى صالحاً للاستخدام بعد الآن
                        db.collection("passkeys").document(oldHash).delete();
                    }
                    saveNewRedKey(newHash, payload, redKey);
                })
                .addOnFailureListener(e -> saveNewRedKey(newHash, payload, redKey));
    }

    private void saveNewRedKey(String newHash, String payload, String redKey) {
        db.collection("passkeys").document(newHash)
                .set(java.util.Collections.singletonMap("payload", payload))
                .addOnSuccessListener(unused -> db.collection("users").document(myUid)
                        .update("activeKeyHash", newHash)
                        .addOnCompleteListener(task -> {
                            Intent intent = new Intent(this, RedKeyDisplayActivity.class);
                            intent.putExtra("red_key", redKey);
                            startActivity(intent);
                        }))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "فشل توليد المفتاح: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
