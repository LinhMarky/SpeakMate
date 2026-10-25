package com.example.speakmate;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MatchFragment extends Fragment {

    private static final String ARG_DECK_ID = "deckId";
    private static final String PREFS_RECORDS =
            "speakmate_match_records";

    private static final int COLOR_SURFACE = 0xFF24233F;
    private static final int COLOR_BORDER = 0xFF45416B;
    private static final int COLOR_SELECTED = 0xFF20D9F4;
    private static final int COLOR_SUCCESS = 0xFF35D6A0;
    private static final int COLOR_ERROR = 0xFFFF668C;
    private static final int COLOR_WARNING = 0xFFFFC66D;
    private static final int COLOR_TEXT = 0xFFF5F3FF;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private List<Flashcard> allCards = new ArrayList<>();
    private List<Flashcard> roundCards = new ArrayList<>();

    /*
     * Mỗi từ tạo hai mã:
     * pairIndex * 2     = từ tiếng Anh.
     * pairIndex * 2 + 1 = nghĩa tiếng Việt.
     */
    private final ArrayList<Integer> tileOrder =
            new ArrayList<>();

    private final ArrayList<Integer> matchedPairs =
            new ArrayList<>();

    private final ArrayList<TextView> tileViews =
            new ArrayList<>();

    private String deckId = "";
    private String deckName = "";
    private String recordKey = "";

    private boolean started;
    private boolean finished;
    private boolean locked;
    private boolean newRecord;

    private int selectedPosition = -1;
    private int mistakeCount;

    private long activeMillis;
    private long segmentStart = -1L;

    private long finalMillis;
    private long bestMillis;

    private View introPanel;
    private View gamePanel;
    private View resultPanel;

    private View startButton;
    private View restartTopButton;

    private TextView deckNameText;
    private TextView introText;
    private TextView timerText;
    private TextView pairsText;
    private TextView feedbackText;

    private TextView resultTitleText;
    private TextView resultTimeText;
    private TextView bestText;
    private TextView resultDetailsText;

    private GridLayout grid;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (timerText == null
                    || !started
                    || finished
                    || segmentStart < 0) {
                return;
            }

            updateTimer();

            handler.postDelayed(this, 100);
        }
    };

    public MatchFragment() {
        super(R.layout.fragment_match);
    }

    public static MatchFragment newInstance(String deckId) {
        MatchFragment fragment = new MatchFragment();

        Bundle arguments = new Bundle();
        arguments.putString(ARG_DECK_ID, deckId);

        fragment.setArguments(arguments);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arguments = getArguments();

        deckId = arguments == null
                ? ""
                : arguments.getString(ARG_DECK_ID, "");

        if (deckId == null) {
            deckId = "";
        }

        if (savedInstanceState != null
                && savedInstanceState.containsKey("all_cards")) {
            restoreSession(savedInstanceState);
        } else {
            loadDeck();
        }

        /*
         * Nội dung bộ thẻ thay đổi thì bắt đầu bảng kỷ lục mới.
         * Các tập con ngẫu nhiên của cùng bộ dùng chung kỷ lục.
         */
        String fingerprint = UUID.nameUUIDFromBytes(
                DeckSessionData.encode(allCards)
                        .getBytes(StandardCharsets.UTF_8)
        ).toString();

        recordKey = deckId
                + ":"
                + Math.min(6, allCards.size())
                + ":"
                + fingerprint;
    }

    private void loadDeck() {
        DemoFlashcardRepository repository =
                new DemoFlashcardRepository(requireContext());

        FlashcardDeck deck =
                repository.getDeckById(deckId);

        allCards = new ArrayList<>();

        if (deck != null) {
            deckName = deck.getName();
            allCards.addAll(repository.getCards(deckId));
        }
    }

    private void restoreSession(Bundle state) {
        allCards = DeckSessionData.decode(
                state.getString("all_cards", "[]")
        );

        roundCards = DeckSessionData.decode(
                state.getString("round_cards", "[]")
        );

        deckName = state.getString("deck_name", "");

        ArrayList<Integer> savedOrder =
                state.getIntegerArrayList("tile_order");

        ArrayList<Integer> savedMatches =
                state.getIntegerArrayList("matched_pairs");

        tileOrder.clear();
        matchedPairs.clear();

        if (savedOrder != null) {
            tileOrder.addAll(savedOrder);
        }

        if (savedMatches != null) {
            matchedPairs.addAll(savedMatches);
        }

        started = state.getBoolean("started", false);
        finished = state.getBoolean("finished", false);

        mistakeCount = state.getInt("mistakes", 0);

        activeMillis = state.getLong("active_millis", 0);
        finalMillis = state.getLong("final_millis", 0);
        bestMillis = state.getLong("best_millis", 0);

        newRecord = state.getBoolean("new_record", false);

        selectedPosition = -1;
        locked = false;
        segmentStart = -1L;
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);

        view.findViewById(R.id.btnCloseMatch)
                .setOnClickListener(v -> closeMatch());

        view.findViewById(R.id.btnBackMatchDeck)
                .setOnClickListener(v -> closeMatch());

        startButton.setOnClickListener(v -> startGame());

        view.findViewById(R.id.btnPlayMatchAgain)
                .setOnClickListener(v -> startGame());

        restartTopButton.setOnClickListener(v -> startGame());
    }

    @Override
    public void onViewStateRestored(
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewStateRestored(savedInstanceState);
        render();
    }

    private void bindViews(View view) {
        introPanel =
                view.findViewById(R.id.panelMatchIntro);

        gamePanel =
                view.findViewById(R.id.panelMatchGame);

        resultPanel =
                view.findViewById(R.id.panelMatchResult);

        startButton =
                view.findViewById(R.id.btnStartMatch);

        restartTopButton =
                view.findViewById(R.id.btnRestartMatchTop);

        deckNameText =
                view.findViewById(R.id.tvMatchDeckName);

        introText =
                view.findViewById(R.id.tvMatchIntro);

        timerText =
                view.findViewById(R.id.tvMatchTimer);

        pairsText =
                view.findViewById(R.id.tvMatchPairs);

        feedbackText =
                view.findViewById(R.id.tvMatchFeedback);

        resultTitleText =
                view.findViewById(R.id.tvMatchResultTitle);

        resultTimeText =
                view.findViewById(R.id.tvMatchResultTime);

        bestText =
                view.findViewById(R.id.tvMatchBest);

        resultDetailsText =
                view.findViewById(R.id.tvMatchResultDetails);

        grid =
                view.findViewById(R.id.gridMatchCards);
    }

    private boolean canInteract() {
        return getView() != null
                && isResumed()
                && !getParentFragmentManager().isStateSaved();
    }

    private void startGame() {
        if (!canInteract() || allCards.isEmpty()) {
            return;
        }

        stopClock();
        handler.removeCallbacksAndMessages(null);

        ArrayList<Flashcard> shuffled =
                new ArrayList<>(allCards);

        Collections.shuffle(shuffled);

        int pairCount = Math.min(6, shuffled.size());

        roundCards = new ArrayList<>(
                shuffled.subList(0, pairCount)
        );

        tileOrder.clear();

        for (int i = 0; i < pairCount * 2; i++) {
            tileOrder.add(i);
        }

        Collections.shuffle(tileOrder);

        matchedPairs.clear();

        selectedPosition = -1;
        mistakeCount = 0;
        activeMillis = 0;

        finalMillis = 0;
        bestMillis = 0;

        locked = false;
        newRecord = false;
        started = true;
        finished = false;

        render();
        startClock();
    }

    private long elapsedActiveMillis() {
        if (segmentStart < 0) {
            return activeMillis;
        }

        return activeMillis
                + SystemClock.elapsedRealtime()
                - segmentStart;
    }

    private long currentScoreMillis() {
        /*
         * Đúng quy tắc đã chốt:
         * bắt đầu 0,1 giây, mỗi lần sai trừ 1 giây.
         */
        return Math.max(
                100L,
                100L
                        + elapsedActiveMillis()
                        - mistakeCount * 1000L
        );
    }

    private String formatTime(long millis) {
        return String.format(
                new Locale("vi", "VN"),
                "%.1f giây",
                millis / 1000.0
        );
    }

    private void startClock() {
        if (!started
                || finished
                || segmentStart >= 0
                || timerText == null) {
            return;
        }

        segmentStart = SystemClock.elapsedRealtime();

        handler.removeCallbacks(timerRunnable);
        handler.post(timerRunnable);
    }

    private void stopClock() {
        if (segmentStart >= 0) {
            activeMillis +=
                    SystemClock.elapsedRealtime() - segmentStart;

            segmentStart = -1L;
        }

        handler.removeCallbacks(timerRunnable);
    }

    private void updateTimer() {
        if (timerText != null) {
            timerText.setText(
                    formatTime(currentScoreMillis())
            );
        }
    }

    private void render() {
        if (grid == null) {
            return;
        }

        deckNameText.setText(deckName);

        introPanel.setVisibility(
                started ? View.GONE : View.VISIBLE
        );

        gamePanel.setVisibility(
                started && !finished ? View.VISIBLE : View.GONE
        );

        resultPanel.setVisibility(
                finished ? View.VISIBLE : View.GONE
        );

        restartTopButton.setVisibility(
                started ? View.VISIBLE : View.GONE
        );

        if (!started) {
            int pairs = Math.min(6, allCards.size());

            introText.setText(
                    pairs == 0
                            ? "Bộ thẻ chưa có từ vựng để chơi."
                            : "Ghép " + pairs
                              + " từ tiếng Anh với nghĩa tương ứng."
                              + "\nHoàn thành tất cả các cặp!"
            );

            startButton.setEnabled(pairs > 0);
            return;
        }

        if (finished) {
            renderResult();
            return;
        }

        updateTimer();
        updatePairCount();

        feedbackText.setText("Chọn hai thẻ tương ứng");
        feedbackText.setTextColor(Color.parseColor("#B8B5D6"));

        buildBoard();
    }

    private void updatePairCount() {
        pairsText.setText(
                matchedPairs.size()
                        + " / "
                        + roundCards.size()
                        + " cặp đã ghép"
        );
    }

    private void buildBoard() {
        grid.removeAllViews();
        tileViews.clear();

        selectedPosition = -1;
        locked = false;

        grid.setColumnCount(3);

        for (int position = 0;
             position < tileOrder.size();
             position++) {

            int tileCode = tileOrder.get(position);
            int pairIndex = tileCode / 2;

            Flashcard card = roundCards.get(pairIndex);

            boolean wordSide = tileCode % 2 == 0;

            TextView tile = new TextView(requireContext());

            tile.setText(
                    wordSide
                            ? card.getWord()
                            : card.getMeaning()
            );

            tile.setTextColor(COLOR_TEXT);
            tile.setTextSize(16);
            tile.setGravity(Gravity.CENTER);

            tile.setPadding(
                    dp(8),
                    dp(12),
                    dp(8),
                    dp(12)
            );

            tile.setMinHeight(dp(112));
            tile.setClickable(true);
            tile.setFocusable(true);
            tile.setSaveEnabled(false);

            tile.setBackground(
                    tileBackground(COLOR_BORDER)
            );

            GridLayout.LayoutParams params =
                    new GridLayout.LayoutParams();

            params.width = 0;
            params.height =
                    GridLayout.LayoutParams.WRAP_CONTENT;

            params.columnSpec = GridLayout.spec(
                    position % 3,
                    1f
            );

            params.rowSpec = GridLayout.spec(position / 3);

            params.setMargins(
                    dp(4),
                    dp(5),
                    dp(4),
                    dp(5)
            );

            grid.addView(tile, params);
            tileViews.add(tile);

            if (matchedPairs.contains(pairIndex)) {
                tile.setVisibility(View.INVISIBLE);
                tile.setEnabled(false);
            }

            final int selectedTile = position;

            tile.setOnClickListener(
                    v -> selectTile(selectedTile)
            );
        }
    }

    private GradientDrawable tileBackground(int borderColor) {
        GradientDrawable drawable = new GradientDrawable();

        drawable.setColor(COLOR_SURFACE);
        drawable.setCornerRadius(dp(16));
        drawable.setStroke(dp(2), borderColor);

        return drawable;
    }

    private void paintTile(int position, int color) {
        if (position < 0 || position >= tileViews.size()) {
            return;
        }

        tileViews.get(position)
                .setBackground(tileBackground(color));
    }

    private void selectTile(int position) {
        if (!canInteract()
                || locked
                || finished
                || !started
                || position < 0
                || position >= tileOrder.size()) {
            return;
        }

        int code = tileOrder.get(position);
        int pairIndex = code / 2;

        if (matchedPairs.contains(pairIndex)) {
            return;
        }

        if (selectedPosition < 0) {
            selectedPosition = position;
            paintTile(position, COLOR_SELECTED);
            return;
        }

        // Nhấn lại thẻ đang chọn thì bỏ chọn.
        if (selectedPosition == position) {
            paintTile(position, COLOR_BORDER);
            selectedPosition = -1;
            return;
        }

        int firstPosition = selectedPosition;
        int firstCode = tileOrder.get(firstPosition);

        selectedPosition = -1;

        boolean samePair = firstCode / 2 == code / 2;
        boolean oppositeSides = firstCode % 2 != code % 2;

        if (samePair && oppositeSides) {
            matchedPairs.add(pairIndex);

            hideMatchedTile(firstPosition);
            hideMatchedTile(position);

            feedbackText.setText("✓ Ghép đúng!");
            feedbackText.setTextColor(COLOR_SUCCESS);

            updatePairCount();

            if (matchedPairs.size() == roundCards.size()) {
                finishGame();
            }

            return;
        }

        mistakeCount++;

        updateTimer();

        feedbackText.setText("Ghép sai: −1 giây");
        feedbackText.setTextColor(COLOR_WARNING);

        paintTile(firstPosition, COLOR_ERROR);
        paintTile(position, COLOR_ERROR);

        locked = true;

        /*
         * Chặn chọn tiếp trong khi đang báo sai.
         * Sau 600ms cho phép thử lại.
         */
        handler.postDelayed(() -> {
            if (grid == null) {
                return;
            }

            paintTile(firstPosition, COLOR_BORDER);
            paintTile(position, COLOR_BORDER);

            locked = false;
        }, 600);
    }

    private void hideMatchedTile(int position) {
        TextView tile = tileViews.get(position);

        tile.setVisibility(View.INVISIBLE);
        tile.setEnabled(false);
    }

    private SharedPreferences recordPreferences() {
        return requireContext().getSharedPreferences(
                PREFS_RECORDS,
                Context.MODE_PRIVATE
        );
    }

    private void finishGame() {
        if (finished) {
            return;
        }

        stopClock();

        finalMillis = currentScoreMillis();
        finished = true;
        locked = false;

        SharedPreferences preferences = recordPreferences();

        long previousBest = preferences.getLong(
                recordKey,
                Long.MAX_VALUE
        );

        newRecord = finalMillis < previousBest;

        bestMillis = newRecord
                ? finalMillis
                : previousBest;

        if (newRecord) {
            preferences.edit()
                    .putLong(recordKey, bestMillis)
                    .apply();
        }

        render();
    }

    private void renderResult() {
        resultTitleText.setText(
                newRecord
                        ? "Kỷ lục mới!\nTốt lắm, rất nhanh."
                        : "Hoàn thành!\nTốt lắm."
        );

        resultTimeText.setText(
                "Hoàn thành trong " + formatTime(finalMillis)
        );

        bestText.setText(formatTime(bestMillis));

        resultDetailsText.setText(
                "Đã ghép: " + roundCards.size() + " cặp"
                        + "\nSố lần ghép sai: " + mistakeCount
        );
    }

    private void closeMatch() {
        if (!isAdded()
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        stopClock();

        getParentFragmentManager()
                .popBackStackImmediate();
    }

    private int dp(float value) {
        return Math.round(
                value * getResources()
                        .getDisplayMetrics().density
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        startClock();
    }

    @Override
    public void onPause() {
        stopClock();
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString(
                "all_cards",
                DeckSessionData.encode(allCards)
        );

        outState.putString(
                "round_cards",
                DeckSessionData.encode(roundCards)
        );

        outState.putString("deck_name", deckName);

        outState.putIntegerArrayList(
                "tile_order",
                new ArrayList<>(tileOrder)
        );

        outState.putIntegerArrayList(
                "matched_pairs",
                new ArrayList<>(matchedPairs)
        );

        outState.putBoolean("started", started);
        outState.putBoolean("finished", finished);
        outState.putBoolean("new_record", newRecord);

        outState.putInt("mistakes", mistakeCount);

        outState.putLong(
                "active_millis",
                elapsedActiveMillis()
        );

        outState.putLong("final_millis", finalMillis);
        outState.putLong("best_millis", bestMillis);
    }

    @Override
    public void onDestroyView() {
        stopClock();

        handler.removeCallbacksAndMessages(null);

        tileViews.clear();
        selectedPosition = -1;
        locked = false;

        introPanel = null;
        gamePanel = null;
        resultPanel = null;

        startButton = null;
        restartTopButton = null;

        deckNameText = null;
        introText = null;
        timerText = null;
        pairsText = null;
        feedbackText = null;

        resultTitleText = null;
        resultTimeText = null;
        bestText = null;
        resultDetailsText = null;

        grid = null;

        super.onDestroyView();
    }
}