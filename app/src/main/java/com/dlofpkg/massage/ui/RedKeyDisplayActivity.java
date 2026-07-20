package com.dlofpkg.massage.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dlofpkg.massage.R;

/**
 * تعرض المفتاح الأحمر للمستخدم مرة واحدة فقط مع تحذيرات واضحة،
 * وتُلزمه بتأكيد أنه حفظه قبل المتابعة.
 */
public class RedKeyDisplayActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_red_key_display);

        // منع أخذ لقطة شاشة أو ظهور المفتاح في قائمة التطبيقات الأخيرة (طبقة حماية إضافية)
        getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);

        String redKey = getIntent().getStringExtra("red_key");

        TextView tvRedKey = findViewById(R.id.tvRedKey);
        Button btnCopy = findViewById(R.id.btnCopy);
        CheckBox cbConfirmed = findViewById(R.id.cbConfirmed);
        Button btnContinue = findViewById(R.id.btnContinue);

        tvRedKey.setText(redKey);

        btnCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("red_key", redKey));
            Toast.makeText(this, "تم نسخ المفتاح", Toast.LENGTH_SHORT).show();
        });

        cbConfirmed.setOnCheckedChangeListener((buttonView, isChecked) ->
                btnContinue.setEnabled(isChecked));

        btnContinue.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        // نمنع الرجوع بدون تأكيد الحفظ حتى لا يفوّت المستخدم المفتاح بالخطأ
        Toast.makeText(this, "الرجاء تأكيد حفظ المفتاح أولاً بالضغط على المربع أدناه", Toast.LENGTH_SHORT).show();
    }
}
