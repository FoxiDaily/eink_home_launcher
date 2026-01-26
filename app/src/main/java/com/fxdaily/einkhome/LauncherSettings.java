package com.fxdaily.einkhome;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Switch;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class LauncherSettings extends AppCompatActivity {
    
    private static final String PREFS_NAME = "LauncherPrefs";
    private static final String KEY_COLOR_ICONS = "color_icons";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_launcher_settings);
        
        View root = findViewById(R.id.settings_root);
        if (root != null) {
            int initialLeft = root.getPaddingLeft();
            int initialTop = root.getPaddingTop();
            int initialRight = root.getPaddingRight();
            int initialBottom = root.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                    initialLeft + systemBars.left,
                    initialTop + systemBars.top,
                    initialRight + systemBars.right,
                    initialBottom + systemBars.bottom
                );
                return WindowInsetsCompat.CONSUMED;
            });
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Switch colorSwitch = findViewById(R.id.switch_color_icons);
        
        boolean isColor = prefs.getBoolean(KEY_COLOR_ICONS, false);
        colorSwitch.setChecked(isColor);
        
        colorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_COLOR_ICONS, isChecked).apply();
        });
    }
}
