package com.example.speakmate;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.speakmate.auth.AuthSession;
import com.example.speakmate.auth.SessionManager;
import com.example.speakmate.ui.NetworkFragment;

import java.util.List;
import java.util.Locale;

public class ProfileFragment extends NetworkFragment {

    private int statisticsRequest;

    @Override
    protected boolean requiresAuthentication() {
        return false;
    }

    private static final String PREFS_NAME = "speakmate_profile";

    private static final String KEY_NAME = "display_name";
    private static final String KEY_LEVEL = "practice_level";
    private static final String KEY_GOAL = "daily_goal_minutes";

    private static final String[] LEVELS = {
            "A1", "A2", "B1", "B2", "C1", "C2"
    };

    private static final int[] GOALS = {
            5, 10, 15, 20, 30
    };

    public ProfileFragment() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btnProfileSpeakingHistory)
                .setOnClickListener(button -> {
                    if (!isAdded()
                            || !isResumed()
                            || getParentFragmentManager().isStateSaved()) {
                        return;
                    }

                    WindowCompat.getInsetsController(
                            requireActivity().getWindow(),
                            view
                    ).hide(WindowInsetsCompat.Type.ime());

                    View focused = requireActivity().getCurrentFocus();

                    if (focused != null) {
                        focused.clearFocus();
                    }

                    ((MainActivity) requireActivity())
                            .openSpeakingHistory();
                });

        view.findViewById(R.id.btnProfileOpenLogin)
                .setOnClickListener(button ->
                        ((MainActivity) requireActivity()).openLogin()
                );

        SharedPreferences preferences = getPreferences();

        EditText nameInput =
                view.findViewById(R.id.etProfileName);

        Spinner levelSpinner =
                view.findViewById(R.id.spProfileLevel);

        Spinner goalSpinner =
                view.findViewById(R.id.spProfileGoal);

        levelSpinner.setAdapter(createAdapter(LEVELS));

        String[] goalLabels = new String[GOALS.length];

        for (int i = 0; i < GOALS.length; i++) {
            goalLabels[i] = GOALS[i] + " phút / ngày";
        }

        goalSpinner.setAdapter(createAdapter(goalLabels));

        // Khi xoay màn hình, Android tự khôi phục các ô nhập có ID.
        // Chỉ đọc thiết lập đã lưu khi mở màn hình lần đầu.
        if (savedInstanceState == null) {
            nameInput.setText(
                    preferences.getString(KEY_NAME, "Khoa")
            );

            String savedLevel =
                    preferences.getString(KEY_LEVEL, "A2");

            int levelPosition = 1;

            for (int i = 0; i < LEVELS.length; i++) {
                if (LEVELS[i].equals(savedLevel)) {
                    levelPosition = i;
                    break;
                }
            }

            levelSpinner.setSelection(levelPosition);

            int savedGoal = preferences.getInt(KEY_GOAL, 10);
            int goalPosition = 1;

            for (int i = 0; i < GOALS.length; i++) {
                if (GOALS[i] == savedGoal) {
                    goalPosition = i;
                    break;
                }
            }

            goalSpinner.setSelection(goalPosition);
        }

        view.findViewById(R.id.btnSaveProfile)
                .setOnClickListener(v -> saveProfile(view));
    }

    @Override
    public void onResume() {
        super.onResume();

        View view = getView();

        if (view != null) {
            renderHeader(view);
            renderStats(view);
        }
    }

    private SharedPreferences getPreferences() {
        return requireContext()
                .getApplicationContext()
                .getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );
    }

    private ArrayAdapter<String> createAdapter(String[] values) {
        return new ArrayAdapter<String>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                values
        ) {
            @NonNull
            @Override
            public View getView(
                    int position,
                    @Nullable View convertView,
                    @NonNull ViewGroup parent
            ) {
                View item = super.getView(
                        position,
                        convertView,
                        parent
                );

                styleSpinnerText(item);
                return item;
            }

            @Override
            public View getDropDownView(
                    int position,
                    @Nullable View convertView,
                    @NonNull ViewGroup parent
            ) {
                View item = super.getDropDownView(
                        position,
                        convertView,
                        parent
                );

                styleSpinnerText(item);

                item.setBackgroundColor(
                        ContextCompat.getColor(
                                requireContext(),
                                R.color.sm_surface
                        )
                );

                return item;
            }

            private void styleSpinnerText(View item) {
                if (!(item instanceof TextView)) {
                    return;
                }

                TextView text = (TextView) item;

                text.setTextColor(
                        ContextCompat.getColor(
                                requireContext(),
                                R.color.sm_text_primary
                        )
                );

                text.setTextSize(16);
                text.setGravity(android.view.Gravity.CENTER_VERTICAL);
                text.setMinHeight(dp(48));
                text.setPadding(dp(12), dp(8), dp(12), dp(8));
            }
        };
    }

    private void renderHeader(@NonNull View view) {
        String name = getPreferences()
                .getString(KEY_NAME, "Khoa");

        if (name == null || name.trim().isEmpty()) {
            name = "Khoa";
        }

        name = name.trim();

        TextView nameText =
                view.findViewById(R.id.tvProfileName);

        TextView avatarText =
                view.findViewById(R.id.tvProfileAvatar);

        nameText.setText(name);

        int firstCharacterEnd =
                name.offsetByCodePoints(0, 1);

        avatarText.setText(
                name.substring(0, firstCharacterEnd)
                        .toUpperCase(Locale.ROOT)
        );
    }

    private void renderStats(@NonNull View view) {
        TextView stats = view.findViewById(R.id.tvProfileStats);
        int request = ++statisticsRequest;
        AuthSession session = SessionManager.get(requireContext()).load();
        if (session == null) {
            showStats(stats, java.util.Collections.emptyList());
            return;
        }
        long userId = session.user.optLong("id");
        stats.setText("Đang tải tiến độ học…");
        stats.setOnClickListener(null);
        call(() -> api.savedCards(), cards -> {
            AuthSession current = SessionManager.get(requireContext()).load();
            if (request != statisticsRequest || current == null || current.user.optLong("id") != userId) return;
            showStats(stats, cards);
        }, error -> {
            if (request != statisticsRequest) return;
            stats.setText(error.getMessage() + " Chạm để tải lại số liệu.");
            stats.setOnClickListener(v -> renderStats(view));
        });
    }

    private void showStats(TextView view, List<Flashcard> savedCards) {
        try {
            FlashcardLibrary.Statistics stats = FlashcardLibrary.statistics(requireContext(), savedCards);
            view.setText("Bộ thẻ: " + stats.deckCount
                    + "\nBộ đã thuộc: " + stats.completedDeckCount + "/" + stats.deckCount
                    + "\nTổng số từ: " + stats.totalWords
                    + "\nTừ đã nhớ: " + stats.rememberedWords
                    + "\nTừ chưa nhớ: " + (stats.totalWords - stats.rememberedWords));
            view.setOnClickListener(null);
        } catch (IllegalStateException error) {
            view.setText("Chưa đọc được dữ liệu bộ thẻ trên thiết bị.");
        }
    }

    @Override
    public void onDestroyView() {
        statisticsRequest++;
        super.onDestroyView();
    }

    private void saveProfile(@NonNull View view) {
        if (!isAdded()
                || !isResumed()
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        EditText nameInput =
                view.findViewById(R.id.etProfileName);

        Spinner levelSpinner =
                view.findViewById(R.id.spProfileLevel);

        Spinner goalSpinner =
                view.findViewById(R.id.spProfileGoal);

        String name = nameInput.getText()
                .toString()
                .trim();

        if (name.isEmpty()) {
            nameInput.setError("Bạn hãy nhập tên hiển thị.");
            nameInput.requestFocus();
            return;
        }

        int levelPosition =
                levelSpinner.getSelectedItemPosition();

        int goalPosition =
                goalSpinner.getSelectedItemPosition();

        if (levelPosition < 0
                || levelPosition >= LEVELS.length
                || goalPosition < 0
                || goalPosition >= GOALS.length) {
            return;
        }

        getPreferences()
                .edit()
                .putString(KEY_NAME, name)
                .putString(KEY_LEVEL, LEVELS[levelPosition])
                .putInt(KEY_GOAL, GOALS[goalPosition])
                .apply();

        nameInput.setError(null);

        renderHeader(view);

        WindowCompat.getInsetsController(
                requireActivity().getWindow(),
                view
        ).hide(WindowInsetsCompat.Type.ime());

        view.clearFocus();
        nameInput.clearFocus();

        Toast.makeText(
                requireContext(),
                "Đã cập nhật thiết lập.",
                Toast.LENGTH_SHORT
        ).show();
    }

    private int dp(int value) {
        return Math.round(
                value * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
