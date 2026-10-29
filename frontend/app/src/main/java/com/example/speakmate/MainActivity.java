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
        navigation.setItemActiveIndicatorEnabled(false);

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

                            Bundle arguments = current.getArguments();

                            boolean fromHistory = arguments != null
                                    && arguments.getBoolean("fromHistory", false);

                            if (fromHistory
                                    && manager.getBackStackEntryCount() > 0) {
                                manager.popBackStackImmediate();
                            } else {
                                navigateToTab(R.id.nav_speaking);
                            }

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

        Fragment current =
                manager.findFragmentById(R.id.fragmentContainer);

        // Tránh xử lý kết thúc hai lần liên tiếp.
        if (current instanceof SpeakingResultFragment) {
            return;
        }

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

        String sessionLevel =
                level == null || level.trim().isEmpty()
                        ? "A2"
                        : level.trim();

        String sessionTopic =
                topic == null || topic.trim().isEmpty()
                        ? "Chủ đề luyện nói"
                        : topic.trim();

        java.util.ArrayList<String> transcript =
                new java.util.ArrayList<>(messages);

        boolean[] roles = userRoles.clone();

        for (String message : transcript) {
            if (message == null) {
                android.widget.Toast.makeText(
                        this,
                        "Dữ liệu hội thoại không hợp lệ.",
                        android.widget.Toast.LENGTH_SHORT
                ).show();
                return;
            }
        }

        String sessionId =
                java.util.UUID.randomUUID().toString();

        long finishedAt = System.currentTimeMillis();

        SpeakingSession session = new SpeakingSession(
                sessionId,
                sessionLevel,
                sessionTopic,
                finishedAt,
                transcript,
                roles
        );

        boolean historySaved = false;

        try {
            new SpeakingHistoryStore(this).saveSession(session);
            historySaved = true;

        } catch (IllegalStateException exception) {
            // Vẫn cho xem tổng kết nếu lưu lịch sử thất bại.
            android.widget.Toast.makeText(
                    this,
                    "Chưa lưu được lịch sử. Bạn vẫn có thể "
                            + "xem tổng kết của phiên này.",
                    android.widget.Toast.LENGTH_LONG
            ).show();
        }

        SpeakingResultFragment result =
                SpeakingResultFragment.newInstance(
                        session.getLevel(),
                        session.getTopic(),
                        session.getUserTurnCount()
                );

        Bundle arguments = result.requireArguments();

        arguments.putStringArrayList(
                "transcriptMessages",
                session.getMessages()
        );

        arguments.putBooleanArray(
                "transcriptUserRoles",
                session.getUserRoles()
        );

        arguments.putString("historySessionId", sessionId);
        arguments.putLong("sessionFinishedAt", finishedAt);
        arguments.putBoolean("historySaved", historySaved);
        arguments.putBoolean("fromHistory", false);

        hideKeyboard();

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

    public void openSavedSpeakingSession(String sessionId) {
        FragmentManager manager = getSupportFragmentManager();

        if (manager.isStateSaved()) {
            return;
        }

        Fragment current =
                manager.findFragmentById(R.id.fragmentContainer);

        if (current instanceof SpeakingResultFragment) {
            return;
        }

        SpeakingSession session;

        try {
            session = new SpeakingHistoryStore(this)
                    .getSessionById(sessionId);

        } catch (IllegalStateException exception) {
            android.widget.Toast.makeText(
                    this,
                    "Chưa đọc được lịch sử luyện tập.",
                    android.widget.Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (session == null) {
            android.widget.Toast.makeText(
                    this,
                    "Phiên luyện không còn trong lịch sử.",
                    android.widget.Toast.LENGTH_SHORT
            ).show();
            return;
        }

        SpeakingResultFragment result =
                SpeakingResultFragment.newInstance(
                        session.getLevel(),
                        session.getTopic(),
                        session.getUserTurnCount()
                );

        Bundle arguments = result.requireArguments();

        arguments.putStringArrayList(
                "transcriptMessages",
                session.getMessages()
        );

        arguments.putBooleanArray(
                "transcriptUserRoles",
                session.getUserRoles()
        );

        arguments.putString(
                "historySessionId",
                session.getId()
        );

        arguments.putLong(
                "sessionFinishedAt",
                session.getFinishedAt()
        );

        arguments.putBoolean("historySaved", true);
        arguments.putBoolean("fromHistory", true);

        hideKeyboard();

        // Không gọi saveSession khi chỉ xem lại.
        manager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragmentContainer, result)
                .addToBackStack("history_result")
                .commit();
    }

    public void openSpeakingHistory() {
        FragmentManager manager = getSupportFragmentManager();

        if (manager.isStateSaved()) {
            return;
        }

        Fragment current =
                manager.findFragmentById(R.id.fragmentContainer);

        if (current instanceof SpeakingHistoryFragment) {
            return;
        }

        hideKeyboard();

        manager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.fragmentContainer,
                        new SpeakingHistoryFragment()
                )
                .addToBackStack("speaking_history")
                .commit();
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
                        || fragment instanceof RegisterFragment
                        || fragment instanceof GenerateDeckFragment
                        || fragment instanceof GeneratedDeckReviewFragment
                        || fragment instanceof SpeakingHistoryFragment;

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