package com.dlofpkg.massage.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * محرّك تخصيص واجهة MyDoc، مبني على ثلاثة ملفات موثَّقة في mydoc.md:
 *
 *  - assets/re.ui.css : الألوان الافتراضية للتطبيق (صيغة شبيهة بـCSS، أزواج
 *    "--اسم-المتغيّر: قيمة؛").
 *  - assets/io.yml    : إعدادات تشغيل عامة (تفعيل/تعطيل ميزات، اسم افتراضي...).
 *  - حقل "customPrimaryColor" داخل مستند المستخدم في Firestore: تخصيص شخصي
 *    اختياري يتجاوز اللون الافتراضي في re.ui.css، ويُضبط من صفحة الحساب ←
 *    الإعدادات ← حاوية التخصيص (ProfileActivity).
 *
 * التطبيق يبقى Native Android (Java + XML)، لذا re.ui.css/io.yml لا يُشغَّلان
 * كملفات ويب فعلية؛ هما ببساطة صيغة تخزين بسيطة يفهمها هذا الصنف فقط،
 * ليسهل على أي شخص تعديل الثيم الافتراضي بدون لمس الكود.
 */
public class ThemeManager {

    private static final String CSS_ASSET = "re.ui.css";
    private static final String YML_ASSET = "io.yml";

    private static Map<String, String> cssCache;
    private static Map<String, String> ymlCache;

    /** أسماء ألوان جاهزة يقدر المستخدم يختار منها في حاوية التخصيص. */
    public static final Map<String, Integer> SWATCHES = new HashMap<>();
    static {
        SWATCHES.put("red", Color.parseColor("#FF0033"));
        SWATCHES.put("blue", Color.parseColor("#58A6FF"));
        SWATCHES.put("green", Color.parseColor("#3FB950"));
        SWATCHES.put("purple", Color.parseColor("#A371F7"));
        SWATCHES.put("orange", Color.parseColor("#FF8C42"));
    }

    private ThemeManager() {}

    /** يقرأ re.ui.css مرة واحدة فقط ويخزّنه مؤقتاً في الذاكرة. */
    private static Map<String, String> readCss(Context context) {
        if (cssCache != null) return cssCache;
        cssCache = parseKeyValueAsset(context, CSS_ASSET, ":", ";", "--");
        return cssCache;
    }

    /** يقرأ io.yml مرة واحدة فقط (هذا هو "تشغيل io.yml" المطلوب: يُحمَّل ويُطبَّق عند كل Activity). */
    public static Map<String, String> runIo(Context context) {
        if (ymlCache != null) return ymlCache;
        ymlCache = parseKeyValueAsset(context, YML_ASSET, ":", null, null);
        return ymlCache;
    }

    private static Map<String, String> parseKeyValueAsset(Context context, String assetName,
                                                            String separator, String trimTrailing, String stripPrefix) {
        Map<String, String> result = new HashMap<>();
        try (InputStream is = context.getAssets().open(assetName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            boolean inBlockComment = false;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("/*")) { inBlockComment = true; }
                if (inBlockComment) {
                    if (trimmed.endsWith("*/")) inBlockComment = false;
                    continue;
                }
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue;
                int idx = trimmed.indexOf(separator);
                if (idx < 0) continue;
                String key = trimmed.substring(0, idx).trim();
                String value = trimmed.substring(idx + 1).trim();
                if (trimTrailing != null && value.endsWith(trimTrailing)) {
                    value = value.substring(0, value.length() - trimTrailing.length()).trim();
                }
                if (stripPrefix != null && key.startsWith(stripPrefix)) {
                    key = key.substring(stripPrefix.length());
                }
                result.put(key, value);
            }
        } catch (IOException e) {
            // إن تعذّرت قراءة الأصول لأي سبب، نستمر بالألوان الافتراضية المبرمَجة في colors.xml
        }
        return result;
    }

    /** اللون الأساسي الافتراضي من re.ui.css (بدون أي تخصيص شخصي). */
    public static int getDefaultPrimaryColor(Context context) {
        Map<String, String> css = readCss(context);
        String hex = css.get("primary");
        try {
            return hex != null ? Color.parseColor(hex) : Color.parseColor("#FF0033");
        } catch (IllegalArgumentException e) {
            return Color.parseColor("#FF0033");
        }
    }

    /** هل ميزة معيّنة مفعّلة حسب io.yml (مثال: feature_likes، feature_follow...). */
    public static boolean isFeatureEnabled(Context context, String featureKey) {
        Map<String, String> io = runIo(context);
        String value = io.get(featureKey);
        return value == null || value.equalsIgnoreCase("true");
    }

    /**
     * يطبّق التخصيص الحالي (الشخصي إن وجد، وإلا الافتراضي من re.ui.css) على
     * أهم عناصر الواجهة في أي Activity: الأزرار الأساسية وشريط التنقّل السفلي
     * وزر النشر العائم. يُستدعى مرة في onCreate بعد إعداد الـ views.
     */
    public static void applyToActivity(Activity activity) {
        runIo(activity); // "تشغيل io.yml"
        int defaultColor = getDefaultPrimaryColor(activity);

        String uid = SessionManager.getUid();
        if (uid == null) {
            applyColor(activity, defaultColor);
            return;
        }

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    String customHex = snapshot.getString("customPrimaryColor");
                    int color = defaultColor;
                    if (customHex != null && !customHex.isEmpty()) {
                        try {
                            color = Color.parseColor(customHex);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    applyColor(activity, color);
                })
                .addOnFailureListener(e -> applyColor(activity, defaultColor));
    }

    private static void applyColor(Activity activity, int color) {
        if (activity == null || activity.isFinishing()) return;

        FloatingActionButton fab = activity.findViewById(com.dlofpkg.massage.R.id.fabNewPost);
        if (fab != null) {
            fab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        }

        BottomNavigationView bottomNav = activity.findViewById(com.dlofpkg.massage.R.id.bottomNav);
        if (bottomNav != null) {
            android.content.res.ColorStateList tint = new android.content.res.ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_checked}
                    },
                    new int[]{color, activity.getResources().getColor(com.dlofpkg.massage.R.color.text_gray)});
            bottomNav.setItemIconTintList(tint);
            bottomNav.setItemTextColor(tint);
        }
    }

    /** يحفظ اختيار المستخدم للون الأساسي في حسابه (مستخدَم من حاوية التخصيص في صفحة الحساب). */
    public static void saveCustomColor(String uid, String hexColor, Runnable onDone) {
        Map<String, Object> update = new HashMap<>();
        update.put("customPrimaryColor", hexColor);
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(update, com.google.firebase.firestore.SetOptions.merge())
                .addOnCompleteListener(task -> {
                    if (onDone != null) onDone.run();
                });
    }
}
