package com.example.speakmate;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;

public class DeckDetailFragment extends Fragment {

    private boolean openingStudy;

    public DeckDetailFragment() {
        super(R.layout.fragment_deck_detail);
    }

    public static DeckDetailFragment newInstance(String deckId) {
        DeckDetailFragment fragment = new DeckDetailFragment();

        Bundle arguments = new Bundle();
        arguments.putString("deckId", deckId);

        fragment.setArguments(arguments);

        return fragment;
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        String deckId = requireArguments().getString(
                "deckId", ""
        );

        DemoFlashcardRepository repository =
                new DemoFlashcardRepository();

        FlashcardDeck deck = repository.getDeckById(deckId);

        view.findViewById(R.id.btnBackDeck)
                .setOnClickListener(v -> {
                    if (!getParentFragmentManager().isStateSaved()) {
                        getParentFragmentManager()
                                .popBackStackImmediate();
                    }
                });

        TextView title =
                view.findViewById(R.id.tvDetailDeckName);

        TextView info =
                view.findViewById(R.id.tvDetailDeckInfo);

        LinearLayout container =
                view.findViewById(R.id.cardListContainer);

        View studyButton =
                view.findViewById(R.id.btnStudyDeck);

        if (deck == null) {
            title.setText("Không tìm thấy bộ thẻ");
            info.setText("Vui lòng quay lại thư viện.");
            studyButton.setEnabled(false);
            return;
        }

        List<Flashcard> cards = repository.getCards(deckId);

        title.setText(deck.getName());

        info.setText(
                "Trình độ: " + deck.getLevel()
                        + "\nSố từ: " + cards.size()
                        + "\nĐã nhớ: " + deck.getLearnedCards()
                        + "\nNguồn: Dữ liệu demo"
        );

        container.removeAllViews();

        for (Flashcard card : cards) {
            View item = getLayoutInflater().inflate(
                    R.layout.item_vocabulary,
                    container,
                    false
            );

            TextView word =
                    item.findViewById(R.id.tvVocabularyWord);

            TextView meaning =
                    item.findViewById(R.id.tvVocabularyMeaning);

            TextView example =
                    item.findViewById(R.id.tvVocabularyExample);

            word.setText(card.getWord());
            meaning.setText(card.getMeaning());
            example.setText(card.getExample());

            container.addView(item);
        }

        studyButton.setEnabled(!cards.isEmpty());

        studyButton.setOnClickListener(v -> {
            if (getParentFragmentManager().isStateSaved()
                    || openingStudy
                    || cards.isEmpty()) {
                return;
            }

            openingStudy = true;
            studyButton.setEnabled(false);

            getParentFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(
                            R.id.fragmentContainer,
                            StudyFragment.newInstance(deckId)
                    )
                    .addToBackStack("study")
                    .commit();
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        openingStudy = false;

        String deckId = requireArguments().getString(
                "deckId", ""
        );

        DemoFlashcardRepository repository =
                new DemoFlashcardRepository();

        boolean canStudy =
                repository.getDeckById(deckId) != null
                        && !repository.getCards(deckId).isEmpty();

        requireView()
                .findViewById(R.id.btnStudyDeck)
                .setEnabled(canStudy);
    }
}