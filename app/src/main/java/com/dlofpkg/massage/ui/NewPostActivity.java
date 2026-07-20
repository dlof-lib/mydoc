package com.dlofpkg.massage.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.dlofpkg.massage.R;
import com.dlofpkg.massage.model.Post;
import com.dlofpkg.massage.util.Base64Utils;
import com.dlofpkg.massage.util.SessionManager;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;

public class NewPostActivity extends AppCompatActivity {

    private EditText etTitle, etContent;
    private RadioGroup radioGroupType;
    private TextView tvFileName;
    private ProgressBar progressBar;
    private FirebaseFirestore db;

    private String attachedFileBase64 = "";
    private String attachedFileName = "";

    private final ActivityResultLauncher<String> filePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onFilePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_post);
        db = FirebaseFirestore.getInstance();

        etTitle = findViewById(R.id.etTitle);
        etContent = findViewById(R.id.etContent);
        radioGroupType = findViewById(R.id.radioGroupType);
        tvFileName = findViewById(R.id.tvFileName);
        progressBar = findViewById(R.id.progressBar);

        Button btnAttachFile = findViewById(R.id.btnAttachFile);
        Button btnPublish = findViewById(R.id.btnPublish);

        btnAttachFile.setOnClickListener(v -> filePicker.launch("*/*"));
        btnPublish.setOnClickListener(v -> publishPost());
    }

    private void onFilePicked(Uri uri) {
        if (uri == null) return;
        try {
            attachedFileBase64 = Base64Utils.uriToBase64(getContentResolver(), uri);
            attachedFileName = getFileNameFromUri(uri);
            tvFileName.setText("تم إرفاق: " + attachedFileName);
        } catch (IOException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String result = "file";
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) result = cursor.getString(idx);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private void publishPost() {
        String title = etTitle.getText().toString().trim();
        String content = etContent.getText().toString().trim();

        if (TextUtils.isEmpty(title) || (TextUtils.isEmpty(content) && TextUtils.isEmpty(attachedFileBase64))) {
            Toast.makeText(this, "الرجاء إدخال عنوان ومحتوى أو ملف مرفق", Toast.LENGTH_SHORT).show();
            return;
        }

        String type;
        int checkedId = radioGroupType.getCheckedRadioButtonId();
        if (checkedId == R.id.radioCode) {
            type = Post.TYPE_CODE;
        } else if (checkedId == R.id.radioText) {
            type = Post.TYPE_TEXT;
        } else {
            type = Post.TYPE_ARTICLE;
        }

        String uid = SessionManager.getUid();
        if (uid == null) {
            Toast.makeText(this, "الرجاء تسجيل الدخول أولاً", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        String cachedUsername = SessionManager.getUsername();
        if (cachedUsername != null && !cachedUsername.isEmpty()) {
            doPublish(uid, cachedUsername, title, type, content);
        } else {
            // احتياط: إذا لم يكن الاسم متوفراً محلياً بعد (نادر جداً)، نجلبه
            // من Firestore أولاً بدل نشر منشور بمؤلف فارغ.
            SessionManager.refreshUsernameFromFirestore(() -> {
                String username = SessionManager.getUsername();
                doPublish(uid, username != null ? username : "مستخدم", title, type, content);
            });
        }
    }

    private void doPublish(String uid, String username, String title, String type, String content) {
        Post post = new Post(uid, username, title, type, content);
        post.setFileBase64(attachedFileBase64);
        post.setFileName(attachedFileName);

        db.collection("posts").add(post)
                .addOnSuccessListener(ref -> {
                    setLoading(false);
                    Toast.makeText(this, "تم النشر بنجاح", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "فشل النشر: " + friendlyError(e), Toast.LENGTH_LONG).show();
                });
    }

    /** يترجم أخطاء Firestore الشائعة لرسالة مفهومة، خصوصاً خطأ الصلاحيات
     * الذي يحدث عادة بسبب عدم تفعيل Firestore أو عدم نشر firestore.rules. */
    private String friendlyError(Exception e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("PERMISSION_DENIED")) {
            return "تم رفض الحفظ من Firestore (تحقق من تفعيل Cloud Firestore ونشر firestore.rules في مشروع Firebase)";
        }
        return msg;
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
