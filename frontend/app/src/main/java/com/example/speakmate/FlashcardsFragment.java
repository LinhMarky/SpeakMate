package com.example.speakmate;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.example.speakmate.ui.NetworkFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FlashcardsFragment extends NetworkFragment {

    private List<FlashcardDeck> decks = new ArrayList<>();

    private EditText search;
    private LinearLayout container;
    private TextView countText;

    private TextWatcher searchWatcher;

    private boolean openingDeck;
    private boolean loading;
    private String serverStatus = "";

    public FlashcardsFragment() {
        super(R.layout.fragment_flashcards);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        openingDeck = false;

        search = view.findViewById(R.id.etSearchDeck);
        container = view.findViewById(R.id.deckContainer);
        countText = view.findViewById(R.id.tvDeckCount);

        searchWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {
                showDecks(s);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };

        search.addTextChangedListener(searchWatcher);

        // Phím Done đóng bàn phím sau khi tìm kiếm.
        search.setOnEditorActionListener(
                (input, actionId, event) -> {
                    if (actionId
                            == android.view.inputmethod.EditorInfo
                            .IME_ACTION_DONE
                            || actionId
                            == android.view.inputmethod.EditorInfo
                            .IME_ACTION_SEARCH) {

                        hideKeyboard();
                        input.clearFocus();
                        return true;
                    }

                    return false;
                }
        );

        // Nút Tạo bộ thẻ bằng AI theo chủ đề
        view.findViewById(R.id.btnCreateDeckAi)
                .setOnClickListener(button ->
                        openScreen(
                                new GenerateDeckFragment(),
                                "generate_deck"
                        )
                );

        // Nút Xem từ gợi ý AI sau bài hội thoại đã kết thúc
        View historyVocabularyBtn = view.findViewById(R.id.btnHistoryVocabulary);
        if (historyVocabularyBtn != null) {
            historyVocabularyBtn.setOnClickListener(button ->
                    openScreen(
                            SpeakingHistoryFragment.forVocabulary(),
                            "vocabulary_history"
                    )
            );
        }

        view.findViewById(R.id.btnCreateDeckManual)
                .setOnClickListener(button ->
                        openScreen(
                                new CreateDeckFragment(),
                                "create_deck"
                        )
                );
    }

    @Override
    public void onResume() {
        super.onResume();

        openingDeck = false;

        View view = getView();

        if (view == null
                || search == null
                || container == null
                || countText == null) {
            return;
        }

        view.findViewById(R.id.btnCreateDeckAi)
                .setEnabled(true);

        View historyVocabularyBtn = view.findViewById(R.id.btnHistoryVocabulary);
        if (historyVocabularyBtn != null) {
            historyVocabularyBtn.setEnabled(true);
        }

        view.findViewById(R.id.btnCreateDeckManual)
                .setEnabled(true);

        refreshDecks();
    }

    private void refreshDecks() {
        if (!isAdded()
                || search == null
                || container == null
                || countText == null) {
            return;
        }

        if (loading) return;
        loading = true;
        serverStatus = "Đang tải Từ của tôi…";
        countText.setOnClickListener(v -> refreshDecks());
        showDecks(search.getText());
        call(() -> api.savedCards(), cards -> {
            loading = false;
            decks = new ArrayList<>();
            int mastered = 0;
            for (Flashcard card : cards) if ("MASTERED".equals(card.getStatus())) mastered++;
            decks.add(new FlashcardDeck(FlashcardDeck.SAVED_WORDS_ID, "Từ của tôi", "Đồng bộ tài khoản",
                    cards.size(), mastered));
            try {
                decks.addAll(new LocalDeckStore(requireContext()).getDecks());
            } catch (IllegalStateException error) {
                serverStatus = "Chưa đọc được bộ thẻ trên thiết bị.";
            }
            if (serverStatus.startsWith("Đang tải")) serverStatus = cards.isEmpty()
                    ? "Chưa có từ đã lưu. Chọn Tạo bộ thẻ bằng AI hoặc Từ trong bài luyện." : "";
            showDecks(search.getText());
        }, error -> {
            loading = false;
            serverStatus = error.getMessage() + " Chạm để tải lại.";
            showDecks(search.getText());
        });
    }

    private void showDecks(@Nullable CharSequence keyword) {
        if (!isAdded()
                || container == null
                || countText == null) {
            return;
        }

        container.removeAllViews();

        String query = keyword == null
                ? ""
                : keyword.toString()
                .trim()
                .toLowerCase(Locale.ROOT);

        int visibleCount = 0;

        for (FlashcardDeck deck : decks) {
            String deckName = deck.getName() == null
                    ? ""
                    : deck.getName();

            if (!deckName.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }

            visibleCount++;

            View item = getLayoutInflater().inflate(
                    R.layout.item_flashcard_deck,
                    container,
                    false
            );

            TextView name =
                    item.findViewById(R.id.tvDeckName);

            TextView info =
                    item.findViewById(R.id.tvDeckInfo);

            TextView progressText =
                    item.findViewById(R.id.tvDeckProgress);

            ProgressBar progress =
                    item.findViewById(R.id.progressDeck);

            int totalCards = Math.max(
                    0,
                    deck.getTotalCards()
            );

            int learnedCards = Math.max(
                    0,
                    Math.min(
                            totalCards,
                            deck.getLearnedCards()
                    )
            );

            int percentage = totalCards == 0
                    ? 0
                    : Math.round(
                    learnedCards * 100f / totalCards
            );

            name.setText(deckName);

            android.widget.ImageView deckIcon =
                    item.findViewById(R.id.ivDeckIcon);

            deckIcon.setImageResource(getDeckIconResource(deckName));

            info.setText(
                    totalCards + " từ · " + deck.getLevel()
            );

            progress.setMax(100);
            progress.setProgress(percentage);

            progressText.setText(
                    learnedCards + "/" + totalCards
                            + " từ đã nhớ · " + percentage + "%"
            );

            item.setOnClickListener(button ->
                    openScreen(
                            DeckDetailFragment.newInstance(
                                    deck.getId()
                            ),
                            "deck_detail"
                    )
            );

            container.addView(item);
        }

        if (visibleCount > 0) {
            countText.setText(visibleCount + " bộ thẻ");

        } else if (query.isEmpty()) {
            countText.setText(
                    "Chưa có bộ thẻ. Hãy tạo bộ thẻ đầu tiên."
            );

        } else {
            countText.setText(
                    "Không tìm thấy bộ thẻ phù hợp."
            );
        }
        if (!serverStatus.isEmpty()) countText.setText(serverStatus);
    }

    private int getDeckIconResource(@Nullable String deckName) {
        if (deckName == null || deckName.trim().isEmpty()) {
            return R.drawable.ic_sm_stat_decks;
        }

        String normalized = java.text.Normalizer.normalize(
                deckName,
                java.text.Normalizer.Form.NFD
        );

        normalized = normalized
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);

        if (normalized.contains("travel")
                || normalized.contains("trip")
                || normalized.contains("tourism")
                || normalized.contains("du lich")
                || normalized.contains("hotel")
                || normalized.contains("khach san")) {
            return R.drawable.ic_sm_topic_travel;
        }

        if (normalized.contains("food")
                || normalized.contains("drink")
                || normalized.contains("restaurant")
                || normalized.contains("cooking")
                || normalized.contains("am thuc")
                || normalized.contains("mon an")
                || normalized.contains("do an")) {
            return R.drawable.ic_sm_topic_food;
        }

        if (normalized.contains("university")
                || normalized.contains("school")
                || normalized.contains("education")
                || normalized.contains("dai hoc")
                || normalized.contains("truong hoc")) {
            return R.drawable.ic_sm_topic_university;
        }

        if (normalized.contains("introduc")
                || normalized.contains("yourself")
                || normalized.contains("gioi thieu")
                || normalized.contains("ban than")) {
            return R.drawable.ic_sm_topic_intro;
        }

        return R.drawable.ic_sm_stat_decks;
    }

    private void openScreen(
            @NonNull Fragment destination,
            @NonNull String backStackName
    ) {
        if (!isAdded()
                || !isResumed()
                || getView() == null
                || openingDeck
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        openingDeck = true;

        hideKeyboard();

        if (search != null) {
            search.clearFocus();
        }

        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.fragmentContainer,
                        destination
                )
                .addToBackStack(backStackName)
                .commit();
    }

    private void hideKeyboard() {
        View view = getView();

        if (view == null || !isAdded()) {
            return;
        }

        WindowCompat.getInsetsController(
                requireActivity().getWindow(),
                view
        ).hide(WindowInsetsCompat.Type.ime());
    }

    @Override
    public void onDestroyView() {
        if (search != null) {
            if (searchWatcher != null) {
                search.removeTextChangedListener(searchWatcher);
            }

            search.setOnEditorActionListener(null);
        }

        searchWatcher = null;
        search = null;
        container = null;
        countText = null;

        openingDeck = false;
        loading = false;

        super.onDestroyView();
    }
}
