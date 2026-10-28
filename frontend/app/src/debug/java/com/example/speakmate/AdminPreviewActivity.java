package com.example.speakmate;

import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class AdminPreviewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout container = new FrameLayout(this);

        container.setId(R.id.fragmentContainer);

        container.setBackgroundColor(
                ContextCompat.getColor(
                        this,
                        R.color.sm_background
                )
        );

        container.setLayoutParams(
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        // Chừa khoảng trống cho thanh trạng thái, camera và thanh điều hướng.
        ViewCompat.setOnApplyWindowInsetsListener(
                container,
                (view, insets) -> {
                    Insets safeArea = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                                    | WindowInsetsCompat.Type.displayCutout()
                    );
                    Insets keyboard = insets.getInsets(
                            WindowInsetsCompat.Type.ime()
                    );

                    int bottomInset = Math.max(
                            safeArea.bottom,
                            keyboard.bottom
                    );

                    view.setPadding(
                            safeArea.left,
                            safeArea.top,
                            safeArea.right,
                            bottomInset
                    );

                    return WindowInsetsCompat.CONSUMED;
                }
        );

        setContentView(container);
        ViewCompat.requestApplyInsets(container);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(
                            R.id.fragmentContainer,
                            new AdminDashboardFragment()
                    )
                    .commit();
        }
    }
}