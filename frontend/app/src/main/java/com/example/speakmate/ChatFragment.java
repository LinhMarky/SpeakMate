package com.example.speakmate;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

public class ChatFragment extends Fragment {

    private static final String ARG_LEVEL = "level";
    private static final String ARG_TOPIC = "topic";

    private static final String ARG_REVIEW_ONLY = "reviewOnly";
    private static final String ARG_REVIEW_MESSAGES = "reviewMessages";
    private static final String ARG_REVIEW_ROLES = "reviewRoles";

    private static final String STATE_MESSAGES = "messages";
    private static final String STATE_ROLES = "roles";
    private static final String STATE_DRAFT = "chat_draft";

    private String level = "A2";
    private String topic = "Ordering Food";

    private String draftMessage = "";
    private String voiceBaseDraft = "";

    private boolean reviewOnly;
    private boolean leavingSession;
    private boolean voicePending;
    private boolean fingerHolding;
    private boolean permissionRequestPending;
    private boolean ignoreVoiceClick;

    private final ArrayList<String> messages = new ArrayList<>();
    private final ArrayList<Boolean> userMessages = new ArrayList<>();

    private LinearLayout messageContainer;
    private ScrollView chatScroll;
    private EditText messageInput;

    private View microphoneButton;
    private TextView voiceHint;

    private AlertDialog endDialog;
    private HoldToSpeakController holdToSpeak;

    private static final String STATE_LAST_SPOKEN =
            "chat_last_spoken_index";

    private DeckSpeech chatSpeech;

    private boolean autoSpeakEnabled = true;
    private int lastAutoSpokenIndex = -1;

    private final ActivityResultLauncher<String> microphonePermission =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        permissionRequestPending = false;
                        updateInputControls();

                        if (reviewOnly) {
                            return;
                        }

                        showMessage(
                                granted
                                        ? "Đã cấp quyền. Bạn hãy giữ micro để nói."
                                        : "Chưa được cấp quyền micro. "
                                          + "Bạn vẫn có thể nhập bằng bàn phím. "
                                          + "Có thể cấp quyền trong Cài đặt ứng dụng."
                        );
                    }
            );

    public ChatFragment() {
        super(R.layout.fragment_chat);
    }

    public static ChatFragment newInstance(
            String level,
            String topic
    ) {
        ChatFragment fragment = new ChatFragment();

        Bundle arguments = new Bundle();
        arguments.putString(ARG_LEVEL, level);
        arguments.putString(ARG_TOPIC, topic);

        fragment.setArguments(arguments);

        return fragment;
    }

    public static ChatFragment newReviewInstance(
            String level,
            String topic,
            ArrayList<String> messages,
            boolean[] userRoles
    ) {
        if (messages == null
                || userRoles == null
                || messages.size() != userRoles.length) {
            throw new IllegalArgumentException(
                    "Dữ liệu hội thoại không hợp lệ."
            );
        }

        for (String message : messages) {
            if (message == null) {
                throw new IllegalArgumentException(
                        "Hội thoại có tin nhắn không hợp lệ."
                );
            }
        }

        ChatFragment fragment = new ChatFragment();

        Bundle arguments = new Bundle();

        arguments.putString(ARG_LEVEL, level);
        arguments.putString(ARG_TOPIC, topic);
        arguments.putBoolean(ARG_REVIEW_ONLY, true);

        arguments.putStringArrayList(
                ARG_REVIEW_MESSAGES,
                new ArrayList<>(messages)
        );

        arguments.putBooleanArray(
                ARG_REVIEW_ROLES,
                userRoles.clone()
        );

        fragment.setArguments(arguments);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            lastAutoSpokenIndex = savedInstanceState.getInt(
                    STATE_LAST_SPOKEN,
                    -1
            );
        }

        Bundle arguments = getArguments();

        if (arguments != null) {
            level = valueOrDefault(
                    arguments.getString(ARG_LEVEL),
                    "A2"
            );

            topic = valueOrDefault(
                    arguments.getString(ARG_TOPIC),
                    "Ordering Food"
            );

            reviewOnly = arguments.getBoolean(
                    ARG_REVIEW_ONLY,
                    false
            );
        }

        if (savedInstanceState != null) {
            restoreConversation(
                    savedInstanceState.getStringArrayList(
                            STATE_MESSAGES
                    ),
                    savedInstanceState.getBooleanArray(
                            STATE_ROLES
                    )
            );

            draftMessage = valueOrDefault(
                    savedInstanceState.getString(STATE_DRAFT),
                    ""
            );
        }

        if (reviewOnly && messages.isEmpty() && arguments != null) {
            restoreConversation(
                    arguments.getStringArrayList(
                            ARG_REVIEW_MESSAGES
                    ),
                    arguments.getBooleanArray(
                            ARG_REVIEW_ROLES
                    )
            );
        }

        // Chỉ tạo lời chào cho phiên luyện mới.
        if (!reviewOnly && messages.isEmpty()) {
            messages.add(getOpeningMessage());
            userMessages.add(false);
        }
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        leavingSession = false;
        voicePending = false;
        fingerHolding = false;

        TextView topicText =
                view.findViewById(R.id.tvChatTopic);

        TextView levelText =
                view.findViewById(R.id.tvChatLevel);

        messageInput =
                view.findViewById(R.id.etMessage);

        messageContainer =
                view.findViewById(R.id.messageContainer);

        chatScroll =
                view.findViewById(R.id.chatScroll);

        microphoneButton =
                view.findViewById(R.id.btnVoiceInput);

        voiceHint =
                view.findViewById(R.id.tvVoiceInputHint);

        topicText.setText(topic);

        levelText.setText(
                reviewOnly
                        ? "Xem lại hội thoại · " + level
                        : "Mức bài luyện: " + level
        );

        messageInput.setSaveEnabled(false);
        messageInput.setText(draftMessage);
        messageInput.setSelection(messageInput.length());

        messageInput.setOnEditorActionListener(
                (input, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        hideKeyboard();
                        input.clearFocus();
                        return true;
                    }

                    return false;
                }
        );

        messageContainer.removeAllViews();

        for (int i = 0; i < messages.size(); i++) {
            displayMessage(
                    messages.get(i),
                    userMessages.get(i)
            );
        }

        view.findViewById(R.id.btnBackChat)
                .setOnClickListener(
                        button -> confirmEndSession()
                );

        view.findViewById(R.id.btnEndChat)
                .setOnClickListener(
                        button -> confirmEndSession()
                );

        view.findViewById(R.id.btnSendMessage)
                .setOnClickListener(
                        button -> sendMessage()
                );

        view.findViewById(R.id.chatComposer)
                .setVisibility(
                        reviewOnly ? View.GONE : View.VISIBLE
                );

        view.findViewById(R.id.btnEndChat)
                .setVisibility(
                        reviewOnly ? View.GONE : View.VISIBLE
                );

        android.widget.CheckBox autoSpeakCheckBox =
                view.findViewById(R.id.cbChatAutoSpeak);

        android.content.SharedPreferences preferences =
                requireContext().getSharedPreferences(
                        "speakmate_profile",
                        android.content.Context.MODE_PRIVATE
                );

        autoSpeakEnabled = preferences.getBoolean(
                "chat_auto_speak",
                true
        );

        autoSpeakCheckBox.setChecked(autoSpeakEnabled);

        autoSpeakCheckBox.setVisibility(
                reviewOnly ? View.GONE : View.VISIBLE
        );

        if (reviewOnly) {
            view.findViewById(R.id.btnBackChat)
                    .setContentDescription(
                            "Quay lại tổng kết"
                    );
        } else {
            chatSpeech = new DeckSpeech(requireContext());
            setupHoldToSpeak(view);
        }

        autoSpeakCheckBox.setOnCheckedChangeListener(
                (button, checked) -> {
                    autoSpeakEnabled = checked;

                    preferences.edit()
                            .putBoolean("chat_auto_speak", checked)
                            .apply();

                    if (checked) {
                        speakLatestPartnerMessage(true);
                    } else {
                        stopChatSpeech();
                    }
                }
        );

        updateInputControls();
        scrollConversation(reviewOnly);
    }

    @Override
    public void onResume() {
        super.onResume();

        leavingSession = false;
        updateInputControls();

        speakLatestPartnerMessage(false);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupHoldToSpeak(@NonNull View root) {
        holdToSpeak = new HoldToSpeakController(
                requireContext(),
                new HoldToSpeakController.Listener() {

                    @Override
                    public void onStateChanged(
                            boolean busy,
                            String message
                    ) {
                        voicePending = busy;

                        if (busy) {
                            stopChatSpeech();
                        }

                        if (getView() != root) {
                            return;
                        }

                        if (voiceHint != null) {
                            voiceHint.setText(message);
                        }

                        if (microphoneButton != null) {
                            microphoneButton.setAlpha(
                                    busy ? 0.65f : 1f
                            );

                            if (!busy) {
                                microphoneButton.setPressed(false);
                            }
                        }

                        updateInputControls();
                    }

                    @Override
                    public void onResult(String text) {
                        if (getView() == root) {
                            appendRecognizedText(text);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (getView() == root) {
                            showMessage(message);
                        }
                    }
                }
        );

        microphoneButton.setContentDescription(
                "Nhấn giữ để nói tiếng Anh, thả tay để kết thúc"
        );

        microphoneButton.setOnClickListener(button -> {
            if (!ignoreVoiceClick) {
                showMessage(
                        "Bạn hãy nhấn giữ micro để nói, "
                                + "thả tay khi nói xong."
                );
            }
        });

        microphoneButton.setOnTouchListener((button, event) -> {
            switch (event.getActionMasked()) {

                case MotionEvent.ACTION_DOWN:
                    if (reviewOnly
                            || !canAct()
                            || permissionRequestPending
                            || endDialog != null
                            || messageInput == null
                            || holdToSpeak == null) {
                        return true;
                    }

                    boolean granted =
                            ContextCompat.checkSelfPermission(
                                    requireContext(),
                                    Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED;

                    if (!granted) {
                        permissionRequestPending = true;
                        updateInputControls();

                        microphonePermission.launch(
                                Manifest.permission.RECORD_AUDIO
                        );

                        return true;
                    }

                    draftMessage =
                            messageInput.getText().toString();

                    voiceBaseDraft = draftMessage;

                    hideKeyboard();
                    messageInput.clearFocus();

                    fingerHolding = true;
                    button.setPressed(true);

                    holdToSpeak.start();
                    return true;

                case MotionEvent.ACTION_UP:
                    button.setPressed(false);

                    if (fingerHolding) {
                        fingerHolding = false;

                        if (holdToSpeak != null) {
                            holdToSpeak.stop();
                        }

                        ignoreVoiceClick = true;

                        try {
                            button.performClick();
                        } finally {
                            ignoreVoiceClick = false;
                        }
                    }

                    return true;

                case MotionEvent.ACTION_CANCEL:
                    cancelVoiceInput();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    boolean outside =
                            event.getX() < 0
                                    || event.getY() < 0
                                    || event.getX() > button.getWidth()
                                    || event.getY() > button.getHeight();

                    if (outside && fingerHolding) {
                        cancelVoiceInput();
                    }

                    return true;

                default:
                    return true;
            }
        });
    }

    private void appendRecognizedText(String text) {
        if (reviewOnly
                || leavingSession
                || text == null
                || text.trim().isEmpty()) {
            return;
        }

        String separator = "";

        if (!voiceBaseDraft.isEmpty()) {
            char lastCharacter = voiceBaseDraft.charAt(
                    voiceBaseDraft.length() - 1
            );

            if (!Character.isWhitespace(lastCharacter)) {
                separator = " ";
            }
        }

        draftMessage =
                voiceBaseDraft + separator + text.trim();

        voiceBaseDraft = "";

        if (messageInput != null) {
            messageInput.setText(draftMessage);
            messageInput.setSelection(messageInput.length());
            messageInput.setError(null);
        }

        if (voiceHint != null) {
            voiceHint.setText(
                    "Đã nhận câu nói. Bạn kiểm tra nội dung "
                            + "rồi bấm Gửi."
            );
        }
    }

    private void cancelVoiceInput() {
        fingerHolding = false;

        if (microphoneButton != null) {
            microphoneButton.setPressed(false);
        }

        if (holdToSpeak != null) {
            holdToSpeak.cancel();
        }

        voicePending = false;
        updateInputControls();
    }

    private void updateInputControls() {
        View view = getView();

        if (view == null) {
            return;
        }

        boolean editable =
                !reviewOnly
                        && !voicePending
                        && !permissionRequestPending
                        && !leavingSession;

        // Micro vẫn phải nhận được ACTION_UP khi người dùng thả tay.
        view.findViewById(R.id.btnVoiceInput)
                .setEnabled(
                        !reviewOnly
                                && !permissionRequestPending
                                && !leavingSession
                );

        view.findViewById(R.id.btnSendMessage)
                .setEnabled(editable);

        view.findViewById(R.id.etMessage)
                .setEnabled(editable);

        view.findViewById(R.id.btnEndChat)
                .setEnabled(editable);

        view.findViewById(R.id.btnBackChat)
                .setEnabled(!leavingSession);
    }

    private void sendMessage() {
        if (reviewOnly
                || !canAct()
                || messageInput == null
                || endDialog != null) {
            return;
        }

        String content =
                messageInput.getText().toString().trim();

        if (content.isEmpty()) {
            messageInput.setError(
                    "Bạn hãy nhập hoặc nói câu trả lời."
            );

            messageInput.requestFocus();
            return;
        }

        messageInput.setError(null);

        addMessage(content, true);

        messageInput.setText("");
        draftMessage = "";
        voiceBaseDraft = "";

        // Phản hồi mẫu, chưa gọi backend AI.
        addMessage(getDemoReply(), false);

        if (voiceHint != null) {
            voiceHint.setText(
                    "Giữ micro để nói tiếng Anh, "
                            + "thả tay để kết thúc."
            );
        }

        hideKeyboard();
        messageInput.clearFocus();

        scrollConversation(false);
    }

    private void restoreConversation(
            @Nullable ArrayList<String> savedMessages,
            @Nullable boolean[] savedRoles
    ) {
        if (savedMessages == null
                || savedRoles == null
                || savedMessages.size() != savedRoles.length) {
            return;
        }

        for (String message : savedMessages) {
            if (message == null) {
                return;
            }
        }

        messages.clear();
        userMessages.clear();

        messages.addAll(savedMessages);

        for (boolean role : savedRoles) {
            userMessages.add(role);
        }
    }

    public void confirmEndSession() {
        // Nếu bấm Back lúc đang nhận giọng nói,
        // hủy nhận dạng trước khi xử lý điều hướng.
        stopChatSpeech();
        if (voicePending || fingerHolding) {
            cancelVoiceInput();
        }

        if (!canAct()) {
            return;
        }

        hideKeyboard();

        if (messageInput != null) {
            messageInput.clearFocus();
        }

        if (reviewOnly) {
            leavingSession = true;

            boolean returned =
                    getParentFragmentManager()
                            .popBackStackImmediate();

            if (!returned) {
                leavingSession = false;
                updateInputControls();
            }

            return;
        }

        if (endDialog != null) {
            return;
        }

        AlertDialog dialog =
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Kết thúc phiên luyện?")
                        .setMessage(
                                "Xem tổng kết để nhìn lại phiên luyện, "
                                        + "hoặc thoát về trang chủ."
                                        + "\n\nCâu đang nhập chưa gửi "
                                        + "sẽ không được đưa vào tổng kết."
                        )
                        .setNegativeButton(
                                "Tiếp tục luyện",
                                null
                        )
                        .setNeutralButton(
                                "Thoát phiên",
                                (ignored, which) -> exitSession()
                        )
                        .setPositiveButton(
                                "Xem tổng kết",
                                (ignored, which) -> finishSession()
                        )
                        .create();

        endDialog = dialog;

        dialog.setOnDismissListener(ignored -> {
            if (endDialog == dialog) {
                endDialog = null;
            }
        });

        dialog.show();
    }

    private void exitSession() {
        if (!canAct()) {
            return;
        }

        leavingSession = true;
        updateInputControls();

        ((MainActivity) requireActivity())
                .navigateToTab(R.id.nav_home);
    }

    private void finishSession() {
        if (!canAct()) {
            return;
        }

        ArrayList<String> transcript =
                new ArrayList<>(messages);

        boolean[] roles = createRolesArray();

        leavingSession = true;
        updateInputControls();

        ((MainActivity) requireActivity())
                .showSpeakingResult(
                        level,
                        topic,
                        transcript,
                        roles
                );
    }

    private boolean[] createRolesArray() {
        boolean[] roles = new boolean[userMessages.size()];

        for (int i = 0; i < userMessages.size(); i++) {
            roles[i] = userMessages.get(i);
        }

        return roles;
    }

    private boolean canAct() {
        return isAdded()
                && isResumed()
                && getView() != null
                && !leavingSession
                && !voicePending
                && !permissionRequestPending
                && !getParentFragmentManager().isStateSaved();
    }

    private String getOpeningMessage() {
        switch (topic) {
            case "Introducing Yourself":
                return "Hi! I'm your practice partner. What's your name?";

            case "Travel":
                return "Hello! Where would you like to travel?";

            case "University Life":
                return "Hi! What do you study at university?";

            case "Ordering Food":
                return "Welcome! What would you like to order?";

            default:
                return "Hi! Let's practise English. "
                        + "What would you like to share about this topic?";
        }
    }

    private String getDemoReply() {
        switch (topic) {
            case "Introducing Yourself":
                return "Nice to meet you! What do you enjoy doing?";

            case "Travel":
                return "What would you like to do there?";

            case "University Life":
                return "What do you enjoy most about university?";

            case "Ordering Food":
                return "Would you like something to drink?";

            default:
                return "Could you tell me more about that?";
        }
    }

    private void addMessage(String content, boolean fromUser) {
        messages.add(content);
        userMessages.add(fromUser);

        displayMessage(content, fromUser);

        if (!fromUser) {
            speakLatestPartnerMessage(false);
        }
    }

    private void displayMessage(
            String content,
            boolean fromUser
    ) {
        if (messageContainer == null) {
            return;
        }

        LinearLayout row =
                new LinearLayout(requireContext());

        row.setOrientation(LinearLayout.HORIZONTAL);

        row.setGravity(
                fromUser ? Gravity.END : Gravity.START
        );

        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        rowParams.bottomMargin = dp(14);

        TextView bubble =
                new TextView(requireContext());

        bubble.setText(content);
        bubble.setTextSize(16);
        bubble.setTextIsSelectable(true);

        bubble.setTextColor(
                ContextCompat.getColor(
                        requireContext(),
                        fromUser
                                ? R.color.sm_background
                                : R.color.sm_text_primary
                )
        );

        bubble.setBackgroundResource(
                fromUser
                        ? R.drawable.bg_button_primary
                        : R.drawable.bg_card
        );

        bubble.setPadding(
                dp(16),
                dp(14),
                dp(16),
                dp(14)
        );

        int screenWidth =
                getResources().getDisplayMetrics().widthPixels;

        bubble.setMaxWidth(
                (int) (screenWidth * 0.76f)
        );

        row.addView(
                bubble,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        messageContainer.addView(row, rowParams);
    }

    private void scrollConversation(boolean toTop) {
        ScrollView scroll = chatScroll;

        if (scroll == null) {
            return;
        }

        scroll.post(() -> {
            if (chatScroll == scroll && isAdded()) {
                scroll.fullScroll(
                        toTop
                                ? View.FOCUS_UP
                                : View.FOCUS_DOWN
                );
            }
        });
    }

    private void speakLatestPartnerMessage(boolean force) {
        if (reviewOnly
                || !autoSpeakEnabled
                || chatSpeech == null
                || !isAdded()
                || !isResumed()
                || leavingSession
                || voicePending
                || permissionRequestPending
                || messages.isEmpty()) {
            return;
        }

        int index = messages.size() - 1;

        // Chỉ đọc câu của bạn luyện.
        if (userMessages.get(index)) {
            return;
        }

        if (!force && lastAutoSpokenIndex == index) {
            return;
        }

        String content = messages.get(index);

        if (content == null || content.trim().isEmpty()) {
            return;
        }

        lastAutoSpokenIndex = index;
        chatSpeech.speak(content);
    }

    private void stopChatSpeech() {
        if (chatSpeech != null) {
            chatSpeech.stop();
        }
    }

    private void hideKeyboard() {
        View view = getView();

        if (view == null || !isAdded()) {
            return;
        }

        WindowCompat.getInsetsController(
                requireActivity().getWindow(),
                view
        ).hide(WindowInsetsCompat.Type.ime());
    }

    private void showMessage(String message) {
        if (getContext() != null) {
            Toast.makeText(
                    requireContext(),
                    message,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private String valueOrDefault(
            @Nullable String value,
            String fallback
    ) {
        return value == null || value.trim().isEmpty()
                ? fallback
                : value;
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }

    @Override
    public void onPause() {
        stopChatSpeech();
        cancelVoiceInput();

        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (messageInput != null) {
            draftMessage =
                    messageInput.getText().toString();
        }

        outState.putStringArrayList(
                STATE_MESSAGES,
                new ArrayList<>(messages)
        );

        outState.putBooleanArray(
                STATE_ROLES,
                createRolesArray()
        );

        outState.putString(
                STATE_DRAFT,
                draftMessage
        );

        outState.putInt(
                STATE_LAST_SPOKEN,
                lastAutoSpokenIndex
        );

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        if (chatSpeech != null) {
            chatSpeech.close();
            chatSpeech = null;
        }
        if (messageInput != null) {
            draftMessage =
                    messageInput.getText().toString();

            messageInput.setOnEditorActionListener(null);
        }

        if (holdToSpeak != null) {
            holdToSpeak.close();
            holdToSpeak = null;
        }

        if (microphoneButton != null) {
            microphoneButton.setOnTouchListener(null);
            microphoneButton.setOnClickListener(null);
        }

        if (endDialog != null) {
            endDialog.setOnDismissListener(null);
            endDialog.dismiss();
            endDialog = null;
        }

        voicePending = false;
        fingerHolding = false;

        messageInput = null;
        messageContainer = null;
        chatScroll = null;
        microphoneButton = null;
        voiceHint = null;

        super.onDestroyView();
    }
}