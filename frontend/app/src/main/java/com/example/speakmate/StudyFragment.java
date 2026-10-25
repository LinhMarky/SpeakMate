package com.example.speakmate;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

public class StudyFragment extends Fragment {

    private static final String ARG_DECK_ID = "deckId";

    private static final String STATE_ANSWERS = "study_answers";
    private static final String STATE_FLIPPED = "study_flipped";
    private static final String STATE_REVIEW = "study_review";
    private static final String STATE_WORDS = "study_words";
    private static final String STATE_MEANINGS = "study_meanings";
    private static final String STATE_EXAMPLES = "study_examples";

    private static final int COLOR_SURFACE = 0xFF24233F;
    private static final int COLOR_BORDER = 0xFF45416B;
    private static final int COLOR_SUCCESS = 0xFF35D6A0;
    private static final int COLOR_WARNING = 0xFFFFC66D;

    private List<Flashcard> allCards = new ArrayList<>();
    private List<Flashcard> cards = new ArrayList<>();

    private final ArrayList<Integer> answers = new ArrayList<>();

    private String currentDeckId = "";

    private boolean roundInitialized;
    private boolean reviewMode;
    private boolean flipped;

    private boolean animating;
    private boolean dragging;
    private boolean gestureCancelled;

    private float downX;
    private float downY;
    private int touchSlop;

    private View rootView;
    private SwipeCardLayout cardView;

    private FlashcardProgressStore progressStore;
    private DeckSpeech speech;

    public StudyFragment() {
        super(R.layout.fragment_study);
    }

    public static StudyFragment newInstance(String deckId) {
        StudyFragment fragment = new StudyFragment();

        Bundle arguments = new Bundle();
        arguments.putString(ARG_DECK_ID, deckId);

        fragment.setArguments(arguments);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            return;
        }

        ArrayList<String> words =
                savedInstanceState.getStringArrayList(STATE_WORDS);

        ArrayList<String> meanings =
                savedInstanceState.getStringArrayList(STATE_MEANINGS);

        ArrayList<String> examples =
                savedInstanceState.getStringArrayList(STATE_EXAMPLES);

        boolean validRound = words != null
                && meanings != null
                && examples != null
                && words.size() == meanings.size()
                && words.size() == examples.size();

        if (!validRound) {
            return;
        }

        cards = new ArrayList<>();

        for (int i = 0; i < words.size(); i++) {
            cards.add(new Flashcard(
                    words.get(i),
                    meanings.get(i),
                    examples.get(i)
            ));
        }

        roundInitialized = true;

        ArrayList<Integer> savedAnswers =
                savedInstanceState.getIntegerArrayList(STATE_ANSWERS);

        answers.clear();

        if (savedAnswers != null) {
            for (Integer answer : savedAnswers) {
                if (answers.size() >= cards.size()
                        || answer == null
                        || (answer != 0 && answer != 1)) {
                    break;
                }

                answers.add(answer);
            }
        }

        flipped = savedInstanceState.getBoolean(
                STATE_FLIPPED,
                false
        );

        reviewMode = savedInstanceState.getBoolean(
                STATE_REVIEW,
                false
        );
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        rootView = view;

        animating = false;
        dragging = false;
        gestureCancelled = false;

        touchSlop = ViewConfiguration.get(requireContext())
                .getScaledTouchSlop();

        progressStore =
                new FlashcardProgressStore(requireContext());

        setupSwipeCard(view);
        loadDeck();

        speech = new DeckSpeech(requireContext());

        view.findViewById(R.id.btnCloseStudy)
                .setOnClickListener(v -> closeStudy());

        view.findViewById(R.id.btnBackToDeck)
                .setOnClickListener(v -> closeStudy());

        cardView.setOnClickListener(v -> {
            if (!canInteract() || isFinished()) {
                return;
            }

            flipped = !flipped;

            render();
            speakCurrentWord();
        });

        view.findViewById(R.id.btnRemembered)
                .setOnClickListener(v -> dismissCard(true));

        view.findViewById(R.id.btnNotRemembered)
                .setOnClickListener(v -> dismissCard(false));

        view.findViewById(R.id.btnRestartStudy)
                .setOnClickListener(v -> {
                    if (!canInteract() || allCards.isEmpty()) {
                        return;
                    }

                    startRound(allCards, false);
                });

        view.findViewById(R.id.btnReviewUnknown)
                .setOnClickListener(v -> reviewUnknownCards());

        render();
    }

    @Override
    public void onViewStateRestored(
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewStateRestored(savedInstanceState);

        // Áp dụng lại giao diện sau khi Android khôi phục view.
        render();
    }

    private void setupSwipeCard(View view) {
        FrameLayout originalCard =
                view.findViewById(R.id.studyCard);

        ViewGroup parent =
                (ViewGroup) originalCard.getParent();

        int index = parent.indexOfChild(originalCard);

        ViewGroup.LayoutParams params =
                originalCard.getLayoutParams();

        cardView = new SwipeCardLayout();

        while (originalCard.getChildCount() > 0) {
            View child = originalCard.getChildAt(0);

            originalCard.removeViewAt(0);
            cardView.addView(child);
        }

        parent.removeViewAt(index);

        cardView.setId(R.id.studyCard);
        cardView.setLayoutParams(params);
        cardView.setClickable(true);
        cardView.setFocusable(true);

        cardView.setContentDescription(
                "Thẻ từ vựng. Chạm để lật và nghe lại"
        );

        parent.addView(cardView, index);
    }

    private void loadDeck() {
        Bundle arguments = getArguments();

        currentDeckId = arguments == null
                ? ""
                : arguments.getString(ARG_DECK_ID, "");

        if (currentDeckId == null) {
            currentDeckId = "";
        }

        DemoFlashcardRepository repository =
                new DemoFlashcardRepository(requireContext());

        FlashcardDeck deck =
                repository.getDeckById(currentDeckId);

        allCards = new ArrayList<>();

        if (deck == null) {
            text(R.id.tvStudyTitle)
                    .setText("Không tìm thấy bộ thẻ");

            cards.clear();
            answers.clear();

            reviewMode = false;
            roundInitialized = true;

            return;
        }

        text(R.id.tvStudyTitle).setText(deck.getName());

        allCards.addAll(repository.getCards(currentDeckId));

        if (!roundInitialized) {
            cards = new ArrayList<>(allCards);
            answers.clear();

            reviewMode = false;
            roundInitialized = true;
        }
    }

    private TextView text(int id) {
        return rootView.findViewById(id);
    }

    private boolean canInteract() {
        return rootView != null
                && isResumed()
                && !animating
                && !getParentFragmentManager().isStateSaved();
    }

    private boolean isFinished() {
        return answers.size() >= cards.size();
    }

    private int getRememberedCount() {
        int count = 0;

        for (Integer answer : answers) {
            if (answer == 1) {
                count++;
            }
        }

        return count;
    }

    private void startRound(
            List<Flashcard> nextCards,
            boolean reviewing
    ) {
        stopSpeech();

        cards = new ArrayList<>(nextCards);
        answers.clear();

        reviewMode = reviewing;
        roundInitialized = true;

        flipped = false;
        dragging = false;
        gestureCancelled = false;

        render();
        speakCurrentWord();
    }

    private void reviewUnknownCards() {
        if (!canInteract() || !isFinished()) {
            return;
        }

        List<Flashcard> unknownCards = new ArrayList<>();

        for (int i = 0; i < answers.size(); i++) {
            if (answers.get(i) == 0) {
                unknownCards.add(cards.get(i));
            }
        }

        if (!unknownCards.isEmpty()) {
            startRound(unknownCards, true);
        }
    }

    private void speakCurrentWord() {
        if (speech == null
                || !canInteract()
                || isFinished()) {
            return;
        }

        /*
         * Luôn đọc từ tiếng Anh,
         * kể cả khi thẻ đang hiện nghĩa tiếng Việt.
         */
        speech.speak(
                cards.get(answers.size()).getWord()
        );
    }

    private void stopSpeech() {
        if (speech != null) {
            speech.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Lần đầu mở hoặc quay lại màn hình: đọc thẻ hiện tại.
        speakCurrentWord();
    }

    @Override
    public void onPause() {
        stopSpeech();
        super.onPause();
    }

    private void closeStudy() {
        if (!isAdded()
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        stopSpeech();

        getParentFragmentManager().popBackStackImmediate();
    }

    private void recordAnswer(boolean remembered) {
        if (progressStore == null || isFinished()) {
            return;
        }

        Flashcard card = cards.get(answers.size());

        progressStore.saveAnswer(
                currentDeckId,
                card,
                remembered
        );

        answers.add(remembered ? 1 : 0);
        flipped = false;
    }

    private void render() {
        if (rootView == null || cardView == null) {
            return;
        }

        int completed = answers.size();
        int total = cards.size();

        int remembered = getRememberedCount();
        int unknown = completed - remembered;

        boolean finished = isFinished();

        text(R.id.tvStudyMode).setText(
                reviewMode ? "Ôn từ chưa thuộc" : "Học toàn bộ"
        );

        text(R.id.tvRememberedCount).setText(
                "Đã nhớ: " + remembered
        );

        text(R.id.tvNotRememberedCount).setText(
                "Chưa thuộc: " + unknown
        );

        text(R.id.tvStudyProgress).setText(
                completed + "/" + total + " thẻ"
        );

        ProgressBar progress =
                rootView.findViewById(R.id.progressStudy);

        progress.setMax(100);

        progress.setProgress(
                total == 0
                        ? 0
                        : Math.round(completed * 100f / total)
        );

        rootView.findViewById(R.id.studyStage)
                .setVisibility(
                        finished ? View.GONE : View.VISIBLE
                );

        rootView.findViewById(R.id.studyActions)
                .setVisibility(
                        finished ? View.GONE : View.VISIBLE
                );

        rootView.findViewById(R.id.tvSwipeHint)
                .setVisibility(
                        finished ? View.GONE : View.VISIBLE
                );

        rootView.findViewById(R.id.studyResult)
                .setVisibility(
                        finished ? View.VISIBLE : View.GONE
                );

        rootView.findViewById(R.id.btnRestartStudy)
                .setEnabled(
                        !allCards.isEmpty() && !animating
                );

        TextView reviewButton =
                text(R.id.btnReviewUnknown);

        boolean canReview = finished && unknown > 0;

        reviewButton.setText(
                "Ôn từ chưa thuộc (" + unknown + ")"
        );

        reviewButton.setVisibility(
                canReview ? View.VISIBLE : View.GONE
        );

        reviewButton.setEnabled(canReview && !animating);

        resetCardAppearance();

        if (finished) {
            stopSpeech();

            if (total == 0) {
                text(R.id.tvStudyResultTitle)
                        .setText("Chưa có thẻ để học");

                text(R.id.tvStudyResult).setText(
                        "Bộ thẻ không tồn tại hoặc chưa có từ vựng."
                );
            } else {
                text(R.id.tvStudyResultTitle).setText(
                        reviewMode
                                ? "Hoàn thành lượt ôn!"
                                : "Hoàn thành lượt học!"
                );

                String message =
                        "Đã nhớ: " + remembered
                                + "\nChưa thuộc: " + unknown
                                + "\nSố thẻ trong lượt: " + total
                                + "\n\nĐã lưu tiến độ trên thiết bị.";

                if (unknown == 0) {
                    message +=
                            "\nBạn đã nhớ tất cả thẻ trong lượt này.";
                }

                text(R.id.tvStudyResult).setText(message);
            }

            setAnswerButtonsEnabled(false);
            return;
        }

        Flashcard card = cards.get(completed);

        text(R.id.tvStudyWord).setText(card.getWord());
        text(R.id.tvStudyMeaning).setText(card.getMeaning());
        text(R.id.tvStudyExample).setText(card.getExample());

        rootView.findViewById(R.id.tvStudyMeaning)
                .setVisibility(
                        flipped ? View.VISIBLE : View.GONE
                );

        boolean hasExample = card.getExample() != null
                && !card.getExample().trim().isEmpty();

        rootView.findViewById(R.id.tvStudyExample)
                .setVisibility(
                        flipped && hasExample
                                ? View.VISIBLE
                                : View.GONE
                );

        text(R.id.tvFlipHint).setText(
                flipped
                        ? "Chạm để ẩn nghĩa và nghe lại"
                        : "Chạm để xem nghĩa và nghe lại"
        );

        setAnswerButtonsEnabled(!animating);
    }

    private void resetCardAppearance() {
        cardView.setTranslationX(0f);
        cardView.setTranslationY(0f);
        cardView.setRotation(0f);
        cardView.setAlpha(1f);

        cardView.setBackground(
                createCardBackground(COLOR_BORDER, 1)
        );

        rootView.findViewById(R.id.tvSwipeLabel)
                .setVisibility(View.GONE);
    }

    private GradientDrawable createCardBackground(
            int borderColor,
            int borderWidthDp
    ) {
        GradientDrawable background =
                new GradientDrawable();

        background.setColor(COLOR_SURFACE);
        background.setCornerRadius(dp(20));

        background.setStroke(
                Math.round(dp(borderWidthDp)),
                borderColor
        );

        return background;
    }

    private void showSwipeFeedback(boolean remembered) {
        int color = remembered
                ? COLOR_SUCCESS
                : COLOR_WARNING;

        TextView label = text(R.id.tvSwipeLabel);

        label.setText(
                remembered ? "Đã nhớ" : "Chưa thuộc"
        );

        label.setTextColor(color);

        label.setBackground(
                createCardBackground(color, 3)
        );

        label.setVisibility(View.VISIBLE);

        cardView.setBackground(
                createCardBackground(color, 3)
        );
    }

    private void setAnswerButtonsEnabled(boolean enabled) {
        if (rootView == null) {
            return;
        }

        rootView.findViewById(R.id.btnRemembered)
                .setEnabled(enabled);

        rootView.findViewById(R.id.btnNotRemembered)
                .setEnabled(enabled);
    }

    private void dismissCard(boolean remembered) {
        if (!canInteract()
                || cardView == null
                || isFinished()) {
            return;
        }

        stopSpeech();

        if (cardView.getWidth() == 0) {
            recordAnswer(remembered);

            render();
            speakCurrentWord();

            return;
        }

        animating = true;

        setAnswerButtonsEnabled(false);
        showSwipeFeedback(remembered);

        SwipeCardLayout movingCard = cardView;

        float direction = remembered ? 1f : -1f;

        float distance = Math.max(
                rootView.getWidth(),
                movingCard.getWidth()
        ) + movingCard.getWidth();

        movingCard.animate()
                .translationX(direction * distance)
                .translationY(dp(24))
                .rotation(direction * 18f)
                .alpha(0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (rootView == null
                            || cardView != movingCard) {
                        return;
                    }

                    recordAnswer(remembered);
                    animating = false;

                    render();
                    speakCurrentWord();
                })
                .start();
    }

    private void snapCardBack() {
        if (cardView == null
                || animating
                || isFinished()) {
            return;
        }

        animating = true;
        setAnswerButtonsEnabled(false);

        SwipeCardLayout movingCard = cardView;

        movingCard.animate()
                .translationX(0f)
                .translationY(0f)
                .rotation(0f)
                .alpha(1f)
                .setDuration(160)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (rootView == null
                            || cardView != movingCard) {
                        return;
                    }

                    animating = false;
                    render();
                })
                .start();
    }

    private void allowParentIntercept(boolean allow) {
        if (cardView == null) {
            return;
        }

        ViewParent parent = cardView.getParent();

        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(!allow);
        }
    }

    private float dp(float value) {
        return value * getResources()
                .getDisplayMetrics().density;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putIntegerArrayList(
                STATE_ANSWERS,
                new ArrayList<>(answers)
        );

        outState.putBoolean(STATE_FLIPPED, flipped);
        outState.putBoolean(STATE_REVIEW, reviewMode);

        ArrayList<String> words = new ArrayList<>();
        ArrayList<String> meanings = new ArrayList<>();
        ArrayList<String> examples = new ArrayList<>();

        for (Flashcard card : cards) {
            words.add(card.getWord());
            meanings.add(card.getMeaning());
            examples.add(card.getExample());
        }

        outState.putStringArrayList(STATE_WORDS, words);
        outState.putStringArrayList(STATE_MEANINGS, meanings);
        outState.putStringArrayList(STATE_EXAMPLES, examples);
    }

    @Override
    public void onDestroyView() {
        allowParentIntercept(true);

        if (speech != null) {
            speech.close();
            speech = null;
        }

        if (cardView != null) {
            cardView.animate().withEndAction(null);
            cardView.animate().cancel();
            cardView.setOnClickListener(null);
        }

        rootView = null;
        cardView = null;
        progressStore = null;

        animating = false;
        dragging = false;
        gestureCancelled = false;

        super.onDestroyView();
    }

    private class SwipeCardLayout extends FrameLayout {

        SwipeCardLayout() {
            super(StudyFragment.this.requireContext());
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (!canInteract() || isFinished()) {
                return true;
            }

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();

                    dragging = false;
                    gestureCancelled = false;

                    allowParentIntercept(false);
                    break;

                case MotionEvent.ACTION_POINTER_DOWN:
                    gestureCancelled = true;

                    if (dragging) {
                        dragging = false;
                        snapCardBack();
                    }

                    allowParentIntercept(true);
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (gestureCancelled) {
                        super.dispatchTouchEvent(event);
                        return true;
                    }

                    float deltaX = event.getRawX() - downX;
                    float deltaY = event.getRawY() - downY;

                    if (!dragging) {
                        if (Math.abs(deltaX) <= touchSlop
                                && Math.abs(deltaY) <= touchSlop) {
                            break;
                        }

                        if (Math.abs(deltaY) > Math.abs(deltaX)) {
                            gestureCancelled = true;
                            allowParentIntercept(true);
                            break;
                        }

                        dragging = true;
                        cancelChildTouch(event);
                    }

                    setTranslationX(deltaX);
                    setTranslationY(deltaY * 0.1f);

                    float rotation = deltaX
                            / Math.max(1, getWidth()) * 14f;

                    setRotation(
                            Math.max(-18f, Math.min(18f, rotation))
                    );

                    showSwipeFeedback(deltaX >= 0f);

                    return true;

                case MotionEvent.ACTION_UP:
                    allowParentIntercept(true);

                    if (gestureCancelled) {
                        dragging = false;
                        super.dispatchTouchEvent(event);
                        return true;
                    }

                    if (dragging) {
                        dragging = false;

                        float distanceX =
                                event.getRawX() - downX;

                        float threshold = Math.max(
                                dp(48),
                                getWidth() * 0.28f
                        );

                        if (Math.abs(distanceX) >= threshold) {
                            dismissCard(distanceX > 0f);
                        } else {
                            snapCardBack();
                        }

                        return true;
                    }

                    cancelChildTouch(event);
                    performClick();

                    return true;

                case MotionEvent.ACTION_CANCEL:
                    allowParentIntercept(true);

                    if (dragging) {
                        dragging = false;
                        snapCardBack();
                    }

                    gestureCancelled = true;
                    break;

                default:
                    break;
            }

            super.dispatchTouchEvent(event);

            return true;
        }

        private void cancelChildTouch(MotionEvent source) {
            MotionEvent cancelEvent =
                    MotionEvent.obtain(source);

            cancelEvent.setAction(MotionEvent.ACTION_CANCEL);

            super.dispatchTouchEvent(cancelEvent);

            cancelEvent.recycle();
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }
    }
}