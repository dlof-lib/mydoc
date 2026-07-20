package com.dlofpkg.massage.adapter;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dlofpkg.massage.R;
import com.dlofpkg.massage.model.Post;
import com.dlofpkg.massage.ui.PostDetailActivity;
import com.dlofpkg.massage.ui.ProfileActivity;
import com.dlofpkg.massage.util.SessionManager;
import com.dlofpkg.massage.util.SocialActions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * يعرض إما بطاقات منشورات حقيقية أو عدداً من بطاقات "هيكلية" (Skeleton)
 * أثناء انتظار أول استجابة حقيقية من Firestore. استخدام هذا بدل مؤشر تحميل
 * دائري وحيد يعطي إحساساً بأن المحتوى قادم فعلاً، وهو الشكل المعتاد في
 * تطبيقات التواصل الحديثة.
 */
public class PostAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_POST = 0;
    private static final int TYPE_SKELETON = 1;
    private static final int SKELETON_COUNT = 6;

    private static final String[] REPORT_REASONS = {
            "سبام أو محتوى مكرر", "محتوى غير لائق", "انتحال شخصية", "كود ضار", "سبب آخر"
    };

    private final List<Post> posts = new ArrayList<>();
    private final Set<String> likedPostIds = new HashSet<>();

    // نبدأ بحالة "تحميل" افتراضياً حتى تصل أول دفعة بيانات حقيقية من MainActivity
    private boolean loading = true;

    public void setPosts(List<Post> newPosts) {
        loading = false;
        posts.clear();
        posts.addAll(newPosts);
        notifyDataSetChanged();
    }

    /** يُفعَّل/يُعطَّل عرض البطاقات الهيكلية (يُستدعى قبل أول استجابة من Firestore، وعند السحب للتحديث). */
    public void setLoading(boolean isLoading) {
        if (this.loading == isLoading) return;
        this.loading = isLoading;
        notifyDataSetChanged();
    }

    /** يحدَّث بمجموعة معرّفات المنشورات التي أعجب بها المستخدم الحالي، لتلوين زر الإعجاب بشكل صحيح. */
    public void setLikedPostIds(Set<String> liked) {
        likedPostIds.clear();
        likedPostIds.addAll(liked);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return (loading && posts.isEmpty()) ? TYPE_SKELETON : TYPE_POST;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SKELETON) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_post_skeleton, parent, false);
            return new SkeletonViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder rawHolder, int position) {
        if (rawHolder instanceof SkeletonViewHolder) {
            ((SkeletonViewHolder) rawHolder).startShimmer();
            return;
        }
        bindPost((PostViewHolder) rawHolder, position);
    }

    private void bindPost(@NonNull PostViewHolder holder, int position) {
        Post post = posts.get(position);
        Context context = holder.itemView.getContext();

        holder.tvTitle.setText(post.getTitle());
        holder.tvAuthor.setText("بواسطة: " + post.getAuthorUsername());

        if (post.getRepostOfId() != null && !post.getRepostOfId().isEmpty()) {
            holder.tvRepostOf.setVisibility(View.VISIBLE);
            holder.tvRepostOf.setText("🔁 إعادة نشر من @" + post.getRepostOfAuthor());
        } else {
            holder.tvRepostOf.setVisibility(View.GONE);
        }

        String typeLabel;
        switch (post.getType()) {
            case Post.TYPE_CODE:
                typeLabel = "ملف كود";
                break;
            case Post.TYPE_ARTICLE:
                typeLabel = "مقال";
                break;
            default:
                typeLabel = "نص";
        }
        holder.tvType.setText(typeLabel);

        boolean liked = likedPostIds.contains(post.getId());
        updateLikeText(holder.tvLike, liked, post.getLikesCount());
        holder.tvRepost.setText(String.valueOf(post.getRepostsCount()));

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, PostDetailActivity.class);
            intent.putExtra("post_id", post.getId());
            context.startActivity(intent);
        });

        holder.tvAuthor.setOnClickListener(v -> openProfile(context, post.getAuthorUid()));

        holder.tvLike.setOnClickListener(v -> {
            String uid = SessionManager.getUid();
            if (uid == null) return;
            boolean newLiked = !likedPostIds.contains(post.getId());
            SocialActions.toggleLike(post.getId(), uid, !newLiked, (success, error) -> {
                if (!success) return;
                if (newLiked) {
                    likedPostIds.add(post.getId());
                    post.setLikesCount(post.getLikesCount() + 1);
                } else {
                    likedPostIds.remove(post.getId());
                    post.setLikesCount(Math.max(0, post.getLikesCount() - 1));
                }
                ((android.app.Activity) context).runOnUiThread(() ->
                        updateLikeText(holder.tvLike, likedPostIds.contains(post.getId()), post.getLikesCount()));
            });
        });

        holder.tvRepost.setOnClickListener(v -> {
            String uid = SessionManager.getUid();
            String username = SessionManager.getUsername();
            if (uid == null) return;
            SocialActions.repost(post, uid, username, (success, error) ->
                    ((android.app.Activity) context).runOnUiThread(() ->
                            Toast.makeText(context, success ? "تم إعادة النشر" : "فشل إعادة النشر", Toast.LENGTH_SHORT).show()));
        });

        holder.tvCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("post", post.getTitle() + "\n\n" + post.getContent()));
            Toast.makeText(context, "تم النسخ", Toast.LENGTH_SHORT).show();
        });

        holder.tvReport.setOnClickListener(v -> showReportDialog(context, post.getId()));
    }

    private void updateLikeText(TextView tv, boolean liked, long count) {
        tv.setText(String.valueOf(count));
        int iconRes = liked ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline;
        tv.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
        tv.setTextColor(androidx.core.content.ContextCompat.getColor(tv.getContext(),
                liked ? R.color.accent_red : R.color.text_dark));
    }

    private void openProfile(Context context, String uid) {
        if (uid == null) return;
        Intent intent = new Intent(context, ProfileActivity.class);
        intent.putExtra("uid", uid);
        context.startActivity(intent);
    }

    private void showReportDialog(Context context, String postId) {
        String uid = SessionManager.getUid();
        if (uid == null) return;
        new AlertDialog.Builder(context)
                .setTitle("سبب الإبلاغ")
                .setItems(REPORT_REASONS, (dialog, which) ->
                        SocialActions.report(postId, uid, REPORT_REASONS[which], (success, error) ->
                                ((android.app.Activity) context).runOnUiThread(() ->
                                        Toast.makeText(context, success ? "تم إرسال البلاغ، شكراً لك" : "فشل إرسال البلاغ", Toast.LENGTH_SHORT).show())))
                .setNegativeButton("إلغاء", null)
                .show();
    }

    @Override
    public int getItemCount() {
        if (loading && posts.isEmpty()) return SKELETON_COUNT;
        return posts.size();
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvAuthor, tvType, tvRepostOf, tvLike, tvRepost, tvCopy, tvReport;

        PostViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvAuthor = itemView.findViewById(R.id.tvAuthor);
            tvType = itemView.findViewById(R.id.tvType);
            tvRepostOf = itemView.findViewById(R.id.tvRepostOf);
            tvLike = itemView.findViewById(R.id.tvLike);
            tvRepost = itemView.findViewById(R.id.tvRepost);
            tvCopy = itemView.findViewById(R.id.tvCopy);
            tvReport = itemView.findViewById(R.id.tvReport);
        }
    }

    /** بطاقة هيكلية بسيطة مع تلاشٍ خفيف متكرر (Shimmer) بدل صورة GIF أو مكتبة خارجية. */
    static class SkeletonViewHolder extends RecyclerView.ViewHolder {
        private final View root;
        private ObjectAnimator animator;

        SkeletonViewHolder(View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.skeletonRoot);
        }

        void startShimmer() {
            if (animator != null) animator.cancel();
            root.setAlpha(0.5f);
            animator = ObjectAnimator.ofFloat(root, "alpha", 0.5f, 1f, 0.5f);
            animator.setDuration(1100);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.start();
        }
    }
}
