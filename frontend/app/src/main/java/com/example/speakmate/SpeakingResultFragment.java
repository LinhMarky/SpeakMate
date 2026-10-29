package com.example.speakmate;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatCheckBox;
import com.example.speakmate.ui.NetworkFragment;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

import java.util.ArrayList;
import java.util.List;

public class SpeakingResultFragment extends NetworkFragment {

    // ID dương dùng kết quả server; nhánh cũ chỉ phục vụ bản xem trước local.
    private long conversationId;
    private JSONObject serverReview;
    private boolean loadingReview;
    private final Set<Long> selectedSuggestions = new HashSet<>();
    private final Handler reviewPoll = new Handler(Looper.getMainLooper());

    private static final String STATE_SELECTED = "result_selected";
    private static final String STATE_DECK_NAME = "result_deck_name";
    private static final String STATE_CREATED_ID = "result_created_id";

    private final List<Flashcard> vocabulary = new ArrayList<>();
    private final List<AppCompatCheckBox> checkBoxes = new ArrayList<>();

    private boolean[] selected;

    private String level = "A2";
    private String topic = "";
    private int userTurnCount;

    private String draftDeckName;
    private String createdDeckId;

    private EditText deckNameInput;
    private TextView selectedCountText;
    private TextView deckStatusText;

    private View createButton;
    private View openButton;

    private boolean saving;
    private boolean openingDeck;

    public SpeakingResultFragment() {
        super(R.layout.fragment_speaking_result);
    }

    public static SpeakingResultFragment newInstance(long id) {
        SpeakingResultFragment fragment = new SpeakingResultFragment();
        Bundle args = new Bundle();
        args.putLong("conversationId", id);
        fragment.setArguments(args);
        return fragment;
    }

    public static SpeakingResultFragment newInstance(
            String level,
            String topic,
            int userTurnCount
    ) {
        SpeakingResultFragment fragment =
                new SpeakingResultFragment();

        Bundle arguments = new Bundle();

        arguments.putString("level", level);
        arguments.putString("topic", topic);
        arguments.putInt("userTurnCount", userTurnCount);

        fragment.setArguments(arguments);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arguments = getArguments();

        if (arguments != null) {
            conversationId = arguments.getLong("conversationId");
            level = arguments.getString("level", "A2");
            topic = arguments.getString("topic", "");
            userTurnCount = Math.max(
                    0,
                    arguments.getInt("userTurnCount", 0)
            );
        }

        if (level == null || level.trim().isEmpty()) {
            level = "A2";
        }

        if (topic == null || topic.trim().isEmpty()) {
            topic = "Chủ đề luyện nói";
        }

        if (conversationId == 0) prepareVocabulary();

        selected = new boolean[vocabulary.size()];

        for (int i = 0; i < selected.length; i++) {
            selected[i] = true;
        }

        draftDeckName = "Từ vựng - " + topic;

        if (savedInstanceState != null) {
            long[] ids = savedInstanceState.getLongArray("selectedSuggestionIds");
            if (ids != null) for (long id : ids) selectedSuggestions.add(id);
            boolean[] savedSelected =
                    savedInstanceState.getBooleanArray(STATE_SELECTED);

            if (savedSelected != null
                    && savedSelected.length == selected.length) {
                selected = savedSelected;
            }

            draftDeckName = savedInstanceState.getString(
                    STATE_DECK_NAME,
                    draftDeckName
            );

            createdDeckId = savedInstanceState.getString(
                    STATE_CREATED_ID
            );
        }
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        saving = false;
        openingDeck = false;

        deckNameInput =
                view.findViewById(R.id.etResultDeckName);

        selectedCountText =
                view.findViewById(R.id.tvResultSelectedCount);

        deckStatusText =
                view.findViewById(R.id.tvResultDeckStatus);

        createButton =
                view.findViewById(R.id.btnCreateResultDeck);

        openButton =
                view.findViewById(R.id.btnOpenResultDeck);

        if (conversationId > 0) {
            bindServerReview(view);
            return;
        }

        deckNameInput.setText(draftDeckName);

        renderSummary(view);
        renderVocabulary(view);

        View conversationButton =
                view.findViewById(R.id.btnViewResultConversation);

        Bundle resultArguments = getArguments();

        boolean hasConversation = resultArguments != null
                && resultArguments.containsKey("transcriptMessages")
                && resultArguments.containsKey("transcriptUserRoles");

        conversationButton.setVisibility(
                hasConversation ? View.VISIBLE : View.GONE
        );

        conversationButton.setOnClickListener(
                v -> openConversationReview()
        );

        createButton.setOnClickListener(v -> createDeck());
        openButton.setOnClickListener(v -> openCreatedDeck());

        TextView backButton =
                view.findViewById(R.id.btnBackToPractice);

        Bundle arguments = getArguments();

        boolean fromHistory = arguments != null
                && arguments.getBoolean("fromHistory", false);

        backButton.setText(
                fromHistory
                        ? "Quay lại lịch sử"
                        : "Chọn bài luyện tiếp"
        );

        backButton.setOnClickListener(v -> {
            if (!canAct()) {
                return;
            }

            if (fromHistory
                    && getParentFragmentManager()
                    .getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            } else {
                navigateToTab(R.id.nav_speaking);
            }
        });

        view.findViewById(R.id.btnResultHome)
                .setOnClickListener(v ->
                        navigateToTab(R.id.nav_home)
                );
    }

    @Override
    public void onResume() {
        super.onResume();
        if (conversationId > 0) {
            loadServerReview();
            return;
        }

        openingDeck = false;
        refreshCreatedDeckState();

        View view = getView();

        if (view != null) {
            view.findViewById(R.id.btnViewResultConversation)
                    .setEnabled(true);
        }
    }

    private void renderSummary(View view) {
        TextView topicText =
                view.findViewById(R.id.tvResultTopic);

        TextView statsText =
                view.findViewById(R.id.tvResultStats);

        TextView estimatedLevelText =
                view.findViewById(R.id.tvResultEstimatedLevel);

        TextView levelDetailText =
                view.findViewById(R.id.tvResultLevelDetail);

        TextView strengthsText =
                view.findViewById(R.id.tvResultStrengths);

        TextView weaknessesText =
                view.findViewById(R.id.tvResultWeaknesses);

        TextView grammarText =
                view.findViewById(R.id.tvResultGrammar);

        TextView vocabularyNote =
                view.findViewById(R.id.tvResultVocabularyNote);

        topicText.setText(topic);

        statsText.setText(
                "Mức bài luyện: " + level
                        + " · " + userTurnCount + " lượt trả lời"
        );

        estimatedLevelText.setText("Chưa có đánh giá");

        if (userTurnCount == 0) {
            levelDetailText.setText(
                    "Bạn chưa gửi câu trả lời trong phiên này, "
                            + "nên chưa có dữ liệu để đánh giá."
            );

            strengthsText.setText(
                    "Hãy thử trả lời trong phiên tiếp theo "
                            + "để có nội dung nhận xét."
            );

            weaknessesText.setText(
                    "Chưa có câu trả lời để xác định "
                            + "điểm cần cải thiện."
            );

            grammarText.setText(
                    "Chưa có câu nào để kiểm tra ngữ pháp."
            );
        } else {
            levelDetailText.setText(
                    level + " là mức bài luyện bạn đã chọn. "
                            + "Đây chưa phải mức trình độ được đánh giá."
                            + "\n\nCần nội dung hội thoại và kết quả "
                            + "phân tích để ước tính trình độ thể hiện "
                            + "trong phiên."
            );

            strengthsText.setText(
                    "Bạn đã tham gia hội thoại với "
                            + userTurnCount + " lượt trả lời."
                            + "\n\nNhận xét về từ vựng, ngữ pháp "
                            + "và cách diễn đạt sẽ được bổ sung "
                            + "sau khi nối dữ liệu phân tích."
            );

            weaknessesText.setText(
                    "Chưa có kết quả phân tích để kết luận "
                            + "điểm cần cải thiện trong phiên này."
            );

            grammarText.setText(
                    "Phần này sẽ hiển thị:"
                            + "\n• Câu bạn đã nói."
                            + "\n• Câu được sửa."
                            + "\n• Giải thích bằng tiếng Việt."
                            + "\n\nChưa có kết quả phân tích AI "
                            + "để đưa ra sửa lỗi cụ thể."
            );
        }

        vocabularyNote.setText(
                "Từ tham khảo được chuẩn bị sẵn theo chủ đề, "
                        + "chưa được trích xuất từ cuộc trò chuyện. "
                        + "Bạn có thể chọn từ muốn lưu thành flashcard."
        );
    }

    private void prepareVocabulary() {
        vocabulary.clear();

        switch (topic) {
            case "Introducing Yourself":
                vocabulary.add(new Flashcard(
                        "Introduce",
                        "Giới thiệu",
                        "Let me introduce myself."
                ));

                vocabulary.add(new Flashcard(
                        "Hobby",
                        "Sở thích",
                        "My hobby is reading."
                ));

                vocabulary.add(new Flashcard(
                        "Hometown",
                        "Quê quán",
                        "My hometown is a small city."
                ));
                break;

            case "Travel":
                vocabulary.add(new Flashcard(
                        "Passport",
                        "Hộ chiếu",
                        "Please show your passport."
                ));

                vocabulary.add(new Flashcard(
                        "Luggage",
                        "Hành lý",
                        "My luggage is very heavy."
                ));

                vocabulary.add(new Flashcard(
                        "Destination",
                        "Điểm đến",
                        "What is your destination?"
                ));
                break;

            case "University Life":
                vocabulary.add(new Flashcard(
                        "Major",
                        "Chuyên ngành",
                        "My major is computer science."
                ));

                vocabulary.add(new Flashcard(
                        "Assignment",
                        "Bài tập",
                        "I need to finish my assignment."
                ));

                vocabulary.add(new Flashcard(
                        "Campus",
                        "Khuôn viên trường",
                        "Our campus has a large library."
                ));
                break;

            case "Ordering Food":
                vocabulary.add(new Flashcard(
                        "Menu",
                        "Thực đơn",
                        "Can I see the menu, please?"
                ));

                vocabulary.add(new Flashcard(
                        "Order",
                        "Gọi món",
                        "I would like to order a sandwich."
                ));

                vocabulary.add(new Flashcard(
                        "Reservation",
                        "Sự đặt chỗ",
                        "I have a reservation for two people."
                ));
                break;

            default:
                vocabulary.add(new Flashcard(
                        "Explain",
                        "Giải thích",
                        "Could you explain that, please?"
                ));

                vocabulary.add(new Flashcard(
                        "Opinion",
                        "Ý kiến",
                        "What is your opinion?"
                ));

                vocabulary.add(new Flashcard(
                        "Example",
                        "Ví dụ",
                        "Can you give me an example?"
                ));
                break;
        }
    }

    private void renderVocabulary(View view) {
        LinearLayout container =
                view.findViewById(R.id.resultVocabularyContainer);

        container.removeAllViews();
        checkBoxes.clear();

        for (int i = 0; i < vocabulary.size(); i++) {
            final int index = i;

            Flashcard card = vocabulary.get(i);

            String heading =
                    card.getWord() + " · " + card.getMeaning();

            String example = card.getExample();

            boolean hasExample =
                    example != null
                            && !example.trim().isEmpty();

            String content = hasExample
                    ? heading + "\n" + example.trim()
                    : heading;

            android.text.SpannableString styledText =
                    new android.text.SpannableString(content);

            // Nhấn mạnh từ tiếng Anh.
            styledText.setSpan(
                    new android.text.style.StyleSpan(
                            android.graphics.Typeface.BOLD
                    ),
                    0,
                    card.getWord().length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            // Câu ví dụ nhỏ hơn và dịu màu.
            if (hasExample) {
                int exampleStart = heading.length() + 1;

                styledText.setSpan(
                        new android.text.style.ForegroundColorSpan(
                                androidx.core.content.ContextCompat.getColor(
                                        requireContext(),
                                        R.color.sm_text_secondary
                                )
                        ),
                        exampleStart,
                        content.length(),
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );

                styledText.setSpan(
                        new android.text.style.RelativeSizeSpan(0.88f),
                        exampleStart,
                        content.length(),
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }

            AppCompatCheckBox checkBox =
                    new AppCompatCheckBox(requireContext());

            checkBox.setText(styledText);
            checkBox.setTextSize(15);

            checkBox.setTypeface(
                    android.graphics.Typeface.create(
                            "sans-serif",
                            android.graphics.Typeface.NORMAL
                    )
            );

            checkBox.setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                            requireContext(),
                            R.color.sm_text_primary
                    )
            );

            checkBox.setButtonTintList(
                    android.content.res.ColorStateList.valueOf(
                            androidx.core.content.ContextCompat.getColor(
                                    requireContext(),
                                    R.color.sm_cyan
                            )
                    )
            );

            checkBox.setBackgroundResource(
                    R.drawable.bg_sm_result_word
            );

            checkBox.setGravity(
                    android.view.Gravity.CENTER_VERTICAL
            );

            checkBox.setMinHeight(dp(76));

            checkBox.setLineSpacing(
                    dp(4),
                    1f
            );

            checkBox.setPadding(
                    dp(12),
                    dp(14),
                    dp(14),
                    dp(14)
            );

            checkBox.setChecked(selected[i]);
            checkBox.setSaveEnabled(false);

            checkBox.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        selected[index] = isChecked;
                        updateControls();
                    }
            );

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            params.topMargin = dp(10);

            container.addView(checkBox, params);
            checkBoxes.add(checkBox);
        }
    }

    private int selectedCount() {
        int count = 0;

        for (boolean value : selected) {
            if (value) {
                count++;
            }
        }

        return count;
    }

    private void updateControls() {
        if (getView() == null || createButton == null) {
            return;
        }
        if (conversationId > 0) {
            updateServerControls();
            return;
        }

        boolean created = createdDeckId != null;

        selectedCountText.setText(
                "Đã chọn " + selectedCount()
                        + "/" + vocabulary.size() + " từ"
        );

        createButton.setEnabled(
                !saving && !created && selectedCount() > 0
        );

        deckNameInput.setEnabled(!saving && !created);

        for (AppCompatCheckBox checkBox : checkBoxes) {
            checkBox.setEnabled(!saving && !created);
        }

        deckStatusText.setVisibility(
                created ? View.VISIBLE : View.GONE
        );

        openButton.setVisibility(
                created ? View.VISIBLE : View.GONE
        );

        openButton.setEnabled(!openingDeck);

        if (created) {
            deckStatusText.setText(
                    "Đã tạo bộ thẻ. Bạn có thể mở để bắt đầu học."
            );
        }
    }

    private void refreshCreatedDeckState() {
        if (createdDeckId != null) {
            try {
                FlashcardDeck deck =
                        new DemoFlashcardRepository(requireContext())
                                .getDeckById(createdDeckId);

                // Bộ vừa tạo có thể đã bị người dùng xóa.
                if (deck == null) {
                    createdDeckId = null;
                }
            } catch (IllegalStateException exception) {
                showMessage(exception.getMessage());
            }
        }

        updateControls();
    }

    private void createDeck() {
        if (!canAct() || saving || createdDeckId != null) {
            return;
        }

        String name =
                deckNameInput.getText().toString().trim();

        if (name.isEmpty()) {
            deckNameInput.setError("Vui lòng nhập tên bộ thẻ.");
            deckNameInput.requestFocus();
            return;
        }

        List<Flashcard> chosenCards = new ArrayList<>();

        for (int i = 0; i < vocabulary.size(); i++) {
            if (selected[i]) {
                chosenCards.add(vocabulary.get(i));
            }
        }

        if (chosenCards.isEmpty()) {
            showMessage("Hãy chọn ít nhất một từ.");
            return;
        }

        saving = true;
        updateControls();

        try {
            createdDeckId =
                    new LocalDeckStore(requireContext()).createDeck(
                            name,
                            level,
                            chosenCards
                    );

            draftDeckName = name;
            showMessage("Đã tạo bộ flashcard.");

        } catch (IllegalArgumentException | IllegalStateException exception) {
            showMessage(exception.getMessage());

        } finally {
            saving = false;
            updateControls();
        }
    }

    private void openCreatedDeck() {
        if (!canAct()
                || createdDeckId == null
                || openingDeck) {
            return;
        }

        openingDeck = true;
        updateControls();

        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.fragmentContainer,
                        DeckDetailFragment.newInstance(createdDeckId)
                )
                .addToBackStack("result_created_deck")
                .commit();
    }

    private boolean canAct() {
        return isAdded()
                && isResumed()
                && getView() != null
                && !getParentFragmentManager().isStateSaved();
    }

    private void navigateToTab(int itemId) {
        if (!canAct()) {
            return;
        }

        ((MainActivity) requireActivity())
                .navigateToTab(itemId);
    }

    private void showMessage(String message) {
        if (getContext() != null) {
            Toast.makeText(
                    requireContext(),
                    message == null ? "Có lỗi xảy ra." : message,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private int dp(int value) {
        return Math.round(
                value * getResources()
                        .getDisplayMetrics().density
        );
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        long[] ids = new long[selectedSuggestions.size()];
        int next = 0;
        for (Long id : selectedSuggestions) ids[next++] = id;
        outState.putLongArray("selectedSuggestionIds", ids);

        if (deckNameInput != null) {
            draftDeckName = deckNameInput.getText().toString();
        }

        outState.putBooleanArray(
                STATE_SELECTED,
                selected.clone()
        );

        outState.putString(STATE_DECK_NAME, draftDeckName);
        outState.putString(STATE_CREATED_ID, createdDeckId);
    }

    private void openConversationReview() {
        if (!isAdded()
                || !isResumed()
                || getView() == null
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        View reviewButton =
                requireView().findViewById(
                        R.id.btnViewResultConversation
                );

        // Ngăn mở nhiều màn hình khi bấm liên tục.
        if (!reviewButton.isEnabled()) {
            return;
        }

        Bundle arguments = getArguments();

        ArrayList<String> transcript = arguments == null
                ? null
                : arguments.getStringArrayList(
                "transcriptMessages"
        );

        boolean[] roles = arguments == null
                ? null
                : arguments.getBooleanArray(
                "transcriptUserRoles"
        );

        if (transcript == null
                || roles == null
                || transcript.size() != roles.length
                || transcript.isEmpty()) {
            Toast.makeText(
                    requireContext(),
                    "Không có hội thoại để xem lại.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        for (String message : transcript) {
            if (message == null) {
                Toast.makeText(
                        requireContext(),
                        "Dữ liệu hội thoại không hợp lệ.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
        }

        ChatFragment review = ChatFragment.newReviewInstance(
                arguments.getString("level", "A2"),
                arguments.getString("topic", "Chủ đề luyện nói"),
                transcript,
                roles
        );

        reviewButton.setEnabled(false);

        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragmentContainer, review)
                .addToBackStack("conversation_review")
                .commit();
    }

    @Override
    public void onDestroyView() {
        reviewPoll.removeCallbacksAndMessages(null);
        loadingReview = false;
        if (deckNameInput != null) {
            draftDeckName = deckNameInput.getText().toString();
        }

        checkBoxes.clear();

        deckNameInput = null;
        selectedCountText = null;
        deckStatusText = null;
        createButton = null;
        openButton = null;

        super.onDestroyView();
    }

    private void bindServerReview(View view) {
        loadingReview = false;
        deckNameInput.setVisibility(View.GONE);
        ((TextView) createButton).setText("Lưu từ đã chọn");
        ((TextView) openButton).setText("Mở Từ của tôi");
        // Danh sách từ trên tài khoản chưa nối vào màn bộ thẻ offline.
        openButton.setVisibility(View.GONE);
        text(R.id.tvResultSaveTitle, "Ôn từ mới sau buổi luyện");
        text(R.id.tvResultSaveDescription, "Chọn từ muốn lưu vào Từ của tôi. Bạn có thể ôn lại bất cứ lúc nào.");
        text(R.id.tvResultNotice, "Nhận xét do AI tạo dựa trên hội thoại, không thay thế bài kiểm tra trình độ.");
        text(R.id.tvResultSummary, "Đang tải kết quả…");
        createButton.setEnabled(false);
        createButton.setOnClickListener(v -> {
            if (serverReview != null && serverReview.optBoolean("canRetry")) retryServerReview();
            else saveServerWords();
        });
        openButton.setOnClickListener(v -> navigateToTab(R.id.nav_flashcards));
        deckStatusText.setOnClickListener(v -> loadServerReview());
        view.findViewById(R.id.btnViewResultConversation).setVisibility(View.VISIBLE);
        view.findViewById(R.id.btnViewResultConversation).setOnClickListener(v -> {
            if (canAct()) getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, ChatFragment.newReviewInstance(conversationId))
                    .addToBackStack("conversation_review").commit();
        });
        TextView back = view.findViewById(R.id.btnBackToPractice);
        boolean history = requireArguments().getBoolean("fromHistory");
        back.setText(history ? "Quay lại lịch sử" : "Chọn bài luyện tiếp");
        back.setOnClickListener(v -> {
            if (!canAct()) return;
            if (history) getParentFragmentManager().popBackStack();
            else navigateToTab(R.id.nav_speaking);
        });
        view.findViewById(R.id.btnResultHome).setOnClickListener(v -> navigateToTab(R.id.nav_home));
    }

    private void loadServerReview() {
        if (loadingReview || saving || getView() == null) return;
        loadingReview = true;
        reviewPoll.removeCallbacksAndMessages(null);
        updateServerControls();
        call(() -> new JSONObject().put("detail", api.get("conversations/" + conversationId))
                .put("review", api.get("conversations/" + conversationId + "/review")), response -> {
            loadingReview = false;
            JSONObject detail = response.optJSONObject("detail");
            serverReview = response.optJSONObject("review");
            text(R.id.tvResultTopic, detail.optString("topicName"));
            text(R.id.tvResultStats, "Mức luyện: " + detail.optString("level") + " · " + detail.optInt("userTurnCount") + " lượt");
            renderServerReview();
        }, error -> {
            loadingReview = false;
            deckStatusText.setVisibility(View.VISIBLE);
            deckStatusText.setText(error.getMessage() + " Chạm để tải lại.");
            updateServerControls();
        });
    }

    private void renderServerReview() {
        String status = serverReview.optString("reviewStatus");
        boolean complete = "COMPLETED".equals(status);
        String description = "PROCESSING".equals(status) ? "Đang tạo nhận xét…"
                : "INSUFFICIENT_DATA".equals(status) ? "Bài còn ngắn, chưa đủ dữ liệu để đánh giá. Hãy luyện ít nhất 3 lượt trả lời có nội dung."
                : "FAILED".equals(status) ? "Chưa tạo được nhận xét. Hội thoại vẫn được giữ; bạn có thể thử lại."
                : "Chưa có nhận xét cho bài này.";
        text(R.id.tvResultSummary, complete ? value(serverReview, "summary") : description);
        text(R.id.tvResultEstimatedLevel, complete ? value(serverReview, "recommendedLevel") : "Chưa có đánh giá");
        text(R.id.tvResultLevelDetail, complete ? value(serverReview, "levelFeedback") : "");
        text(R.id.tvResultStrengths, complete ? value(serverReview, "strengths") : "Chưa có kết quả phân tích.");
        text(R.id.tvResultWeaknesses, complete ? value(serverReview, "weaknesses") : "Chưa có kết quả phân tích.");
        StringBuilder grammar = new StringBuilder();
        JSONArray corrections = serverReview.optJSONArray("grammarCorrections");
        if (complete && corrections != null) for (int i = 0; i < corrections.length(); i++) {
            JSONObject item = corrections.optJSONObject(i);
            if (item == null) continue;
            if (grammar.length() > 0) grammar.append("\n\n");
            grammar.append("Bạn nói: ").append(value(item, "originalContent"))
                    .append("\nGợi ý sửa: ").append(value(item, "correctedContent"))
                    .append("\n").append(value(item, "feedback"));
        }
        text(R.id.tvResultGrammar, grammar.length() > 0 ? grammar.toString()
                : complete ? "AI không đề xuất sửa câu nào trong bài này." : "Chưa có kết quả ngữ pháp.");
        vocabulary.clear();
        JSONArray words = serverReview.optJSONArray("suggestedWords");
        int count = complete && words != null ? words.length() : 0;
        selected = new boolean[count];
        for (int i = 0; i < count; i++) {
            JSONObject word = words.optJSONObject(i);
            vocabulary.add(new Flashcard(value(word, "word"), value(word, "meaning"), value(word, "example")));
            selected[i] = word.optLong("savedWordId") <= 0 && selectedSuggestions.contains(word.optLong("id"));
        }
        text(R.id.tvResultVocabularyNote, count == 0 ? "Chưa có từ được đề xuất." : "Từ và ví dụ được đề xuất từ bài vừa luyện.");
        renderVocabulary(requireView());
        deckStatusText.setVisibility(View.VISIBLE);
        deckStatusText.setText(complete ? "Đã tải kết quả. Chạm để cập nhật trạng thái từ đã lưu." : description + " Chạm để tải lại.");
        updateServerControls();
        if ("PROCESSING".equals(status) && isResumed()) reviewPoll.postDelayed(this::loadServerReview,
                Math.max(2, Math.min(10, serverReview.optInt("pollAfterSeconds", 3))) * 1000L);
    }

    private void updateServerControls() {
        if (createButton == null) return;
        JSONArray words = serverReview == null ? null : serverReview.optJSONArray("suggestedWords");
        if (words != null && checkBoxes.size() == words.length()) selectedSuggestions.clear();
        for (int i = 0; i < checkBoxes.size() && words != null && i < words.length(); i++) {
            JSONObject word = words.optJSONObject(i);
            boolean saved = word.optLong("savedWordId") > 0;
            AppCompatCheckBox box = checkBoxes.get(i);
            box.setEnabled(!saving && !loadingReview && !saved);
            box.setTag(word.optLong("id"));
            if (saved) box.setText(value(word, "word") + " · " + value(word, "meaning") + " · Đã lưu");
            if (selected[i] && !saved) selectedSuggestions.add(word.optLong("id"));
        }
        boolean retry = serverReview != null && serverReview.optBoolean("canRetry");
        ((TextView) createButton).setText(retry ? "Thử lại nhận xét" : "Lưu từ đã chọn");
        createButton.setEnabled(!saving && !loadingReview && (retry || !selectedSuggestions.isEmpty()));
        selectedCountText.setText("Đã chọn " + selectedSuggestions.size() + " từ");
    }

    private void saveServerWords() {
        if (!canAct() || saving || loadingReview || selectedSuggestions.isEmpty()) return;
        JSONArray ids = new JSONArray();
        for (Long id : selectedSuggestions) ids.put(id);
        saving = true;
        updateServerControls();
        call(() -> api.saveWords(ids), result -> {
            saving = false;
            showMessage("Đã lưu từ vào tài khoản.");
            loadServerReview();
        }, error -> {
            saving = false;
            deckStatusText.setText(error.getMessage() + " Chạm để kiểm tra trạng thái đã lưu.");
            updateServerControls();
        });
    }

    private void retryServerReview() {
        if (!canAct() || loadingReview || saving || serverReview == null || !serverReview.optBoolean("canRetry")) return;
        saving = true;
        updateServerControls();
        call(() -> api.finish(conversationId, true), result -> {
            saving = false;
            loadServerReview();
        }, error -> {
            saving = false;
            showMessage(error.getMessage());
            loadServerReview();
        });
    }

    private String value(JSONObject object, String key) {
        return object == null || object.isNull(key) ? "" : object.optString(key, "");
    }

    private void text(int id, String value) {
        ((TextView) requireView().findViewById(id)).setText(value);
    }

    @Override
    public void onPause() {
        reviewPoll.removeCallbacksAndMessages(null);
        super.onPause();
    }
}
