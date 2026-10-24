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

    private static final String ARG_DECK_ID = "deckId";

    private boolean openingStudy;

    public DeckDetailFragment() {
        super(R.layout.fragment_deck_detail);
    }

    public static DeckDetailFragment newInstance(String deckId) {
        DeckDetailFragment fragment = new DeckDetailFragment();

        Bundle arguments = new Bundle();
        arguments.putString(ARG_DECK_ID, deckId);

        fragment.setArguments(arguments);

        return fragment;
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        openingStudy = false;

        view.findViewById(R.id.btnBackDeck)
                .setOnClickListener(v -> {
                    if (!isAdded()
                            || getParentFragmentManager().isStateSaved()) {
                        return;
                    }

                    getParentFragmentManager()
                            .popBackStackImmediate();
                });

        view.findViewById(R.id.btnStudyDeck)
                .setOnClickListener(v -> openStudy());

        // Hiển thị danh sách từ khi tạo giao diện.
        renderDeck(view, true);
    }

    @Override
    public void onResume() {
        super.onResume();

        openingStudy = false;

        View view = getView();

        if (view != null) {
            // Đọc lại tiến độ khi quay về từ màn hình học.
            renderDeck(view, false);
        }
    }

    private String getDeckId() {
        Bundle arguments = getArguments();

        if (arguments == null) {
            return "";
        }

        String deckId = arguments.getString(ARG_DECK_ID);

        return deckId == null ? "" : deckId;
    }

    private void renderDeck(
            @NonNull View view,
            boolean rebuildCardList
    ) {
        DemoFlashcardRepository repository =
                new DemoFlashcardRepository(requireContext());

        String deckId = getDeckId();

        FlashcardDeck deck =
                repository.getDeckById(deckId);

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

            container.removeAllViews();
            studyButton.setEnabled(false);

            return;
        }

        List<Flashcard> cards =
                repository.getCards(deckId);

        title.setText(deck.getName());

        String source = deckId.startsWith("local_")
                ? "Tự tạo trên thiết bị"
                : "Dữ liệu demo";

        info.setText(
                "Trình độ: " + deck.getLevel()
                        + "\nSố từ: " + cards.size()
                        + "\nĐã nhớ: " + deck.getLearnedCards()
                        + "\nNguồn: " + source
        );

        studyButton.setEnabled(
                !openingStudy && !cards.isEmpty()
        );

        if (!rebuildCardList) {
            return;
        }

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

            boolean hasExample = card.getExample() != null
                    && !card.getExample().trim().isEmpty();

            example.setVisibility(
                    hasExample ? View.VISIBLE : View.GONE
            );

            container.addView(item);
        }
    }

    private void openStudy() {
        View view = getView();

        if (view == null
                || !isAdded()
                || openingStudy
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        String deckId = getDeckId();

        DemoFlashcardRepository repository =
                new DemoFlashcardRepository(requireContext());

        FlashcardDeck deck =
                repository.getDeckById(deckId);

        if (deck == null
                || repository.getCards(deckId).isEmpty()) {
            renderDeck(view, false);
            return;
        }

        openingStudy = true;

        view.findViewById(R.id.btnStudyDeck)
                .setEnabled(false);

        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.fragmentContainer,
                        StudyFragment.newInstance(deckId)
                )
                .addToBackStack("study")
                .commit();
    }
}