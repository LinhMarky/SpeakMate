package com.example.speakmate;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

        view.findViewById(R.id.btnCreateDeckAi)
                .setOnClickListener(v -> showDemoMessage(
                        getString(R.string.flashcard_create_ai_demo)
                ));

        view.findViewById(R.id.btnCreateDeckManual)
                .setOnClickListener(v -> {
                    if (openingDeck
                            || getParentFragmentManager().isStateSaved()) {
                        return;
                    }

                    openingDeck = true;

                    getParentFragmentManager()
                            .beginTransaction()
                            .setReorderingAllowed(true)
                            .replace(
                                    R.id.fragmentContainer,
                                    new CreateDeckFragment()
                            )
                            .addToBackStack("create_deck")
                            .commit();
                });
    }

    @Override
    public void onResume() {
        super.onResume();

        openingDeck = false;

        if (search == null) {
            return;
        }

        DemoFlashcardRepository repository =
                new DemoFlashcardRepository(requireContext());

        decks = repository.getDecks();

        showDecks(search.getText());
    }

    private void showDecks(@Nullable CharSequence keyword) {
        if (container == null || countText == null) {
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
            String deckName =
                    deck.getName().toLowerCase(Locale.ROOT);

            if (!deckName.contains(query)) {
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

            name.setText(deck.getName());

            info.setText(getString(
                    R.string.flashcard_deck_info,
                    deck.getTotalCards(),
                    deck.getLevel()
            ));

            int percentage = deck.getTotalCards() == 0
                    ? 0
                    : Math.round(
                    deck.getLearnedCards() * 100f
                    / deck.getTotalCards()
            );

            progress.setMax(100);
            progress.setProgress(percentage);

            progressText.setText(getString(
                    R.string.flashcard_deck_progress,
                    deck.getLearnedCards(),
                    deck.getTotalCards(),
                    percentage
            ));

            item.setOnClickListener(v -> {
                if (openingDeck
                        || getParentFragmentManager().isStateSaved()) {
                    return;
                }

                openingDeck = true;

                getParentFragmentManager()
                        .beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(
                                R.id.fragmentContainer,
                                DeckDetailFragment.newInstance(
                                        deck.getId()
                                )
                        )
                        .addToBackStack("deck_detail")
                        .commit();
            });

            container.addView(item);
        }

        if (visibleCount == 0) {
            countText.setText(R.string.flashcard_decks_empty);
        } else {
            countText.setText(visibleCount + " bộ thẻ");
        }
    }

    private void showDemoMessage(String message) {
        Toast.makeText(
                requireContext(),
                message,
                Toast.LENGTH_SHORT
        ).show();
    }

    @Override
    public void onDestroyView() {
        if (search != null && searchWatcher != null) {
            search.removeTextChangedListener(searchWatcher);
        }

        searchWatcher = null;
        search = null;
        container = null;
        countText = null;

        super.onDestroyView();
    }
}