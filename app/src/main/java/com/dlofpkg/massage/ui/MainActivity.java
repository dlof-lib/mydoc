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
import com.dlofpkg.massage.util.ThemeManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * الشاشة الرئيسية. تحتوي شريط تنقّل سفلي بأربعة أقسام:
 * الرئيسية (كل المنشورات) / مقالات / برمجيات (ملفات الكود) / حسابي.
 * الفلترة تتم محلياً على القائمة المحمَّلة مسبقاً بدل استعلام Firestore
 * منفصل لكل تبويب، لتفادي الحاجة لفهارس مركّبة (composite index) في
 * Firestore والتي تسبب فشلاً صامتاً في التحميل إن لم تُنشأ يدوياً.
 */
public class MainActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private PostAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private BottomNavigationView bottomNav;

    private final List<Post> allPosts = new ArrayList<>();
    private String currentFilter = null; // null = بدون فلترة (تبويب الرئيسية)

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

        bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.navHome) {
                currentFilter = null;
                applyFilter();
                return true;
            } else if (id == R.id.navArticles) {
                currentFilter = Post.TYPE_ARTICLE;
                applyFilter();
                return true;
            } else if (id == R.id.navSoftware) {
                currentFilter = Post.TYPE_CODE;
                applyFilter();
                return true;
            } else if (id == R.id.navAccount) {
                startActivity(new Intent(this, ProfileActivity.class));
                // نعيد التحديد لتبويب الرئيسية بصرياً بعد العودة، حتى لا يبقى
                // "حسابي" محدداً وهو تبويب ينتقل لشاشة أخرى وليس فلتراً.
                bottomNav.post(() -> bottomNav.setSelectedItemId(R.id.navHome));
                return true;
            }
            return false;
        });

        ThemeManager.applyToActivity(this);
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
                .limit(100)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    swipeRefresh.setRefreshing(false);
                    allPosts.clear();
                    querySnapshot.forEach(doc -> {
                        Post post = doc.toObject(Post.class);
                        post.setId(doc.getId());
                        allPosts.add(post);
                    });
                    applyFilter();
                    loadMyLikes();
                })
                .addOnFailureListener(e -> {
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(this, "فشل تحميل المنشورات: " + friendlyError(e), Toast.LENGTH_LONG).show();
                });
    }

    /** يطبّق فلتر التبويب الحالي (بدون إعادة تحميل من الشبكة) على القائمة المحمَّلة مسبقاً. */
    private void applyFilter() {
        if (currentFilter == null) {
            adapter.setPosts(allPosts);
            return;
        }
        List<Post> filtered = new ArrayList<>();
        for (Post p : allPosts) {
            if (currentFilter.equals(p.getType())) filtered.add(p);
        }
        adapter.setPosts(filtered);
    }

    /** يجلب مرة واحدة معرّفات كل المنشورات التي أعجب بها المستخدم الحالي، لتلوين أزرار الإعجاب بشكل صحيح. */
    private void loadMyLikes() {
        String uid = SessionManager.getUid();
        if (uid == null) return;
        db.collection("likes").whereEqualTo("uid", uid).get()
                .addOnSuccessListener(snapshot -> {
                    java.util.Set<String> likedIds = new java.util.HashSet<>();
                    snapshot.forEach(doc -> likedIds.add(doc.getString("postId")));
                    adapter.setLikedPostIds(likedIds);
                });
    }

    private String friendlyError(Exception e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("PERMISSION_DENIED")) {
            return "تم رفض القراءة من Firestore (تحقق من تفعيل Cloud Firestore ونشر firestore.rules)";
        }
        return msg;
    }
}
