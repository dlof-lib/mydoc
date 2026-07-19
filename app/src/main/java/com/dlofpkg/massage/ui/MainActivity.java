package com.dlofpkg.massage.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.dlofpkg.massage.R;
import com.dlofpkg.massage.adapter.PostAdapter;
import com.dlofpkg.massage.model.Post;
import com.dlofpkg.massage.util.SessionManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

public class MainActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private PostAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!SessionManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        db = FirebaseFirestore.getInstance();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PostAdapter();
        recyclerView.setAdapter(adapter);

        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this::loadPosts);

        FloatingActionButton fabNewPost = findViewById(R.id.fabNewPost);
        fabNewPost.setOnClickListener(v -> startActivity(new Intent(this, NewPostActivity.class)));

        FloatingActionButton fabProfile = findViewById(R.id.fabProfile);
        fabProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));

        loadPosts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPosts();
    }

    private void loadPosts() {
        swipeRefresh.setRefreshing(true);
        db.collection("posts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    swipeRefresh.setRefreshing(false);
                    java.util.List<Post> posts = new java.util.ArrayList<>();
                    querySnapshot.forEach(doc -> {
                        Post post = doc.toObject(Post.class);
                        post.setId(doc.getId());
                        posts.add(post);
                    });
                    adapter.setPosts(posts);
                })
                .addOnFailureListener(e -> {
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(this, "فشل تحميل المنشورات: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
