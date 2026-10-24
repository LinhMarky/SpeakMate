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

    private static final int COLOR_SURFACE =
            Color.rgb(36, 35, 63);

    private static final int COLOR_BORDER =
            Color.rgb(69, 65, 107);

    private static final int COLOR_SUCCESS =
            Color.rgb(53, 214, 160);

    private static final int COLOR_WARNING =
            Color.rgb(255, 198, 109);

    /*
     * Kết quả trong lượt học hiện tại:
     * 1 = đã nhớ.
     * 0 = chưa thuộc.
     */
    private final ArrayList<Integer> answers = new ArrayList<>();

    private List<Flashcard> cards = new ArrayList<>();

    private String currentDeckId = "";
    private FlashcardProgressStore progressStore;

    private View rootView;
    private SwipeCardLayout cardView;

    private View studyStage;
    private View studyActions;
    private View studyResult;

    private View rememberedButton;
    private View notRememberedButton;
    private View restartButton;

    private TextView titleText;
    private TextView progressText;
    private TextView rememberedCountText;
    private TextView notRememberedCountText;

    private TextView wordText;
    private TextView meaningText;
    private TextView exampleText;
    private TextView flipHintText;
    private TextView swipeLabel;
    private TextView swipeHintText;

    private TextView resultTitleText;
    private TextView resultText;

    private ProgressBar progressBar;

    private boolean flipped;
    private boolean animating;
    private boolean dragging;
    private boolean gestureCancelled;

    private float downX;
    private float downY;

    private int touchSlop;

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

        ArrayList<Integer> savedAnswers =
                savedInstanceState.getIntegerArrayList(STATE_ANSWERS);

        answers.clear();

        if (savedAnswers != null) {
            for (Integer answer : savedAnswers) {
                if (answer != null && (answer == 0 || answer == 1)) {
                    answers.add(answer);
                }
            }
        }

        flipped = savedInstanceState.getBoolean(
                STATE_FLIPPED,
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
        bindViews(view);
        loadDeck();

        view.findViewById(R.id.btnCloseStudy)
                .setOnClickListener(v -> closeStudy());

        view.findViewById(R.id.btnBackToDeck)
                .setOnClickListener(v -> closeStudy());

        cardView.setOnClickListener(v -> {
            if (animating || isFinished()) {
                return;
            }

            flipped = !flipped;
            render();
        });

        rememberedButton.setOnClickListener(
                v -> dismissCard(true)
        );

        notRememberedButton.setOnClickListener(
                v -> dismissCard(false)
        );

        restartButton.setOnClickListener(v -> {
            if (animating || cards.isEmpty()) {
                return;
            }

            /*
             * Chỉ bắt đầu lại lượt học.
             * Trạng thái từng từ đã lưu vẫn được giữ,
             * và sẽ cập nhật khi người dùng đánh giá lại.
             */
            answers.clear();

            flipped = false;
            dragging = false;
            gestureCancelled = false;

            render();
        });

        render();
    }

    private void setupSwipeCard(View view) {
        FrameLayout originalCard =
                view.findViewById(R.id.studyCard);

        ViewGroup parent =
                (ViewGroup) originalCard.getParent();

        int cardIndex = parent.indexOfChild(originalCard);

        ViewGroup.LayoutParams layoutParams =
                originalCard.getLayoutParams();

        cardView = new SwipeCardLayout();

        while (originalCard.getChildCount() > 0) {
            View child = originalCard.getChildAt(0);

            originalCard.removeViewAt(0);
            cardView.addView(child);
        }

        parent.removeViewAt(cardIndex);

        cardView.setId(R.id.studyCard);
        cardView.setLayoutParams(layoutParams);
        cardView.setClickable(true);
        cardView.setFocusable(true);

        cardView.setContentDescription(
                "Thẻ từ vựng. Chạm để xem nghĩa"
        );

        parent.addView(cardView, cardIndex);
    }

    private void bindViews(View view) {
        studyStage =
                view.findViewById(R.id.studyStage);

        studyActions =
                view.findViewById(R.id.studyActions);

        studyResult =
                view.findViewById(R.id.studyResult);

        rememberedButton =
                view.findViewById(R.id.btnRemembered);

        notRememberedButton =
                view.findViewById(R.id.btnNotRemembered);

        restartButton =
                view.findViewById(R.id.btnRestartStudy);

        titleText =
                view.findViewById(R.id.tvStudyTitle);

        progressText =
                view.findViewById(R.id.tvStudyProgress);

        rememberedCountText =
                view.findViewById(R.id.tvRememberedCount);

        notRememberedCountText =
                view.findViewById(R.id.tvNotRememberedCount);

        wordText =
                view.findViewById(R.id.tvStudyWord);

        meaningText =
                view.findViewById(R.id.tvStudyMeaning);

        exampleText =
                view.findViewById(R.id.tvStudyExample);

        flipHintText =
                view.findViewById(R.id.tvFlipHint);

        swipeLabel =
                view.findViewById(R.id.tvSwipeLabel);

        swipeHintText =
                view.findViewById(R.id.tvSwipeHint);

        resultTitleText =
                view.findViewById(R.id.tvStudyResultTitle);

        resultText =
                view.findViewById(R.id.tvStudyResult);

        progressBar =
                view.findViewById(R.id.progressStudy);
    }

    private void loadDeck() {
        currentDeckId = "";

        Bundle arguments = getArguments();

        if (arguments != null) {
            String deckId = arguments.getString(ARG_DECK_ID);

            if (deckId != null) {
                currentDeckId = deckId;
            }
        }

        DemoFlashcardRepository repository =
                new DemoFlashcardRepository(requireContext());

        FlashcardDeck deck =
                repository.getDeckById(currentDeckId);

        cards = new ArrayList<>();

        if (deck == null) {
            titleText.setText("Không tìm thấy bộ thẻ");
        } else {
            titleText.setText(deck.getName());

            List<Flashcard> loadedCards =
                    repository.getCards(currentDeckId);

            if (loadedCards != null) {
                cards.addAll(loadedCards);
            }
        }

        while (answers.size() > cards.size()) {
            answers.remove(answers.size() - 1);
        }
    }

    private void closeStudy() {
        if (!isAdded()
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        getParentFragmentManager().popBackStackImmediate();
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

    /*
     * Lưu trạng thái của thẻ hiện tại trên máy,
     * sau đó ghi nhận kết quả trong lượt học.
     */
    private void recordAnswer(boolean remembered) {
        if (progressStore == null || isFinished()) {
            return;
        }

        Flashcard currentCard =
                cards.get(answers.size());

        progressStore.saveAnswer(
                currentDeckId,
                currentCard,
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
        int notRemembered = completed - remembered;

        boolean finished = isFinished();

        rememberedCountText.setText(
                "Đã nhớ: " + remembered
        );

        notRememberedCountText.setText(
                "Chưa thuộc: " + notRemembered
        );

        progressText.setText(
                completed + "/" + total + " thẻ"
        );

        int percentage = total == 0
                ? 0
                : Math.round(completed * 100f / total);

        progressBar.setMax(100);
        progressBar.setProgress(percentage);

        studyStage.setVisibility(
                finished ? View.GONE : View.VISIBLE
        );

        studyActions.setVisibility(
                finished ? View.GONE : View.VISIBLE
        );

        swipeHintText.setVisibility(
                finished ? View.GONE : View.VISIBLE
        );

        studyResult.setVisibility(
                finished ? View.VISIBLE : View.GONE
        );

        restartButton.setEnabled(
                total > 0 && !animating
        );

        resetCardAppearance();

        if (finished) {
            if (total == 0) {
                resultTitleText.setText(
                        "Chưa có thẻ để học"
                );

                resultText.setText(
                        "Bộ thẻ không tồn tại hoặc chưa có từ vựng."
                );
            } else {
                resultTitleText.setText(
                        "Hoàn thành lượt học!"
                );

                resultText.setText(
                        "Đã nhớ: " + remembered
                                + "\nChưa thuộc: " + notRemembered
                                + "\nTổng số thẻ: " + total
                                + "\n\nĐã lưu tiến độ trên thiết bị."
                );
            }

            setAnswerButtonsEnabled(false);
            return;
        }

        Flashcard card = cards.get(completed);

        wordText.setText(card.getWord());
        meaningText.setText(card.getMeaning());
        exampleText.setText(card.getExample());

        meaningText.setVisibility(
                flipped ? View.VISIBLE : View.GONE
        );

        boolean hasExample = card.getExample() != null
                && !card.getExample().trim().isEmpty();

        exampleText.setVisibility(
                flipped && hasExample
                        ? View.VISIBLE
                        : View.GONE
        );

        flipHintText.setText(
                flipped
                        ? "Chạm thẻ để ẩn nghĩa"
                        : "Chạm thẻ để xem nghĩa"
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

        swipeLabel.setVisibility(View.GONE);
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

        swipeLabel.setText(
                remembered ? "Đã nhớ" : "Chưa thuộc"
        );

        swipeLabel.setTextColor(color);

        swipeLabel.setBackground(
                createCardBackground(color, 3)
        );

        swipeLabel.setVisibility(View.VISIBLE);

        cardView.setBackground(
                createCardBackground(color, 3)
        );
    }

    private void setAnswerButtonsEnabled(boolean enabled) {
        rememberedButton.setEnabled(enabled);
        notRememberedButton.setEnabled(enabled);
    }

    private void dismissCard(boolean remembered) {
        if (rootView == null
                || cardView == null
                || animating
                || isFinished()) {
            return;
        }

        if (cardView.getWidth() == 0) {
            recordAnswer(remembered);
            render();
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
                .setInterpolator(
                        new DecelerateInterpolator()
                )
                .withEndAction(() -> {
                    if (rootView == null
                            || cardView != movingCard) {
                        return;
                    }

                    recordAnswer(remembered);

                    animating = false;

                    render();
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
                .setInterpolator(
                        new DecelerateInterpolator()
                )
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

        outState.putBoolean(
                STATE_FLIPPED,
                flipped
        );
    }

    @Override
    public void onDestroyView() {
        allowParentIntercept(true);

        if (cardView != null) {
            cardView.animate().withEndAction(null);
            cardView.animate().cancel();
            cardView.setOnClickListener(null);
        }

        rootView = null;
        cardView = null;

        studyStage = null;
        studyActions = null;
        studyResult = null;

        rememberedButton = null;
        notRememberedButton = null;
        restartButton = null;

        titleText = null;
        progressText = null;
        rememberedCountText = null;
        notRememberedCountText = null;

        wordText = null;
        meaningText = null;
        exampleText = null;
        flipHintText = null;
        swipeLabel = null;
        swipeHintText = null;

        resultTitleText = null;
        resultText = null;
        progressBar = null;
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
            if (animating || isFinished()) {
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

                    float deltaX =
                            event.getRawX() - downX;

                    float deltaY =
                            event.getRawY() - downY;

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
                            Math.max(
                                    -18f,
                                    Math.min(18f, rotation)
                            )
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

            cancelEvent.setAction(
                    MotionEvent.ACTION_CANCEL
            );

            super.dispatchTouchEvent(cancelEvent);

            cancelEvent.recycle();
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }
    }
}