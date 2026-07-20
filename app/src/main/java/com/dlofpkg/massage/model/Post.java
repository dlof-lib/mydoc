package com.dlofpkg.massage.model;

/**
 * يمثل منشوراً في المنصة: مقال، نص عادي، أو ملف كود.
 * يُخزَّن في مجموعة "posts" داخل Firestore. الملف المرفق (إن وجد)
 * يُرسل كنص Base64 مباشرة داخل المستند، بدون استخدام Firebase Storage.
 */
public class Post {

    public static final String TYPE_ARTICLE = "article";
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_CODE = "code";

    private String id;
    private String authorUid;       // uid صاحب المنشور من Firebase Auth، تُستخدم في قواعد الأمان
    private String authorUsername;  // اسم يُعرض للمستخدمين
    private String title;
    private String type;            // article | text | code
    private String content;         // نص المقال أو الكود نفسه
    private String fileBase64;      // الملف المرفق كنص Base64
    private String fileName;        // اسم الملف الأصلي (مثال: main.py)
    private long likesCount;        // عدد الإعجابات
    private long repostsCount;      // عدد مرات إعادة النشر
    private String repostOfId;      // معرّف المنشور الأصلي إن كان هذا "إعادة نشر"
    private String repostOfAuthor;  // اسم صاحب المنشور الأصلي (للعرض)
    private long timestamp;

    public Post() {
        // مطلوب من Firestore
    }

    public Post(String authorUid, String authorUsername, String title, String type, String content) {
        this.authorUid = authorUid;
        this.authorUsername = authorUsername;
        this.title = title;
        this.type = type;
        this.content = content;
        this.fileBase64 = "";
        this.fileName = "";
        this.likesCount = 0;
        this.repostsCount = 0;
        this.repostOfId = "";
        this.repostOfAuthor = "";
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAuthorUid() { return authorUid; }
    public void setAuthorUid(String authorUid) { this.authorUid = authorUid; }

    public String getAuthorUsername() { return authorUsername; }
    public void setAuthorUsername(String authorUsername) { this.authorUsername = authorUsername; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getFileBase64() { return fileBase64; }
    public void setFileBase64(String fileBase64) { this.fileBase64 = fileBase64; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public long getLikesCount() { return likesCount; }
    public void setLikesCount(long likesCount) { this.likesCount = likesCount; }

    public long getRepostsCount() { return repostsCount; }
    public void setRepostsCount(long repostsCount) { this.repostsCount = repostsCount; }

    public String getRepostOfId() { return repostOfId; }
    public void setRepostOfId(String repostOfId) { this.repostOfId = repostOfId; }

    public String getRepostOfAuthor() { return repostOfAuthor; }
    public void setRepostOfAuthor(String repostOfAuthor) { this.repostOfAuthor = repostOfAuthor; }
}
