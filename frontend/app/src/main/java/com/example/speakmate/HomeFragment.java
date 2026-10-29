package com.example.speakmate;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;

public class HomeFragment extends Fragment {

    public HomeFragment() {
        super(R.layout.fragment_home);
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
            String displayName = requireContext()
                    .getSharedPreferences(
                            "speakmate_profile",
                            android.content.Context.MODE_PRIVATE
                    )
                    .getString("display_name", "Khoa");

            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = "Khoa";
            }

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

        if (view == null) {
            return;
        }

        TextView deckCountText =
                view.findViewById(R.id.tvHomeDeckCount);

        TextView totalWordText =
                view.findViewById(R.id.tvHomeTotalWordCount);

        TextView learnedWordText =
                view.findViewById(R.id.tvLearnedWordCount);

        TextView remainingWordText =
                view.findViewById(R.id.tvHomeRemainingWordCount);

        TextView percentageText =
                view.findViewById(R.id.tvHomeProgressPercent);

        TextView progressDetailText =
                view.findViewById(R.id.tvHomeProgressDetail);

        TextView suggestionText =
                view.findViewById(R.id.tvHomeSuggestion);

        ProgressBar progress =
                view.findViewById(R.id.progressHomeVocabulary);

        try {
            DemoFlashcardRepository repository =
                    new DemoFlashcardRepository(requireContext());

            List<FlashcardDeck> decks = repository.getDecks();

            long totalWords = 0;
            long learnedWords = 0;

            for (FlashcardDeck deck : decks) {
                int total = Math.max(0, deck.getTotalCards());

                int learned = Math.max(
                        0,
                        Math.min(deck.getLearnedCards(), total)
                );

                totalWords += total;
                learnedWords += learned;
            }

            long remainingWords = totalWords - learnedWords;

            int percentage = totalWords == 0
                    ? 0
                    : (int) Math.round(
                    learnedWords * 100.0 / totalWords
            );

            deckCountText.setText(
                    String.valueOf(decks.size())
            );

            totalWordText.setText(
                    String.valueOf(totalWords)
            );

            learnedWordText.setText(
                    String.valueOf(learnedWords)
            );

            remainingWordText.setText(
                    String.valueOf(remainingWords)
            );

            percentageText.setText(percentage + "%");

            progress.setMax(100);
            progress.setProgress(percentage);

            progressDetailText.setText(
                    learnedWords
                            + " / "
                            + totalWords
                            + " từ đã nhớ"
            );

            if (totalWords == 0) {
                suggestionText.setText(
                        "Thêm bộ thẻ đầu tiên để bắt đầu "
                                + "hành trình học từ vựng."
                );
            } else if (remainingWords == 0) {
                suggestionText.setText(
                        "Bạn đã đánh dấu nhớ toàn bộ từ vựng! "
                                + "Hãy ôn lại hoặc thêm một bộ thẻ mới."
                );
            } else {
                suggestionText.setText(
                        "Còn " + remainingWords
                                + " từ chưa được đánh dấu đã nhớ. "
                                + "Chọn một bộ thẻ để tiếp tục học nhé."
                );
            }

        } catch (IllegalStateException exception) {
            /*
             * Không hiển thị số 0 giả khi đọc dữ liệu thất bại.
             */
            deckCountText.setText("—");
            totalWordText.setText("—");
            learnedWordText.setText("—");
            remainingWordText.setText("—");

            percentageText.setText("—");
            progress.setProgress(0);

            progressDetailText.setText(
                    "Chưa đọc được tiến độ"
            );

            suggestionText.setText(
                    "Không đọc được dữ liệu bộ thẻ. "
                            + "Vui lòng kiểm tra lại dữ liệu đã lưu."
            );
        }
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