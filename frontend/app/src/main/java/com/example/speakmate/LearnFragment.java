package com.example.speakmate;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList;
import java.util.List;

public class LearnFragment extends Fragment {

    private static final String ARG_DECK_ID = "deckId";

    private static final String STATE_CARDS = "learn_cards";
    private static final String STATE_ANSWERS = "learn_answers";
    private static final String STATE_INDEX = "learn_index";
    private static final String STATE_DRAFT = "learn_draft";
    private static final String STATE_NAME = "learn_name";

    private final ArrayList<String> answers = new ArrayList<>();

    private List<Flashcard> cards = new ArrayList<>();

    private String deckName = "";
    private String draft = "";

    private int currentIndex;
    private boolean submitted;

    private ScrollView scroll;
    private View questionPanel;
    private View resultPanel;
    private View feedbackPanel;

    private View checkButton;
    private TextView nextButton;
    private View restartButton;

    private TextView headerText;
    private TextView counterText;
    private TextView deckNameText;
    private TextView meaningText;

    private EditText answerInput;

    private TextView feedbackText;
    private TextView correctAnswerText;
    private TextView exampleText;

    private ProgressBar progressBar;
    private CircularProgressIndicator accuracyProgress;

    private TextView percentText;
    private TextView resultTitleText;
    private TextView resultSummaryText;
    private LinearLayout answerList;

    public LearnFragment() {
        super(R.layout.fragment_learn);
    }

    public static LearnFragment newInstance(String deckId) {
        LearnFragment fragment = new LearnFragment();

        Bundle arguments = new Bundle();
        arguments.putString(ARG_DECK_ID, deckId);

        fragment.setArguments(arguments);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null
                && savedInstanceState.containsKey(STATE_CARDS)) {
            restoreSession(savedInstanceState);
        } else {
            loadDeck();
        }
    }

    private void loadDeck() {
        Bundle arguments = getArguments();

        String deckId = arguments == null
                ? ""
                : arguments.getString(ARG_DECK_ID, "");

        DemoFlashcardRepository repository =
                new DemoFlashcardRepository(requireContext());

        FlashcardDeck deck = repository.getDeckById(deckId);

        cards = new ArrayList<>();

        if (deck != null) {
            deckName = deck.getName();
            cards.addAll(repository.getCards(deckId));
        }

        answers.clear();
        currentIndex = 0;
        submitted = false;
        draft = "";
    }

    private void restoreSession(Bundle state) {
        cards = DeckSessionData.decode(
                state.getString(STATE_CARDS, "[]")
        );

        deckName = state.getString(STATE_NAME, "");
        draft = state.getString(STATE_DRAFT, "");

        ArrayList<String> savedAnswers =
                state.getStringArrayList(STATE_ANSWERS);

        answers.clear();

        if (savedAnswers != null) {
            for (String answer : savedAnswers) {
                if (answers.size() >= cards.size()) {
                    break;
                }

                answers.add(answer == null ? "" : answer);
            }
        }

        currentIndex = Math.max(
                0,
                Math.min(
                        state.getInt(STATE_INDEX, 0),
                        answers.size()
                )
        );

        currentIndex = Math.min(
                currentIndex,
                cards.size()
        );

        // Đã có đáp án tại vị trí này nghĩa là đang xem phản hồi.
        submitted = currentIndex < answers.size();
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);

        view.findViewById(R.id.btnCloseLearn)
                .setOnClickListener(v -> closeLearn());

        view.findViewById(R.id.btnBackLearnDeck)
                .setOnClickListener(v -> closeLearn());

        checkButton.setOnClickListener(v -> checkAnswer());

        nextButton.setOnClickListener(v -> nextQuestion());

        restartButton.setOnClickListener(v -> restart());

        answerInput.setOnEditorActionListener(
                (textView, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        checkAnswer();
                        return true;
                    }

                    return false;
                }
        );
    }

    @Override
    public void onViewStateRestored(
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewStateRestored(savedInstanceState);

        // Hiển thị sau khi Android khôi phục trạng thái giao diện.
        render();
    }

    private void bindViews(View view) {
        scroll = view.findViewById(R.id.scrollLearn);

        questionPanel =
                view.findViewById(R.id.panelLearnQuestion);

        resultPanel =
                view.findViewById(R.id.panelLearnResult);

        feedbackPanel =
                view.findViewById(R.id.panelLearnFeedback);

        checkButton =
                view.findViewById(R.id.btnCheckLearn);

        nextButton =
                view.findViewById(R.id.btnNextLearn);

        restartButton =
                view.findViewById(R.id.btnRestartLearn);

        headerText =
                view.findViewById(R.id.tvLearnHeader);

        counterText =
                view.findViewById(R.id.tvLearnCounter);

        deckNameText =
                view.findViewById(R.id.tvLearnDeckName);

        meaningText =
                view.findViewById(R.id.tvLearnMeaning);

        answerInput =
                view.findViewById(R.id.etLearnAnswer);

        feedbackText =
                view.findViewById(R.id.tvLearnFeedback);

        correctAnswerText =
                view.findViewById(R.id.tvLearnCorrectAnswer);

        exampleText =
                view.findViewById(R.id.tvLearnExample);

        progressBar =
                view.findViewById(R.id.progressLearn);

        accuracyProgress =
                view.findViewById(R.id.progressLearnAccuracy);

        percentText =
                view.findViewById(R.id.tvLearnPercent);

        resultTitleText =
                view.findViewById(R.id.tvLearnResultTitle);

        resultSummaryText =
                view.findViewById(R.id.tvLearnResultSummary);

        answerList =
                view.findViewById(R.id.learnAnswerList);
    }

    private boolean isFinished() {
        return currentIndex >= cards.size();
    }

    private boolean isCorrect(int index) {
        if (index < 0
                || index >= answers.size()
                || index >= cards.size()) {
            return false;
        }

        return DeckSessionData.normalize(answers.get(index))
                .equals(
                        DeckSessionData.normalize(
                                cards.get(index).getWord()
                        )
                );
    }

    private int getCorrectCount() {
        int count = 0;

        for (int i = 0; i < answers.size(); i++) {
            if (isCorrect(i)) {
                count++;
            }
        }

        return count;
    }

    private void checkAnswer() {
        if (answerInput == null
                || submitted
                || isFinished()) {
            return;
        }

        String answer =
                answerInput.getText().toString().trim();

        if (answer.isEmpty()) {
            answerInput.setError("Vui lòng nhập câu trả lời.");
            answerInput.requestFocus();
            return;
        }

        /*
         * Chỉ ghi nhận một lần cho mỗi câu.
         * Các câu trước đã có đúng currentIndex đáp án.
         */
        if (answers.size() != currentIndex) {
            return;
        }

        answers.add(answer);
        submitted = true;
        draft = "";

        hideKeyboard();
        render();
    }

    private void nextQuestion() {
        if (!submitted || isFinished()) {
            return;
        }

        currentIndex++;
        submitted = false;
        draft = "";

        render();
        scrollToTop();
    }

    private void restart() {
        if (cards.isEmpty()) {
            return;
        }

        answers.clear();
        currentIndex = 0;
        submitted = false;
        draft = "";

        render();
        scrollToTop();
    }

    private void render() {
        if (getView() == null || answerInput == null) {
            return;
        }

        boolean finished = isFinished();
        int total = cards.size();
        int completed = answers.size();

        questionPanel.setVisibility(
                finished ? View.GONE : View.VISIBLE
        );

        resultPanel.setVisibility(
                finished ? View.VISIBLE : View.GONE
        );

        progressBar.setMax(100);

        progressBar.setProgress(
                total == 0
                        ? 0
                        : Math.round(completed * 100f / total)
        );

        if (finished) {
            renderResult();
            return;
        }

        headerText.setText("Luyện từ vựng");

        counterText.setText(
                (currentIndex + 1) + " / " + total
        );

        deckNameText.setText(deckName);

        Flashcard card = cards.get(currentIndex);

        meaningText.setText(card.getMeaning());

        answerInput.setError(null);
        answerInput.setEnabled(!submitted);

        checkButton.setVisibility(
                submitted ? View.GONE : View.VISIBLE
        );

        nextButton.setVisibility(
                submitted ? View.VISIBLE : View.GONE
        );

        feedbackPanel.setVisibility(
                submitted ? View.VISIBLE : View.GONE
        );

        nextButton.setText(
                currentIndex == total - 1
                        ? "Xem kết quả"
                        : "Tiếp tục"
        );

        if (submitted) {
            answerInput.setText(answers.get(currentIndex));

            boolean correct = isCorrect(currentIndex);

            feedbackText.setText(
                    correct ? "✓ Chính xác!" : "Chưa đúng"
            );

            feedbackText.setTextColor(
                    Color.parseColor(
                            correct ? "#35D6A0" : "#FFC66D"
                    )
            );

            correctAnswerText.setText(
                    "Đáp án: " + card.getWord()
            );

            String example = card.getExample();

            boolean hasExample = example != null
                    && !example.trim().isEmpty();

            exampleText.setText(example);

            exampleText.setVisibility(
                    hasExample ? View.VISIBLE : View.GONE
            );
        } else {
            answerInput.setText(draft);

            answerInput.setSelection(
                    answerInput.getText().length()
            );
        }
    }

    private void renderResult() {
        hideKeyboard();

        int total = cards.size();
        int correct = getCorrectCount();

        int percentage = total == 0
                ? 0
                : Math.round(correct * 100f / total);

        headerText.setText("Kết quả cuối cùng");

        counterText.setText(
                answers.size() + " / " + total
        );

        accuracyProgress.setProgressCompat(
                percentage,
                false
        );

        percentText.setText(percentage + "%");

        resultTitleText.setText(
                total == 0 ? "Chưa có từ để học" : "Tốt lắm!"
        );

        resultSummaryText.setText(
                total == 0
                        ? "Vui lòng quay về và chọn bộ thẻ có từ vựng."
                        : "Bạn trả lời đúng " + correct
                          + "/" + total + " câu."
                          + "\nCùng xem lại kết quả bên dưới."
        );

        restartButton.setEnabled(total > 0);

        renderAnswerList();
    }

    private void renderAnswerList() {
        answerList.removeAllViews();

        for (int i = 0; i < answers.size(); i++) {
            Flashcard card = cards.get(i);
            boolean correct = isCorrect(i);

            /*
             * Dùng lại layout từ vựng đã có trong dự án.
             * Không cần tạo thêm file XML cho từng dòng.
             */
            View item = getLayoutInflater().inflate(
                    R.layout.item_vocabulary,
                    answerList,
                    false
            );

            TextView word =
                    item.findViewById(R.id.tvVocabularyWord);

            TextView meaning =
                    item.findViewById(R.id.tvVocabularyMeaning);

            TextView detail =
                    item.findViewById(R.id.tvVocabularyExample);

            word.setText(
                    (correct ? "✓ " : "✕ ") + card.getWord()
            );

            word.setTextColor(
                    Color.parseColor(
                            correct ? "#35D6A0" : "#FFC66D"
                    )
            );

            meaning.setText(card.getMeaning());

            String detailValue = correct
                    ? "Bạn trả lời đúng."
                    : "Bạn nhập: " + answers.get(i)
                      + "\nĐáp án: " + card.getWord();

            if (card.getExample() != null
                    && !card.getExample().trim().isEmpty()) {
                detailValue += "\n" + card.getExample();
            }

            detail.setText(detailValue);
            detail.setVisibility(View.VISIBLE);

            answerList.addView(item);
        }
    }

    private void scrollToTop() {
        if (scroll == null) {
            return;
        }

        ScrollView currentScroll = scroll;

        currentScroll.post(() -> {
            if (scroll == currentScroll) {
                currentScroll.scrollTo(0, 0);
            }
        });
    }

    private void hideKeyboard() {
        View view = getView();

        if (view == null) {
            return;
        }

        InputMethodManager manager =
                (InputMethodManager) requireContext()
                        .getSystemService(
                                Context.INPUT_METHOD_SERVICE
                        );

        if (manager != null) {
            manager.hideSoftInputFromWindow(
                    view.getWindowToken(),
                    0
            );
        }
    }

    private void closeLearn() {
        if (!isAdded()
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        hideKeyboard();

        getParentFragmentManager()
                .popBackStackImmediate();
    }

    private void captureDraft() {
        if (answerInput != null
                && !submitted
                && !isFinished()) {
            draft = answerInput.getText().toString();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        captureDraft();

        outState.putString(
                STATE_CARDS,
                DeckSessionData.encode(cards)
        );

        outState.putStringArrayList(
                STATE_ANSWERS,
                new ArrayList<>(answers)
        );

        outState.putInt(STATE_INDEX, currentIndex);
        outState.putString(STATE_DRAFT, draft);
        outState.putString(STATE_NAME, deckName);
    }

    @Override
    public void onDestroyView() {
        captureDraft();

        if (answerInput != null) {
            answerInput.setOnEditorActionListener(null);
        }

        scroll = null;
        questionPanel = null;
        resultPanel = null;
        feedbackPanel = null;

        checkButton = null;
        nextButton = null;
        restartButton = null;

        headerText = null;
        counterText = null;
        deckNameText = null;
        meaningText = null;
        answerInput = null;

        feedbackText = null;
        correctAnswerText = null;
        exampleText = null;

        progressBar = null;
        accuracyProgress = null;

        percentText = null;
        resultTitleText = null;
        resultSummaryText = null;
        answerList = null;

        super.onDestroyView();
    }
}