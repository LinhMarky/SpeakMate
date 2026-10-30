package com.example.speakmate;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.speakmate.auth.AuthSession;
import com.example.speakmate.auth.SessionManager;
import com.example.speakmate.ui.NetworkFragment;

import java.util.List;

public class HomeFragment extends NetworkFragment {

    private int statisticsRequest;

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    protected boolean requiresAuthentication() {
        return false;
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);


        view.findViewById(R.id.btnHomeEditLearningPlan)
                .setOnClickListener(v ->
                        ((MainActivity) requireActivity())
                                .navigateToTab(R.id.nav_profile)
                );

        view.findViewById(R.id.btnStartSpeaking)
                .setOnClickListener(v ->
                        navigateToTab(R.id.nav_speaking)
                );

        view.findViewById(R.id.cardFlashcards)
                .setOnClickListener(v ->
                        navigateToTab(R.id.nav_flashcards)
                );
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshLearningPlan();

        View homeView = getView();

        if (homeView != null) {
            var session = com.example.speakmate.auth.SessionManager.get(requireContext()).load();
            String displayName = session == null ? "bạn" : session.getName();

            android.widget.TextView greeting =
                    homeView.findViewById(R.id.tvGreeting);

            greeting.setText("Chào " + displayName.trim() + "!");
        }

        // Đọc lại dữ liệu mỗi khi quay về trang chủ.
        refreshStatistics();
    }

    private void navigateToTab(int itemId) {
        if (!isAdded()
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity())
                    .navigateToTab(itemId);
        }
    }

    private void refreshStatistics() {
        View view = getView();
        if (view == null) return;

        int request = ++statisticsRequest;
        SessionManager sessions = SessionManager.get(requireContext());
        AuthSession session = sessions.load();
        if (session == null) {
            showStatistics(java.util.Collections.emptyList());
            return;
        }

        long userId = session.user.optLong("id");
        showStatisticsUnavailable("Đang tải tiến độ…", "Đang đồng bộ từ đã lưu.");
        call(() -> api.savedCards(), cards -> {
            if (!isCurrentStatisticsRequest(request, userId)) return;
            showStatistics(cards);
        }, error -> {
            if (!isCurrentStatisticsRequest(request, userId)) return;
            showStatisticsUnavailable("Chưa tải được tiến độ",
                    error.getMessage() + " Chạm để tải lại.");
        });
    }

    private boolean isCurrentStatisticsRequest(int request, long userId) {
        AuthSession current = SessionManager.get(requireContext()).load();
        return statisticsRequest == request
                && current != null
                && current.user.optLong("id") == userId;
    }

    private void showStatistics(List<Flashcard> savedCards) {
        View view = getView();
        if (view == null) return;

        try {
            FlashcardLibrary.Statistics statistics =
                    FlashcardLibrary.statistics(requireContext(), savedCards);
            long totalWords = statistics.totalWords;
            long learnedWords = statistics.rememberedWords;
            long remainingWords = totalWords - learnedWords;
            int percentage = totalWords == 0
                    ? 0 : (int) Math.round(learnedWords * 100.0 / totalWords);

            ((TextView) view.findViewById(R.id.tvHomeDeckCount))
                    .setText(String.valueOf(statistics.deckCount));
            ((TextView) view.findViewById(R.id.tvHomeTotalWordCount))
                    .setText(String.valueOf(totalWords));
            ((TextView) view.findViewById(R.id.tvLearnedWordCount))
                    .setText(String.valueOf(learnedWords));
            ((TextView) view.findViewById(R.id.tvHomeRemainingWordCount))
                    .setText(String.valueOf(remainingWords));
            ((TextView) view.findViewById(R.id.tvHomeProgressPercent))
                    .setText(percentage + "%");
            ((TextView) view.findViewById(R.id.tvHomeProgressDetail)).setText(
                    learnedWords + " / " + totalWords + " từ đã nhớ · "
                            + statistics.completedDeckCount + "/"
                            + statistics.deckCount + " bộ đã thuộc");

            ProgressBar progress = view.findViewById(R.id.progressHomeVocabulary);
            progress.setMax(100);
            progress.setProgress(percentage);

            TextView suggestion = view.findViewById(R.id.tvHomeSuggestion);
            suggestion.setOnClickListener(null);
            if (totalWords == 0) {
                suggestion.setText("Thêm bộ thẻ đầu tiên để bắt đầu hành trình học từ vựng.");
            } else if (remainingWords == 0) {
                suggestion.setText("Bạn đã đánh dấu nhớ toàn bộ từ vựng! "
                        + "Hãy ôn lại hoặc thêm một bộ thẻ mới.");
            } else {
                suggestion.setText("Còn " + remainingWords
                        + " từ chưa được đánh dấu đã nhớ. "
                        + "Chọn một bộ thẻ để tiếp tục học nhé.");
            }
        } catch (IllegalStateException error) {
            showStatisticsUnavailable("Chưa đọc được tiến độ",
                    "Không đọc được dữ liệu bộ thẻ. Chạm để tải lại.");
        }
    }

    private void showStatisticsUnavailable(String detail, String message) {
        View view = getView();
        if (view == null) return;

        int[] counterIds = { R.id.tvHomeDeckCount, R.id.tvHomeTotalWordCount,
                R.id.tvLearnedWordCount, R.id.tvHomeRemainingWordCount,
                R.id.tvHomeProgressPercent };
        for (int id : counterIds) {
            ((TextView) view.findViewById(id)).setText("—");
        }
        ((ProgressBar) view.findViewById(R.id.progressHomeVocabulary)).setProgress(0);
        ((TextView) view.findViewById(R.id.tvHomeProgressDetail)).setText(detail);
        TextView suggestion = view.findViewById(R.id.tvHomeSuggestion);
        suggestion.setText(message);
        suggestion.setOnClickListener(v -> refreshStatistics());
    }

    @Override
    public void onDestroyView() {
        statisticsRequest++;
        super.onDestroyView();
    }

    private void refreshLearningPlan() {
        View view = getView();

        if (view == null) {
            return;
        }

        android.content.SharedPreferences preferences =
                requireContext().getSharedPreferences(
                        "speakmate_profile",
                        android.content.Context.MODE_PRIVATE
                );

        String level = preferences.getString(
                "practice_level",
                "A2"
        );

        // Chỉ hiển thị các mức mà màn luyện nói đang hỗ trợ.
        if (!"A1".equals(level)
                && !"A2".equals(level)
                && !"B1".equals(level)
                && !"B2".equals(level)
                && !"C1".equals(level)
                && !"C2".equals(level)) {
            level = "A2";
        }

        int goalMinutes = preferences.getInt(
                "daily_goal_minutes",
                10
        );

        if (goalMinutes != 5
                && goalMinutes != 10
                && goalMinutes != 15
                && goalMinutes != 20
                && goalMinutes != 30) {
            goalMinutes = 10;
        }

        android.widget.TextView levelText =
                view.findViewById(R.id.tvHomePracticeLevel);

        android.widget.TextView goalText =
                view.findViewById(R.id.tvHomeDailyGoal);

        levelText.setText(
                "Trình độ muốn luyện: " + level
        );

        goalText.setText(
                "Mục tiêu: " + goalMinutes + " phút mỗi ngày"
        );
    }
}
