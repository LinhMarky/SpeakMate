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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FlashcardsFragment extends Fragment {

    private List<FlashcardDeck> decks = new ArrayList<>();

    private EditText search;
    private LinearLayout container;
    private TextView countText;

    private TextWatcher searchWatcher;

    private boolean openingDeck;

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
                    int before,
                    int count
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

        // Mỗi nút chỉ được gắn một sự kiện.
        view.findViewById(R.id.btnCreateDeckAi)
                .setOnClickListener(button ->
                        openScreen(
                                new GenerateDeckFragment(),
                                "generate_deck"
                        )
                );

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

        try {
            DemoFlashcardRepository repository =
                    new DemoFlashcardRepository(requireContext());

            decks = repository.getDecks();

            showDecks(search.getText());

        } catch (IllegalStateException exception) {
            decks = new ArrayList<>();

            container.removeAllViews();

            countText.setText(
                    "Chưa đọc được danh sách bộ thẻ trên thiết bị."
            );
        }
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

        super.onDestroyView();
    }
}