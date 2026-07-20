package com.dlofpkg.massage.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dlofpkg.massage.R;
import com.dlofpkg.massage.model.Post;
import com.dlofpkg.massage.util.SessionManager;
import com.dlofpkg.massage.util.SocialActions;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class PostDetailActivity extends AppCompatActivity {

    private static final String[] REPORT_REASONS = {
            "سبام أو محتوى مكرر", "محتوى غير لائق", "انتحال شخصية", "كود ضار", "سبب آخر"
    };

    private Post currentPost;
    private boolean liked = false;
    private TextView tvLike, tvRepost;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);
        db = FirebaseFirestore.getInstance();

        String postId = getIntent().getStringExtra("post_id");
        if (postId == null) {
            finish();
            return;
        }

        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvAuthor = findViewById(R.id.tvAuthor);
        TextView tvContent = findViewById(R.id.tvContent);
        TextView tvFileInfo = findViewById(R.id.tvFileInfo);
        TextView tvRepostOf = findViewById(R.id.tvRepostOf);
        Button btnSaveFile = findViewById(R.id.btnSaveFile);
        tvLike = findViewById(R.id.tvLike);
        tvRepost = findViewById(R.id.tvRepost);
        TextView tvCopy = findViewById(R.id.tvCopy);
        TextView tvReport = findViewById(R.id.tvReport);

        db.collection("posts").document(postId).get()
                .addOnSuccessListener(snapshot -> {
                    Post post = snapshot.toObject(Post.class);
                    if (post == null) return;
                    post.setId(snapshot.getId());
                    currentPost = post;

                    tvTitle.setText(post.getTitle());
                    tvAuthor.setText("بواسطة: " + post.getAuthorUsername());
                    tvAuthor.setOnClickListener(v -> {
                        Intent intent = new Intent(this, ProfileActivity.class);
                        intent.putExtra("uid", post.getAuthorUid());
                        startActivity(intent);
                    });
                    tvContent.setText(post.getContent());
                    updateRepostText();

                    if (post.getRepostOfId() != null && !post.getRepostOfId().isEmpty()) {
                        tvRepostOf.setVisibility(View.VISIBLE);
                        tvRepostOf.setText("🔁 إعادة نشر من @" + post.getRepostOfAuthor());
                    }

                    if (post.getFileBase64() != null && !post.getFileBase64().isEmpty()) {
                        tvFileInfo.setText("📎 ملف مرفق: " + post.getFileName());
                        btnSaveFile.setVisibility(View.VISIBLE);
                        btnSaveFile.setOnClickListener(v -> saveAttachedFile());
                    }

                    checkIfLiked();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "فشل تحميل المنشور: " + e.getMessage(), Toast.LENGTH_SHORT).show());

        tvLike.setOnClickListener(v -> toggleLike());
        tvRepost.setOnClickListener(v -> doRepost());
        tvCopy.setOnClickListener(v -> copyToClipboard());
        tvReport.setOnClickListener(v -> showReportDialog());
    }

    private void checkIfLiked() {
        String uid = SessionManager.getUid();
        if (uid == null || currentPost == null) return;
        db.collection("likes").document(currentPost.getId() + "_" + uid).get()
                .addOnSuccessListener(snapshot -> {
                    liked = snapshot.exists();
                    updateLikeText();
                });
    }

    private void updateLikeText() {
        if (currentPost == null) return;
        tvLike.setText((liked ? "❤️ " : "🤍 ") + currentPost.getLikesCount());
    }

    private void updateRepostText() {
        if (currentPost == null) return;
        tvRepost.setText("🔁 " + currentPost.getRepostsCount());
    }

    private void toggleLike() {
        String uid = SessionManager.getUid();
        if (uid == null || currentPost == null) return;
        boolean newLiked = !liked;
        SocialActions.toggleLike(currentPost.getId(), uid, liked, (success, error) -> runOnUiThread(() -> {
            if (!success) {
                Toast.makeText(this, "فشلت العملية", Toast.LENGTH_SHORT).show();
                return;
            }
            liked = newLiked;
            currentPost.setLikesCount(currentPost.getLikesCount() + (newLiked ? 1 : -1));
            updateLikeText();
        }));
    }

    private void doRepost() {
        String uid = SessionManager.getUid();
        String username = SessionManager.getUsername();
        if (uid == null || currentPost == null) return;
        SocialActions.repost(currentPost, uid, username, (success, error) -> runOnUiThread(() -> {
            Toast.makeText(this, success ? "تم إعادة النشر" : "فشل إعادة النشر", Toast.LENGTH_SHORT).show();
            if (success) {
                currentPost.setRepostsCount(currentPost.getRepostsCount() + 1);
                updateRepostText();
            }
        }));
    }

    private void copyToClipboard() {
        if (currentPost == null) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("post", currentPost.getTitle() + "\n\n" + currentPost.getContent()));
        Toast.makeText(this, "تم النسخ", Toast.LENGTH_SHORT).show();
    }

    private void showReportDialog() {
        String uid = SessionManager.getUid();
        if (uid == null || currentPost == null) return;
        new AlertDialog.Builder(this)
                .setTitle("سبب الإبلاغ")
                .setItems(REPORT_REASONS, (dialog, which) ->
                        SocialActions.report(currentPost.getId(), uid, REPORT_REASONS[which], (success, error) ->
                                runOnUiThread(() -> Toast.makeText(this, success ? "تم إرسال البلاغ، شكراً لك" : "فشل إرسال البلاغ", Toast.LENGTH_SHORT).show())))
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void saveAttachedFile() {
        if (currentPost == null) return;
        try {
            byte[] bytes = Base64.decode(currentPost.getFileBase64(), Base64.NO_WRAP);
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            File outFile = new File(dir, currentPost.getFileName());
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(bytes);
            }
            Toast.makeText(this, "تم الحفظ في: " + outFile.getPath(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "فشل حفظ الملف: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
