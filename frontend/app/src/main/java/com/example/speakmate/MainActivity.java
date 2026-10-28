package com.example.speakmate;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

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
    private View mainView;

    private boolean selectingTab;
    private boolean keyboardVisible;
    private boolean fullScreenMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        );

        setContentView(R.layout.activity_main);

        mainView = findViewById(R.id.main);
        navigation = findViewById(R.id.bottomNavigation);

        WindowCompat.getInsetsController(
                getWindow(),
                getWindow().getDecorView()
        ).setAppearanceLightStatusBars(false);

        WindowCompat.getInsetsController(
                getWindow(),
                getWindow().getDecorView()
        ).setAppearanceLightNavigationBars(false);

        setupWindowInsets();
        setupFragmentCallbacks();
        setupNavigation();
        setupBackNavigation();

        if (savedInstanceState == null) {
            navigateToTab(R.id.nav_home);
        } else {
            Fragment current = getSupportFragmentManager()
                    .findFragmentById(R.id.fragmentContainer);

            updateNavigation(current);
        }

        ViewCompat.requestApplyInsets(mainView);
    }

    private void setupWindowInsets() {
        /*
         * MainActivity xử lý khoảng trống hệ thống.
         * Không để BottomNavigationView cộng thêm padding.
         */
        navigation.setPadding(0, 0, 0, 0);

        ViewCompat.setOnApplyWindowInsetsListener(
                navigation,
                (view, insets) -> WindowInsetsCompat.CONSUMED
        );

        ViewCompat.setOnApplyWindowInsetsListener(
                mainView,
                (view, insets) -> {
                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                                    | WindowInsetsCompat.Type.displayCutout()
                    );

                    Insets keyboard = insets.getInsets(
                            WindowInsetsCompat.Type.ime()
                    );

                    keyboardVisible = insets.isVisible(
                            WindowInsetsCompat.Type.ime()
                    );

                    applyNavigationVisibility();

                    int bottomPadding = keyboardVisible
                            ? Math.max(bars.bottom, keyboard.bottom)
                            : bars.bottom;

                    if (view.getPaddingLeft() != bars.left
                            || view.getPaddingTop() != bars.top
                            || view.getPaddingRight() != bars.right
                            || view.getPaddingBottom() != bottomPadding) {

                        view.setPadding(
                                bars.left,
                                bars.top,
                                bars.right,
                                bottomPadding
                        );
                    }

                    /*
                     * Khoảng trống đã được xử lý ở màn hình gốc.
                     * Không truyền xuống để các view con cộng lần nữa.
                     */
                    return WindowInsetsCompat.CONSUMED;
                }
        );
    }

    private void setupFragmentCallbacks() {
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
    }

    private void setupNavigation() {
        navigation.setOnItemSelectedListener(item -> {
            if (selectingTab) {
                return true;
            }

            if (getSupportFragmentManager().isStateSaved()) {
                return false;
            }

            showTopLevel(item.getItemId());

            return true;
        });

        navigation.setOnItemReselectedListener(item -> {
            if (!selectingTab
                    && !getSupportFragmentManager().isStateSaved()) {
                showTopLevel(item.getItemId());
            }
        });
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {

                    @Override
                    public void handleOnBackPressed() {
                        /*
                         * Nếu callback nhận Back trong khi bàn phím mở,
                         * đóng bàn phím trước khi chuyển màn hình.
                         */
                        if (keyboardVisible) {
                            hideKeyboard();
                            return;
                        }

                        FragmentManager manager =
                                getSupportFragmentManager();

                        if (manager.isStateSaved()) {
                            return;
                        }

                        Fragment current =
                                manager.findFragmentById(
                                        R.id.fragmentContainer
                                );

                        if (current instanceof ChatFragment) {
                            ((ChatFragment) current)
                                    .confirmEndSession();

                        } else if (current
                                instanceof SpeakingResultFragment) {

                            navigateToTab(R.id.nav_speaking);

                        } else if (
                                manager.getBackStackEntryCount() > 0
                        ) {
                            manager.popBackStackImmediate();

                        } else if (
                                !(current instanceof HomeFragment)
                        ) {
                            navigateToTab(R.id.nav_home);

                        } else {
                            finish();
                        }
                    }
                }
        );
    }

    public void navigateToTab(int itemId) {
        if (getSupportFragmentManager().isStateSaved()) {
            return;
        }

        selectingTab = true;

        try {
            navigation.setSelectedItemId(itemId);
        } finally {
            selectingTab = false;
        }

        showTopLevel(itemId);
    }

    private void showTopLevel(int itemId) {
        FragmentManager manager =
                getSupportFragmentManager();

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
            next = new ProfileFragment();

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
        FragmentManager manager =
                getSupportFragmentManager();

        if (manager.isStateSaved()) {
            return;
        }

        hideKeyboard();

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

    public void showSpeakingResult(
            String level,
            String topic,
            java.util.ArrayList<String> messages,
            boolean[] userRoles
    ) {
        FragmentManager manager = getSupportFragmentManager();

        if (manager.isStateSaved()) {
            return;
        }

        // Kiểm tra dữ liệu trước khi thay đổi màn hình.
        if (messages == null
                || userRoles == null
                || messages.size() != userRoles.length) {
            android.widget.Toast.makeText(
                    this,
                    "Dữ liệu hội thoại không hợp lệ.",
                    android.widget.Toast.LENGTH_SHORT
            ).show();
            return;
        }

        java.util.ArrayList<String> transcript =
                new java.util.ArrayList<>(messages);

        boolean[] roles = userRoles.clone();

        int userTurnCount = 0;

        for (int i = 0; i < transcript.size(); i++) {
            String message = transcript.get(i);

            if (message == null) {
                android.widget.Toast.makeText(
                        this,
                        "Dữ liệu hội thoại không hợp lệ.",
                        android.widget.Toast.LENGTH_SHORT
                ).show();
                return;
            }

            if (roles[i] && !message.trim().isEmpty()) {
                userTurnCount++;
            }
        }

        SpeakingResultFragment result =
                SpeakingResultFragment.newInstance(
                        level,
                        topic,
                        userTurnCount
                );

        Bundle arguments = result.requireArguments();

        arguments.putStringArrayList(
                "transcriptMessages",
                transcript
        );

        arguments.putBooleanArray(
                "transcriptUserRoles",
                roles
        );

        hideKeyboard();

        // Gỡ màn hình chat để Back từ tổng kết không mở lại phiên đã kết thúc.
        manager.popBackStackImmediate(
                null,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
        );

        manager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragmentContainer, result)
                .commitNow();

        updateNavigation(result);
    }

    public void openLogin() {
        FragmentManager manager = getSupportFragmentManager();

        if (manager.isStateSaved()) {
            return;
        }

        Fragment current =
                manager.findFragmentById(R.id.fragmentContainer);

        // Tránh mở chồng nhiều màn hình đăng nhập.
        if (current instanceof LoginFragment
                || current instanceof RegisterFragment) {
            return;
        }

        hideKeyboard();

        manager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.fragmentContainer,
                        new LoginFragment()
                )
                .addToBackStack("login")
                .commit();
    }

    private void hideKeyboard() {
        if (mainView == null) {
            return;
        }

        WindowCompat.getInsetsController(
                getWindow(),
                mainView
        ).hide(WindowInsetsCompat.Type.ime());

        View focused = getCurrentFocus();

        if (focused != null) {
            focused.clearFocus();
        }

        /*
         * Không gán keyboardVisible = false ở đây.
         * Chờ insets xác nhận bàn phím đã đóng.
         */
    }

    private void updateNavigation(Fragment fragment) {
        fullScreenMode =
                fragment instanceof ChatFragment
                        || fragment instanceof SpeakingResultFragment
                        || fragment instanceof StudyFragment
                        || fragment instanceof CreateDeckFragment
                        || fragment instanceof LearnFragment
                        || fragment instanceof MatchFragment
                        || fragment instanceof LoginFragment
                        || fragment instanceof RegisterFragment;

        applyNavigationVisibility();

        if (mainView != null) {
            ViewCompat.requestApplyInsets(mainView);
        }
    }

    private void applyNavigationVisibility() {
        if (navigation == null) {
            return;
        }

        boolean shouldHide = keyboardVisible || fullScreenMode;

        int visibility = shouldHide
                ? View.GONE
                : View.VISIBLE;

        if (navigation.getVisibility() != visibility) {
            navigation.setVisibility(visibility);
        }
    }
}