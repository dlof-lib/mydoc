package com.dlofpkg.massage.ui;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dlofpkg.massage.R;
import com.dlofpkg.massage.adapter.UserAdapter;
import com.dlofpkg.massage.model.User;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * تعرض قائمة "المتابِعين" أو "من يتابعهم" لمستخدم معيّن.
 * تُفتح بتمرير: uid (صاحب القائمة) و mode ("followers" أو "following").
 */
public class UserListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_list);

        String uid = getIntent().getStringExtra("uid");
        String mode = getIntent().getStringExtra("mode"); // followers | following
        if (uid == null || mode == null) {
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(mode.equals("followers") ? "المتابِعون" : "يتابع");
        setSupportActionBar(toolbar);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        UserAdapter adapter = new UserAdapter();
        recyclerView.setAdapter(adapter);
        View tvEmpty = findViewById(R.id.tvEmpty);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String field = mode.equals("followers") ? "followingUid" : "followerUid";

        db.collection("follows").whereEqualTo(field, uid).get()
                .addOnSuccessListener(snapshot -> {
                    List<String> otherUids = new ArrayList<>();
                    snapshot.forEach(doc -> {
                        String otherField = mode.equals("followers") ? "followerUid" : "followingUid";
                        otherUids.add(doc.getString(otherField));
                    });

                    if (otherUids.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        return;
                    }
                    loadUsers(db, otherUids, adapter, tvEmpty);
                });
    }

    /** Firestore يسمح بحد أقصى 30 عنصراً في whereIn، لذا نقسّم القائمة إلى دفعات عند الحاجة. */
    private void loadUsers(FirebaseFirestore db, List<String> uids, UserAdapter adapter, View tvEmpty) {
        List<User> result = new ArrayList<>();
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < uids.size(); i += 30) {
            batches.add(uids.subList(i, Math.min(i + 30, uids.size())));
        }

        int[] remaining = {batches.size()};
        for (List<String> batch : batches) {
            db.collection("users").whereIn("uid", batch).get()
                    .addOnSuccessListener(snapshot -> {
                        snapshot.forEach(doc -> result.add(doc.toObject(User.class)));
                        remaining[0]--;
                        if (remaining[0] == 0) {
                            if (result.isEmpty()) tvEmpty.setVisibility(View.VISIBLE);
                            adapter.setUsers(result);
                        }
                    });
        }
    }
}
