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
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

public class SpeakingResultFragment extends Fragment {

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

        prepareVocabulary();

        selected = new boolean[vocabulary.size()];

        for (int i = 0; i < selected.length; i++) {
            selected[i] = true;
        }

        draftDeckName = "Từ vựng - " + topic;

        if (savedInstanceState != null) {
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

        view.findViewById(R.id.btnBackToPractice)
                .setOnClickListener(v ->
                        navigateToTab(R.id.nav_speaking)
                );

        view.findViewById(R.id.btnResultHome)
                .setOnClickListener(v ->
                        navigateToTab(R.id.nav_home)
                );
    }

    @Override
    public void onResume() {
        super.onResume();

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
                            + "\n\nHiện chưa nhận nội dung hội thoại "
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

            AppCompatCheckBox checkBox =
                    new AppCompatCheckBox(requireContext());

            checkBox.setText(
                    card.getWord()
                            + " · " + card.getMeaning()
                            + "\n" + card.getExample()
            );

            checkBox.setTextSize(15);

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

            checkBox.setBackgroundResource(R.drawable.bg_card);

            checkBox.setPadding(
                    dp(12),
                    dp(14),
                    dp(12),
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
}