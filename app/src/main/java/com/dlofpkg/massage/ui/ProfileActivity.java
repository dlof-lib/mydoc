package com.dlofpkg.massage.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.dlofpkg.massage.util.ThemeManager;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    // أقصى أبعاد للصورة قبل الترميز، لضمان بقاء حجمها صغيراً داخل Firestore
    private static final int MAX_ICON_DIMENSION = 256;

    private ImageView ivIcon;
    private TextView tvUsername, tvPlan, tvFollowersCount, tvFollowingCount;
    private TextView tvPostsCount, tvRepostsCount, tvLikesGivenCount;
    private TextView tvFieldDisplayName, tvFieldUsername, tvFieldEmail;
    private Button btnChangeIcon, btnGenerateRedKey, btnLogout, btnFollow, btnChangePassword;
    private LinearLayout sectionAccountInfo, sectionSettings, swatchContainer;
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
        // نجعل صورة البروفايل مدوّرة فعلياً (وليس فقط خلفية دائرية خلف صورة
        // مربعة)، بقصّ الـ ImageView نفسه على شكل دائرة كاملة.
        ivIcon.setClipToOutline(true);
        ivIcon.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        tvUsername = findViewById(R.id.tvUsername);
        tvPlan = findViewById(R.id.tvPlan);
        tvFollowersCount = findViewById(R.id.tvFollowersCount);
        tvFollowingCount = findViewById(R.id.tvFollowingCount);
        tvPostsCount = findViewById(R.id.tvPostsCount);
        tvRepostsCount = findViewById(R.id.tvRepostsCount);
        tvLikesGivenCount = findViewById(R.id.tvLikesGivenCount);
        tvFieldDisplayName = findViewById(R.id.tvFieldDisplayName);
        tvFieldUsername = findViewById(R.id.tvFieldUsername);
        tvFieldEmail = findViewById(R.id.tvFieldEmail);
        btnChangeIcon = findViewById(R.id.btnChangeIcon);
        btnGenerateRedKey = findViewById(R.id.btnGenerateRedKey);
        btnLogout = findViewById(R.id.btnLogout);
        btnFollow = findViewById(R.id.btnFollow);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        sectionAccountInfo = findViewById(R.id.sectionAccountInfo);
        sectionSettings = findViewById(R.id.sectionSettings);
        swatchContainer = findViewById(R.id.swatchContainer);
        ImageButton btnEditDisplayName = findViewById(R.id.btnEditDisplayName);
        ImageButton btnEditUsername = findViewById(R.id.btnEditUsername);
        ImageButton btnEditEmail = findViewById(R.id.btnEditEmail);

        if (isOwnProfile) {
            btnChangeIcon.setOnClickListener(v -> imagePicker.launch("image/*"));
            btnGenerateRedKey.setOnClickListener(v -> promptPasswordThenGenerateKey());
            btnLogout.setOnClickListener(v -> {
                SessionManager.logout();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
            btnEditDisplayName.setOnClickListener(v -> promptEditDisplayName());
            btnEditUsername.setOnClickListener(v -> promptEditUsername());
            btnEditEmail.setOnClickListener(v -> promptEditEmail());
            btnChangePassword.setOnClickListener(v -> promptChangePassword());
            buildCustomizationSwatches();
        } else {
            // نخفي الإجراءات وقسمي "معلومات الحساب" و"الإعدادات" الخاصين بصاحب
            // الحساب فقط عند عرض ملف شخص آخر.
            btnChangeIcon.setVisibility(View.GONE);
            btnGenerateRedKey.setVisibility(View.GONE);
            btnLogout.setVisibility(View.GONE);
            sectionAccountInfo.setVisibility(View.GONE);
            sectionSettings.setVisibility(View.GONE);
            btnFollow.setVisibility(View.VISIBLE);
            btnFollow.setOnClickListener(v -> toggleFollow());
        }

        tvFollowersCount.setOnClickListener(v -> openUserList("followers"));
        tvFollowingCount.setOnClickListener(v -> openUserList("following"));
        tvPostsCount.setOnClickListener(v -> openMyPosts());

        loadProfile();
        loadContentCounts();
        ThemeManager.applyToActivity(this);
    }

    /**
     * حماية للحسابات القديمة التي أُنشئت قبل إضافة نظام "منع تكرار الاسم":
     * إن لم يكن اسم المستخدم الحالي محجوزاً في مجموعة usernames، نحجزه له
     * تلقائياً بصمت الآن. إن كان محجوزاً بالفعل (لنفس الحساب أو لحساب آخر
     * قديم بنفس الاسم من قبل هذه الميزة)، لا نفعل شيئاً.
     */
    private void ensureUsernameReserved(String username) {
        if (username == null || username.isEmpty()) return;
        String key = username.toLowerCase();
        db.collection("usernames").document(key).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        java.util.Map<String, Object> reservation = new java.util.HashMap<>();
                        reservation.put("uid", myUid);
                        db.collection("usernames").document(key).set(reservation);
                    }
                });
    }

    private void openUserList(String mode) {
        Intent intent = new Intent(this, UserListActivity.class);
        intent.putExtra("uid", viewedUid);
        intent.putExtra("mode", mode);
        startActivity(intent);
    }

    /** يفتح الشاشة الرئيسية لعرض منشورات هذا المستخدم فقط (تُستخدم فقط عند عرض ملفك أنت حالياً). */
    private void openMyPosts() {
        if (!isOwnProfile) return;
        Toast.makeText(this, "افتح تبويب الرئيسية/مقالات/برمجيات من الأسفل لعرض منشوراتك ضمن القائمة", Toast.LENGTH_LONG).show();
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

                    if (isOwnProfile) {
                        tvFieldDisplayName.setText(user.getDisplayName());
                        tvFieldUsername.setText("@" + user.getUsername());
                        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
                        tvFieldEmail.setText(fbUser != null && fbUser.getEmail() != null ? fbUser.getEmail() : "-");
                        ensureUsernameReserved(user.getUsername());
                    }

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

    /** يحسب عدد المنشورات وإعادات النشر والإعجابات المُعطاة لهذا الحساب من مجموعتي posts وlikes. */
    private void loadContentCounts() {
        db.collection("posts").whereEqualTo("authorUid", viewedUid).get()
                .addOnSuccessListener(snapshot -> {
                    int total = snapshot.size();
                    int reposts = 0;
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snapshot) {
                        String repostOfId = doc.getString("repostOfId");
                        if (repostOfId != null && !repostOfId.isEmpty()) reposts++;
                    }
                    tvPostsCount.setText(total + " منشور");
                    tvRepostsCount.setText(reposts + " إعادة نشر");
                })
                .addOnFailureListener(e -> {
                    tvPostsCount.setText("- منشور");
                    tvRepostsCount.setText("- إعادة نشر");
                });

        db.collection("likes").whereEqualTo("uid", viewedUid).get()
                .addOnSuccessListener(snapshot -> tvLikesGivenCount.setText(snapshot.size() + " إعجاب"))
                .addOnFailureListener(e -> tvLikesGivenCount.setText("- إعجاب"));
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

    // ======================= تعديل الاسم / المعرّف / البريد / كلمة المرور =======================

    private void promptEditDisplayName() {
        EditText input = new EditText(this);
        input.setText(tvFieldDisplayName.getText());
        new AlertDialog.Builder(this)
                .setTitle("تعديل الاسم")
                .setView(input)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (TextUtils.isEmpty(newName)) return;
                    FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (fbUser == null) return;
                    db.collection("users").document(myUid).update("displayName", newName)
                            .addOnSuccessListener(u -> {
                                tvFieldDisplayName.setText(newName);
                                loadProfile();
                                Toast.makeText(this, "تم تحديث الاسم", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "فشل التحديث: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void promptEditUsername() {
        EditText input = new EditText(this);
        String current = tvFieldUsername.getText().toString().replace("@", "");
        input.setText(current);
        new AlertDialog.Builder(this)
                .setTitle("تعديل المعرّف (@)")
                .setMessage("بالإنجليزية فقط، بدون مسافات (3-20 حرف). ملاحظة: المنشورات القديمة ستبقى تعرض المعرّف السابق.")
                .setView(input)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    String newUsername = input.getText().toString().trim();
                    if (!newUsername.matches("^[a-zA-Z0-9_.]{3,20}$")) {
                        Toast.makeText(this, "صيغة المعرّف غير صحيحة", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newUsername.equalsIgnoreCase(current)) {
                        return; // لم يتغيّر شيء فعلياً
                    }
                    checkUsernameAvailableThenSwap(current, newUsername);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    /**
     * يتحقق أولاً أن الاسم الجديد غير مستخدم، ثم يحاول حجزه في مجموعة
     * usernames. الحجز نفسه هو الضمان الحقيقي (بفضل قواعد الأمان)، وليس
     * هذا الفحص المبدئي فقط، لذا حتى لو حصل تسابق نادر بين مستخدمين، لن
     * يفوز بالاسم إلا حساب واحد.
     */
    private void checkUsernameAvailableThenSwap(String oldUsername, String newUsername) {
        String newKey = newUsername.toLowerCase();
        String oldKey = oldUsername.toLowerCase();

        db.collection("usernames").document(newKey).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        Toast.makeText(this, "الاسم @" + newUsername + " مستخدم بالفعل من قبل حساب آخر", Toast.LENGTH_LONG).show();
                        return;
                    }
                    reserveNewUsernameThenApply(oldKey, newUsername, newKey);
                })
                .addOnFailureListener(e -> reserveNewUsernameThenApply(oldKey, newUsername, newKey));
    }

    private void reserveNewUsernameThenApply(String oldKey, String newUsername, String newKey) {
        java.util.Map<String, Object> reservation = new java.util.HashMap<>();
        reservation.put("uid", myUid);

        db.collection("usernames").document(newKey).set(reservation)
                .addOnSuccessListener(unused -> {
                    // نجح حجز الاسم الجديد: نحرّر الاسم القديم، ثم نحدّث الحساب فعلياً
                    db.collection("usernames").document(oldKey).delete();
                    applyNewUsername(newUsername);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "الاسم @" + newUsername + " أصبح مستخدماً للتو من حساب آخر", Toast.LENGTH_LONG).show());
    }

    private void applyNewUsername(String newUsername) {
        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser == null) return;
        UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                .setDisplayName(newUsername).build();
        // ملاحظة: displayName في Firebase Auth يُستخدم داخلياً كـ "اسم المؤلف"
        // عند نشر منشور جديد (SessionManager.getUsername())، لذا يجب تحديثه
        // هنا أيضاً حتى تعكس المنشورات الجديدة المعرّف الجديد فوراً.
        fbUser.updateProfile(req).addOnCompleteListener(t ->
                db.collection("users").document(myUid).update("username", newUsername)
                        .addOnSuccessListener(u -> {
                            SessionManager.cacheUsername(myUid, newUsername);
                            tvFieldUsername.setText("@" + newUsername);
                            loadProfile();
                            Toast.makeText(this, "تم تحديث المعرّف", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> Toast.makeText(this, "فشل التحديث: " + e.getMessage(), Toast.LENGTH_SHORT).show()));
    }

    private void promptEditEmail() {
        EditText emailInput = new EditText(this);
        emailInput.setHint("البريد الإلكتروني الجديد");
        emailInput.setText(tvFieldEmail.getText());
        EditText passwordInput = new EditText(this);
        passwordInput.setHint("كلمة المرور الحالية للتأكيد");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(emailInput);
        container.addView(passwordInput);

        new AlertDialog.Builder(this)
                .setTitle("تعديل البريد الإلكتروني")
                .setView(container)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    String newEmail = emailInput.getText().toString().trim();
                    String password = passwordInput.getText().toString();
                    if (!Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                        Toast.makeText(this, "بريد إلكتروني غير صحيح", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (TextUtils.isEmpty(password)) {
                        Toast.makeText(this, "أدخل كلمة المرور للتأكيد", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (fbUser == null || fbUser.getEmail() == null) return;
                    AuthCredential credential = EmailAuthProvider.getCredential(fbUser.getEmail(), password);
                    fbUser.reauthenticate(credential)
                            .addOnSuccessListener(unused -> fbUser.updateEmail(newEmail)
                                    .addOnSuccessListener(u -> {
                                        tvFieldEmail.setText(newEmail);
                                        Toast.makeText(this, "تم تحديث البريد الإلكتروني", Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e -> Toast.makeText(this, "فشل تحديث البريد: " + e.getMessage(), Toast.LENGTH_LONG).show()))
                            .addOnFailureListener(e -> Toast.makeText(this, "كلمة المرور غير صحيحة", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void promptChangePassword() {
        EditText currentInput = new EditText(this);
        currentInput.setHint("كلمة المرور الحالية");
        currentInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText newInput = new EditText(this);
        newInput.setHint("كلمة المرور الجديدة (6 أحرف على الأقل)");
        newInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(currentInput);
        container.addView(newInput);

        new AlertDialog.Builder(this)
                .setTitle("تغيير كلمة المرور")
                .setView(container)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    String current = currentInput.getText().toString();
                    String newPass = newInput.getText().toString();
                    if (newPass.length() < 6) {
                        Toast.makeText(this, "كلمة المرور الجديدة قصيرة جداً", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (fbUser == null || fbUser.getEmail() == null) return;
                    AuthCredential credential = EmailAuthProvider.getCredential(fbUser.getEmail(), current);
                    fbUser.reauthenticate(credential)
                            .addOnSuccessListener(unused -> fbUser.updatePassword(newPass)
                                    .addOnSuccessListener(u -> Toast.makeText(this, "تم تغيير كلمة المرور. ملاحظة: يُفضّل توليد مفتاح أحمر جديد لأن القديم يعتمد على كلمة المرور السابقة.", Toast.LENGTH_LONG).show())
                                    .addOnFailureListener(e -> Toast.makeText(this, "فشل التغيير: " + e.getMessage(), Toast.LENGTH_LONG).show()))
                            .addOnFailureListener(e -> Toast.makeText(this, "كلمة المرور الحالية غير صحيحة", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    // ======================= حاوية التخصيص (mydoc.md / re.ui.css / io.yml) =======================

    /** يبني دوائر ألوان قابلة للنقر داخل حاوية التخصيص، ويحفظ الاختيار في Firestore فور الضغط. */
    private void buildCustomizationSwatches() {
        swatchContainer.removeAllViews();
        int sizeDp = 40;
        int size = (int) (sizeDp * getResources().getDisplayMetrics().density);
        int margin = (int) (6 * getResources().getDisplayMetrics().density);

        for (Map.Entry<String, Integer> entry : ThemeManager.SWATCHES.entrySet()) {
            View swatch = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            swatch.setLayoutParams(lp);

            android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circle.setColor(entry.getValue());
            circle.setStroke((int) (2 * getResources().getDisplayMetrics().density), Color.parseColor("#30363D"));
            swatch.setBackground(circle);
            swatch.setForeground(getDrawableCompatRipple());

            String hex = String.format("#%06X", (0xFFFFFF & entry.getValue()));
            swatch.setOnClickListener(v -> {
                if (myUid == null) return;
                ThemeManager.saveCustomColor(myUid, hex, () -> runOnUiThread(() -> {
                    Toast.makeText(this, "تم تطبيق اللون الجديد", Toast.LENGTH_SHORT).show();
                    ThemeManager.applyToActivity(this);
                }));
            });
            swatchContainer.addView(swatch);
        }
    }

    private android.graphics.drawable.Drawable getDrawableCompatRipple() {
        android.util.TypedValue outValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
        return getResources().getDrawable(outValue.resourceId, getTheme());
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
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
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
