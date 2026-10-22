package com.example.speakmate;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;

public class StudyFragment extends Fragment {

    private List<Flashcard> cards;

    private int currentIndex;
    private int rememberedCount;
    private boolean flipped;

    public StudyFragment() {
        super(R.layout.fragment_study);
    }

    public static StudyFragment newInstance(String deckId) {
        StudyFragment fragment = new StudyFragment();

        Bundle arguments = new Bundle();
        arguments.putString("deckId", deckId);

        fragment.setArguments(arguments);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            currentIndex = savedInstanceState.getInt("index", 0);

            rememberedCount = savedInstanceState.getInt(
                    "remembered", 0
            );

            flipped = savedInstanceState.getBoolean(
                    "flipped", false
            );
        }
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

        cards = repository.getCards(deckId);

        FlashcardDeck deck = repository.getDeckById(deckId);

        TextView title = view.findViewById(R.id.tvStudyTitle);

        title.setText(
                deck == null ? "Học flashcard" : deck.getName()
        );

        currentIndex = Math.max(
                0,
                Math.min(currentIndex, cards.size())
        );

        rememberedCount = Math.max(
                0,
                Math.min(rememberedCount, currentIndex)
        );

        view.findViewById(R.id.btnCloseStudy)
                .setOnClickListener(v -> {
                    if (!getParentFragmentManager().isStateSaved()) {
                        getParentFragmentManager()
                                .popBackStackImmediate();
                    }
                });

        view.findViewById(R.id.studyCard)
                .setOnClickListener(v -> {
                    if (currentIndex >= cards.size()) {
                        return;
                    }

                    flipped = !flipped;
                    render(view);
                });

        view.findViewById(R.id.btnRemembered)
                .setOnClickListener(v -> answer(true, view));

        view.findViewById(R.id.btnNotRemembered)
                .setOnClickListener(v -> answer(false, view));

        view.findViewById(R.id.btnRestartStudy)
                .setOnClickListener(v -> {
                    currentIndex = 0;
                    rememberedCount = 0;
                    flipped = false;

                    render(view);
                });

        render(view);
    }

    private void answer(boolean remembered, View view) {
        if (!flipped || currentIndex >= cards.size()) {
            return;
        }

        if (remembered) {
            rememberedCount++;
        }

        currentIndex++;
        flipped = false;

        render(view);
    }

    private void render(View view) {
        TextView progressText =
                view.findViewById(R.id.tvStudyProgress);

        ProgressBar progress =
                view.findViewById(R.id.progressStudy);

        View cardView = view.findViewById(R.id.studyCard);
        View actions = view.findViewById(R.id.studyActions);
        View result = view.findViewById(R.id.studyResult);

        boolean finished = currentIndex >= cards.size();

        cardView.setVisibility(
                finished ? View.GONE : View.VISIBLE
        );

        actions.setVisibility(
                finished ? View.GONE : View.VISIBLE
        );

        result.setVisibility(
                finished ? View.VISIBLE : View.GONE
        );

        int percentage = cards.isEmpty()
                ? 0
                : Math.round(
                currentIndex * 100f / cards.size()
        );

        progress.setProgress(percentage);

        if (finished) {
            progressText.setText(
                    "Đã ôn " + currentIndex
                            + "/" + cards.size() + " thẻ"
            );

            TextView resultText =
                    view.findViewById(R.id.tvStudyResult);

            if (cards.isEmpty()) {
                resultText.setText(
                        "Bộ thẻ này chưa có từ vựng."
                );
            } else {
                resultText.setText(
                        "Đã nhớ: " + rememberedCount
                                + "\nChưa nhớ: "
                                + (currentIndex - rememberedCount)
                );
            }

            view.findViewById(R.id.btnRestartStudy)
                    .setEnabled(!cards.isEmpty());

            return;
        }

        Flashcard card = cards.get(currentIndex);

        progressText.setText(
                "Thẻ " + (currentIndex + 1)
                        + " / " + cards.size()
                        + " · Đã ôn " + currentIndex
        );

        TextView word =
                view.findViewById(R.id.tvStudyWord);

        TextView meaning =
                view.findViewById(R.id.tvStudyMeaning);

        TextView example =
                view.findViewById(R.id.tvStudyExample);

        TextView hint =
                view.findViewById(R.id.tvFlipHint);

        word.setText(card.getWord());
        meaning.setText(card.getMeaning());
        example.setText(card.getExample());

        meaning.setVisibility(
                flipped ? View.VISIBLE : View.GONE
        );

        example.setVisibility(
                flipped ? View.VISIBLE : View.GONE
        );

        hint.setText(
                flipped
                        ? "Chạm để ẩn nghĩa"
                        : "Chạm vào thẻ để xem nghĩa"
        );

        View remembered =
                view.findViewById(R.id.btnRemembered);

        View notRemembered =
                view.findViewById(R.id.btnNotRemembered);

        remembered.setEnabled(flipped);
        notRemembered.setEnabled(flipped);

        remembered.setAlpha(flipped ? 1f : 0.45f);
        notRemembered.setAlpha(flipped ? 1f : 0.45f);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt("index", currentIndex);
        outState.putInt("remembered", rememberedCount);
        outState.putBoolean("flipped", flipped);
    }
}