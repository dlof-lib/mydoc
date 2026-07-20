package com.dlofpkg.massage.util;

import com.dlofpkg.massage.model.Post;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * يجمّع كل عمليات التفاعل البسيطة (إعجاب، إعادة نشر، إبلاغ، متابعة) في
 * مكان واحد بدل تكرارها في كل شاشة. كل العمليات خفيفة وتعتمد على
 * FieldValue.increment للعدّادات (ذرّية وآمنة بدون الحاجة لـ transaction).
 */
public class SocialActions {

    public interface Callback {
        void onResult(boolean success, String error);
    }

    public interface BoolCallback {
        void onResult(boolean value);
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    // ---------- إعجاب ----------

    private static String likeDocId(String postId, String uid) {
        return postId + "_" + uid;
    }

    /** يبدّل حالة الإعجاب: يعجب إن لم يكن معجباً، ويلغي الإعجاب إن كان معجباً بالفعل. */
    public static void toggleLike(String postId, String uid, boolean currentlyLiked, Callback callback) {
        String docId = likeDocId(postId, uid);
        if (currentlyLiked) {
            db().collection("likes").document(docId).delete()
                    .addOnSuccessListener(unused ->
                            db().collection("posts").document(postId)
                                    .update("likesCount", FieldValue.increment(-1))
                                    .addOnSuccessListener(u2 -> callback.onResult(true, null))
                                    .addOnFailureListener(e -> callback.onResult(false, e.getMessage())))
                    .addOnFailureListener(e -> callback.onResult(false, e.getMessage()));
        } else {
            Map<String, Object> like = new HashMap<>();
            like.put("postId", postId);
            like.put("uid", uid);
            like.put("timestamp", System.currentTimeMillis());
            db().collection("likes").document(docId).set(like)
                    .addOnSuccessListener(unused ->
                            db().collection("posts").document(postId)
                                    .update("likesCount", FieldValue.increment(1))
                                    .addOnSuccessListener(u2 -> callback.onResult(true, null))
                                    .addOnFailureListener(e -> callback.onResult(false, e.getMessage())))
                    .addOnFailureListener(e -> callback.onResult(false, e.getMessage()));
        }
    }

    // ---------- إعادة نشر ----------

    /** ينشئ منشوراً جديداً كإعادة نشر، ويزيد عدّاد المنشور الأصلي. */
    public static void repost(Post original, String myUid, String myUsername, Callback callback) {
        Post repost = new Post(myUid, myUsername, original.getTitle(), original.getType(), original.getContent());
        repost.setFileBase64(original.getFileBase64());
        repost.setFileName(original.getFileName());
        repost.setRepostOfId(original.getId());
        repost.setRepostOfAuthor(original.getAuthorUsername());

        db().collection("posts").add(repost)
                .addOnSuccessListener(ref ->
                        db().collection("posts").document(original.getId())
                                .update("repostsCount", FieldValue.increment(1))
                                .addOnSuccessListener(u -> callback.onResult(true, null))
                                .addOnFailureListener(e -> callback.onResult(false, e.getMessage())))
                .addOnFailureListener(e -> callback.onResult(false, e.getMessage()));
    }

    // ---------- إبلاغ ----------

    public static void report(String postId, String reporterUid, String reason, Callback callback) {
        Map<String, Object> report = new HashMap<>();
        report.put("postId", postId);
        report.put("reporterUid", reporterUid);
        report.put("reason", reason);
        report.put("timestamp", System.currentTimeMillis());
        db().collection("reports").add(report)
                .addOnSuccessListener(ref -> callback.onResult(true, null))
                .addOnFailureListener(e -> callback.onResult(false, e.getMessage()));
    }

    // ---------- متابعة ----------

    private static String followDocId(String followerUid, String followingUid) {
        return followerUid + "_" + followingUid;
    }

    public static void follow(String myUid, String targetUid, Callback callback) {
        Map<String, Object> follow = new HashMap<>();
        follow.put("followerUid", myUid);
        follow.put("followingUid", targetUid);
        follow.put("timestamp", System.currentTimeMillis());

        db().collection("follows").document(followDocId(myUid, targetUid)).set(follow)
                .addOnSuccessListener(unused -> {
                    db().collection("users").document(myUid).update("followingCount", FieldValue.increment(1));
                    db().collection("users").document(targetUid).update("followersCount", FieldValue.increment(1))
                            .addOnSuccessListener(u -> callback.onResult(true, null))
                            .addOnFailureListener(e -> callback.onResult(false, e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onResult(false, e.getMessage()));
    }

    public static void unfollow(String myUid, String targetUid, Callback callback) {
        db().collection("follows").document(followDocId(myUid, targetUid)).delete()
                .addOnSuccessListener(unused -> {
                    db().collection("users").document(myUid).update("followingCount", FieldValue.increment(-1));
                    db().collection("users").document(targetUid).update("followersCount", FieldValue.increment(-1))
                            .addOnSuccessListener(u -> callback.onResult(true, null))
                            .addOnFailureListener(e -> callback.onResult(false, e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onResult(false, e.getMessage()));
    }

    /** يتحقق هل myUid يتابع targetUid حالياً. */
    public static void isFollowing(String myUid, String targetUid, BoolCallback onResult) {
        db().collection("follows").document(followDocId(myUid, targetUid)).get()
                .addOnSuccessListener(snapshot -> onResult.onResult(snapshot.exists()))
                .addOnFailureListener(e -> onResult.onResult(false));
    }
}
