# منصة المبرمجين (DevPlatform)

تطبيق أندرويد بسيط (Java + XML) لنشر المقالات والنصوص وملفات الكود، مبني على Firebase Firestore.

## البنية
```
app/src/main/java/com/dlofpkg/massage/
  model/    User.java, Post.java
  util/     PasswordUtils.java, SessionManager.java, Base64Utils.java
  ui/       LoginActivity, RegisterActivity, MainActivity,
            NewPostActivity, PostDetailActivity, ProfileActivity
app/src/main/res/layout/   تخطيطات XML لكل شاشة
firestore.rules            قواعد أمان Firestore
.github/workflows/android-build.yml   بناء تلقائي عبر GitHub Actions
```

## كيف يعمل تسجيل الدخول الآن
- **Firebase Authentication** (بريد/كلمة مرور) هو المسؤول الفعلي عن المصادقة.
- بما أنك طلبت تجربة "ID + كلمة مرور" بدون بريد إلكتروني حقيقي، نبني داخلياً
  بريداً وهمياً من اسم المستخدم بالشكل: `username@mydoc-users.app`
  (`AuthUtils.usernameToEmail`). المستخدم لا يرى ولا يحتاج معرفة هذا البريد.
- كلمة المرور لم تعد تُخزَّن أو تُشفَّر يدوياً في التطبيق إطلاقاً — Firebase
  يتولى ذلك بشكل احترافي (hashing + salt) على خوادمه.
- معرّف مستند المستخدم في Firestore أصبح **uid** الحقيقي القادم من Firebase
  Auth (وليس اسم المستخدم كنص)، وهذا ما يسمح لقواعد `firestore.rules` بالتحقق
  الحقيقي من الهوية عبر `request.auth.uid` — أي أن مستخدماً لا يقدر يعدّل
  بيانات مستخدم آخر أو ينشر منشوراً باسم شخص غيره، حتى لو حاول التلاعب
  بالطلبات مباشرة خارج التطبيق.
- الصور والملفات ما زالت تُرسل كـ **Base64 مباشرة داخل Firestore** كما طلبت
  في البداية (لم نستخدم Firebase Storage).

## الصور والملفات كـ Base64
- صورة البروفايل تُضغط تلقائياً (JPEG 80%، أقصى بعد 256px) ثم تُرسل كنص Base64
  إلى حقل `iconBase64` في مستند المستخدم — بدون استخدام Firebase Storage.
- ملفات الكود المرفقة بالمنشورات تُرسل بنفس الطريقة إلى حقل `fileBase64`.
- **حد Firestore**: حجم أي مستند لا يتجاوز 1 ميجابايت تقريباً، لذا الكود يمنع
  رفع ملفات أكبر من ~700 كيلوبايت (`Base64Utils.MAX_FILE_SIZE_BYTES`).

## Firestore المجاني مقابل المدفوع
هذه نقطة يجب توضيحها: خطة **Spark (مجانية)** أو **Blaze (مدفوعة)** هي إعداد
على مستوى **مشروع Firebase بالكامل** من console.firebase.google.com، وليست
شيئاً يتحكم به كود التطبيق. أما حقل `paid` في مستند المستخدم (`User.isPaid`)
فهو نظام اشتراك **داخل تطبيقك أنت** (مثلاً: مستخدم مدفوع يحصل على ميزات إضافية)
— لم أربطه بأي بوابة دفع فعلية (Google Play Billing مثلاً)؛ حالياً هو مجرد
حقل بيانات جاهز لتُبنى عليه لاحقاً.

## ملاحظة عن ملف google-services.json
الملف المستخدم هنا يخص مشروع Firebase باسم "dlof-massage2026" وباكج
"com.dlofpkg.massage" — يبدو أنه لمشروع مختلف (تدليك) وليس لمنصة المبرمجين.
التطبيق سيعمل به لأن الكود يستخدم applicationId مطابق، لكن يُفضّل إنشاء
مشروع Firebase جديد بالاسم المناسب من console.firebase.google.com وتنزيل
ملف google-services.json جديد يخص منصتك، ثم استبداله في app/google-services.json
وتعديل applicationId و namespace في app/build.gradle ليطابقا الباكج الجديد.

## التشغيل محلياً
1. افتح المجلد في Android Studio.
2. تأكد من وجود `app/google-services.json` (موجود بالفعل).
3. من Firebase Console → Authentication → Sign-in method، فعّل مزوّد
   **Email/Password** (معطّل افتراضياً في أي مشروع جديد).
4. فعّل Cloud Firestore من Firebase Console وطبّق محتوى `firestore.rules`.
5. شغّل التطبيق على جهاز/محاكي.

## البناء عبر GitHub Actions
عند رفع المشروع إلى مستودع GitHub، سيبني workflow الموجود في
`.github/workflows/android-build.yml` نسخة Debug تلقائياً عند كل push
إلى main، ويرفعها كـ Artifact قابل للتنزيل من تبويب Actions.
