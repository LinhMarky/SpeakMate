package com.example.speakmate;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView navigation;
    private boolean selectingTab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        navigation = findViewById(R.id.bottomNavigation);

        WindowCompat.getInsetsController(
                getWindow(),
                getWindow().getDecorView()
        ).setAppearanceLightStatusBars(false);

        WindowCompat.getInsetsController(
                getWindow(),
                getWindow().getDecorView()
        ).setAppearanceLightNavigationBars(false);

        View main = findViewById(R.id.main);

        ViewCompat.setOnApplyWindowInsetsListener(main, (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
            );

            Insets keyboard = insets.getInsets(
                    WindowInsetsCompat.Type.ime()
            );

            int bottomPadding;

            if (navigation.getVisibility() == View.VISIBLE) {
                // BottomNavigationView xử lý phần thanh hệ thống.
                bottomPadding = Math.max(
                        0,
                        keyboard.bottom - bars.bottom
                );
            } else {
                bottomPadding = Math.max(
                        bars.bottom,
                        keyboard.bottom
                );
            }

            view.setPadding(
                    bars.left,
                    bars.top,
                    bars.right,
                    bottomPadding
            );

            return insets;
        });

        // MainActivity quản lý việc ẩn/hiện thanh điều hướng.
        getSupportFragmentManager()
                .registerFragmentLifecycleCallbacks(
                        new FragmentManager.FragmentLifecycleCallbacks() {

                            @Override
                            public void onFragmentResumed(
                                    @NonNull FragmentManager manager,
                                    @NonNull Fragment fragment
                            ) {
                                if (fragment.getId()
                                        == R.id.fragmentContainer) {
                                    updateNavigation(fragment);
                                }
                            }
                        },
                        false
                );

        navigation.setOnItemSelectedListener(item -> {
            if (!selectingTab) {
                showTopLevel(item.getItemId());
            }

            return true;
        });

        navigation.setOnItemReselectedListener(item -> {
            if (!selectingTab) {
                showTopLevel(item.getItemId());
            }
        });

        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {

                    @Override
                    public void handleOnBackPressed() {
                        FragmentManager manager =
                                getSupportFragmentManager();

                        if (manager.isStateSaved()) {
                            return;
                        }

                        Fragment current = manager.findFragmentById(
                                R.id.fragmentContainer
                        );

                        if (current instanceof ChatFragment) {
                            ((ChatFragment) current)
                                    .confirmEndSession();

                        } else if (current
                                instanceof SpeakingResultFragment) {
                            navigateToTab(R.id.nav_speaking);

                        } else if (manager.getBackStackEntryCount() > 0) {
                            manager.popBackStackImmediate();

                        } else if (!(current instanceof HomeFragment)) {
                            navigateToTab(R.id.nav_home);

                        } else {
                            finish();
                        }
                    }
                }
        );

        if (savedInstanceState == null) {
            navigateToTab(R.id.nav_home);
        }

        ViewCompat.requestApplyInsets(main);
    }

    public void navigateToTab(int itemId) {
        if (getSupportFragmentManager().isStateSaved()) {
            return;
        }

        selectingTab = true;
        navigation.setSelectedItemId(itemId);
        selectingTab = false;

        showTopLevel(itemId);
    }

    private void showTopLevel(int itemId) {
        FragmentManager manager = getSupportFragmentManager();

        if (manager.isStateSaved()) {
            return;
        }

        Fragment next;

        if (itemId == R.id.nav_home) {
            next = new HomeFragment();

        } else if (itemId == R.id.nav_speaking) {
            next = new SpeakingFragment();

        } else if (itemId == R.id.nav_flashcards) {
            next = new FlashcardsFragment();

        } else if (itemId == R.id.nav_profile) {
            next = PlaceholderFragment.newInstance("Cá nhân");

        } else {
            return;
        }

        hideKeyboard();

        manager.popBackStackImmediate(
                null,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
        );

        manager.beginTransaction()
                .replace(R.id.fragmentContainer, next)
                .commitNow();

        updateNavigation(next);
    }

    public void showSpeakingResult(
            String level,
            String topic,
            int count
    ) {
        FragmentManager manager = getSupportFragmentManager();

        if (manager.isStateSaved()) {
            return;
        }

        hideKeyboard();

        // Gỡ phiên chat khỏi lịch sử điều hướng trước khi tổng kết.
        manager.popBackStackImmediate(
                null,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
        );

        SpeakingResultFragment result =
                SpeakingResultFragment.newInstance(
                        level,
                        topic,
                        count
                );

        manager.beginTransaction()
                .replace(R.id.fragmentContainer, result)
                .commitNow();

        updateNavigation(result);
    }

    private void hideKeyboard() {
        View root = findViewById(R.id.main);

        WindowCompat.getInsetsController(getWindow(), root)
                .hide(WindowInsetsCompat.Type.ime());

        View focused = getCurrentFocus();

        if (focused != null) {
            focused.clearFocus();
        }
    }

    private void updateNavigation(Fragment fragment) {
        boolean fullScreen =
                fragment instanceof ChatFragment
                        || fragment instanceof SpeakingResultFragment
                        || fragment instanceof StudyFragment
                        || fragment instanceof CreateDeckFragment;

        navigation.setVisibility(
                fullScreen ? View.GONE : View.VISIBLE
        );

        ViewCompat.requestApplyInsets(
                findViewById(R.id.main)
        );
    }
}