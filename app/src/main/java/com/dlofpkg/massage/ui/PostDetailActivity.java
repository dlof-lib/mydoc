package com.dlofpkg.massage.ui;

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
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class PostDetailActivity extends AppCompatActivity {

    private Post currentPost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);

        String postId = getIntent().getStringExtra("post_id");
        if (postId == null) {
            finish();
            return;
        }

        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvAuthor = findViewById(R.id.tvAuthor);
        TextView tvContent = findViewById(R.id.tvContent);
        TextView tvFileInfo = findViewById(R.id.tvFileInfo);
        Button btnSaveFile = findViewById(R.id.btnSaveFile);

        FirebaseFirestore.getInstance().collection("posts").document(postId).get()
                .addOnSuccessListener(snapshot -> {
                    Post post = snapshot.toObject(Post.class);
                    if (post == null) return;
                    post.setId(snapshot.getId());
                    currentPost = post;

                    tvTitle.setText(post.getTitle());
                    tvAuthor.setText("بواسطة: " + post.getAuthorUsername());
                    tvContent.setText(post.getContent());

                    if (post.getFileBase64() != null && !post.getFileBase64().isEmpty()) {
                        tvFileInfo.setText("📎 ملف مرفق: " + post.getFileName());
                        btnSaveFile.setVisibility(View.VISIBLE);
                        btnSaveFile.setOnClickListener(v -> saveAttachedFile());
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "فشل تحميل المنشور: " + e.getMessage(), Toast.LENGTH_SHORT).show());
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
