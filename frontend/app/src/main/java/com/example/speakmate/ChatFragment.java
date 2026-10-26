package com.example.speakmate;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

public class ChatFragment extends Fragment {

    private static final String ARG_LEVEL = "level";
    private static final String ARG_TOPIC = "topic";

    private static final String ARG_REVIEW_ONLY = "reviewOnly";
    private static final String ARG_REVIEW_MESSAGES = "reviewMessages";
    private static final String ARG_REVIEW_ROLES = "reviewRoles";

    private boolean reviewOnly;

    private static final String STATE_MESSAGES = "messages";
    private static final String STATE_ROLES = "roles";
    private static final String STATE_DRAFT = "chat_draft";

    private String level = "A2";
    private String topic = "Ordering Food";
    private String draftMessage = "";

    private final ArrayList<String> messages = new ArrayList<>();
    private final ArrayList<Boolean> userMessages = new ArrayList<>();

    private LinearLayout messageContainer;
    private ScrollView chatScroll;
    private EditText messageInput;
    private AlertDialog endDialog;

    private boolean leavingSession;

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

        restoreMessages(savedInstanceState);

        if (reviewOnly && messages.isEmpty() && arguments != null) {
            ArrayList<String> savedMessages =
                    arguments.getStringArrayList(
                            ARG_REVIEW_MESSAGES
                    );

            boolean[] savedRoles =
                    arguments.getBooleanArray(
                            ARG_REVIEW_ROLES
                    );

            if (savedMessages != null
                    && savedRoles != null
                    && savedMessages.size() == savedRoles.length) {

                boolean valid = true;

                for (String message : savedMessages) {
                    if (message == null) {
                        valid = false;
                        break;
                    }
                }

                if (valid) {
                    messages.addAll(savedMessages);

                    for (boolean role : savedRoles) {
                        userMessages.add(role);
                    }
                }
            }
        }

        if (savedInstanceState != null) {
            draftMessage = valueOrDefault(
                    savedInstanceState.getString(STATE_DRAFT),
                    ""
            );
        }

        // Chỉ phiên luyện mới mới có lời chào tự động.
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

        TextView topicText = view.findViewById(R.id.tvChatTopic);
        TextView levelText = view.findViewById(R.id.tvChatLevel);

        messageInput = view.findViewById(R.id.etMessage);
        messageContainer = view.findViewById(R.id.messageContainer);
        chatScroll = view.findViewById(R.id.chatScroll);

        topicText.setText(topic);
        levelText.setText("Mức bài luyện: " + level);

        // Tự lưu bản nháp để không xung đột với việc khôi phục của View.
        messageInput.setSaveEnabled(false);
        messageInput.setText(draftMessage);
        messageInput.setSelection(messageInput.length());

        messageContainer.removeAllViews();

        for (int i = 0; i < messages.size(); i++) {
            displayMessage(messages.get(i), userMessages.get(i));
        }

        view.findViewById(R.id.btnBackChat)
                .setOnClickListener(v -> confirmEndSession());

        view.findViewById(R.id.btnEndChat)
                .setOnClickListener(v -> confirmEndSession());

        view.findViewById(R.id.btnSendMessage)
                .setOnClickListener(v -> sendMessage());

        if (reviewOnly) {
            levelText.setText("Xem lại hội thoại · " + level);

            messageInput.setVisibility(View.GONE);

            view.findViewById(R.id.btnSendMessage)
                    .setVisibility(View.GONE);

            view.findViewById(R.id.btnEndChat)
                    .setVisibility(View.GONE);

            view.findViewById(R.id.btnBackChat)
                    .setContentDescription("Quay lại tổng kết");

            // Mở từ đầu để đọc lại hội thoại.
            ScrollView scroll = chatScroll;

            if (scroll != null) {
                scroll.post(() -> {
                    if (chatScroll == scroll && isAdded()) {
                        scroll.fullScroll(View.FOCUS_UP);
                    }
                });
            }
        } else {
            scrollToBottom();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        leavingSession = false;
    }

    private void sendMessage() {
        if (reviewOnly
                || !canAct()
                || messageInput == null
                || endDialog != null) {
            return;
        }

        String content = messageInput.getText()
                .toString()
                .trim();

        if (content.isEmpty()) {
            messageInput.setError("Bạn hãy nhập câu trả lời.");
            return;
        }

        messageInput.setError(null);

        addMessage(content, true);

        messageInput.setText("");
        draftMessage = "";

        // Phản hồi minh họa, chưa gọi backend hoặc AI.
        addMessage(getDemoReply(), false);

        scrollToBottom();
    }

    private void restoreMessages(@Nullable Bundle state) {
        if (state == null) {
            return;
        }

        ArrayList<String> savedMessages =
                state.getStringArrayList(STATE_MESSAGES);

        boolean[] savedRoles =
                state.getBooleanArray(STATE_ROLES);

        if (savedMessages == null
                || savedRoles == null
                || savedMessages.size() != savedRoles.length) {
            return;
        }

        // Kiểm tra trước khi khôi phục để hai danh sách luôn khớp nhau.
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
        if (!canAct()) {
            return;
        }

        // MainActivity cũng gọi hàm này khi bấm Back hệ thống.
        if (reviewOnly) {
            leavingSession = true;

            boolean popped = getParentFragmentManager()
                    .popBackStackImmediate();

            if (!popped) {
                leavingSession = false;
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
                        )
                        .setNegativeButton("Tiếp tục luyện", null)
                        .setNeutralButton(
                                "Thoát phiên",
                                (dialogInterface, which) -> exitSession()
                        )
                        .setPositiveButton(
                                "Xem tổng kết",
                                (dialogInterface, which) -> finishSession()
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

        ((MainActivity) requireActivity())
                .navigateToTab(R.id.nav_home);
    }

    private void finishSession() {
        if (!canAct()) {
            return;
        }

        // Tạo bản sao để dữ liệu tổng kết độc lập với màn hình chat.
        ArrayList<String> transcript =
                new ArrayList<>(messages);

        boolean[] roles = createRolesArray();

        leavingSession = true;

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
    }

    private void displayMessage(
            String content,
            boolean fromUser
    ) {
        if (messageContainer == null) {
            return;
        }

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(fromUser ? Gravity.END : Gravity.START);

        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        rowParams.bottomMargin = dp(14);
        row.setLayoutParams(rowParams);

        TextView bubble = new TextView(requireContext());

        bubble.setText(content);
        bubble.setTextSize(16);
        bubble.setTextIsSelectable(true);

        bubble.setTextColor(ContextCompat.getColor(
                requireContext(),
                fromUser
                        ? R.color.sm_background
                        : R.color.sm_text_primary
        ));

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

        bubble.setMaxWidth((int) (screenWidth * 0.76f));

        row.addView(
                bubble,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        messageContainer.addView(row);
    }

    private void scrollToBottom() {
        ScrollView scroll = chatScroll;

        if (scroll == null) {
            return;
        }

        scroll.post(() -> {
            if (chatScroll == scroll && isAdded()) {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });
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
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (messageInput != null) {
            draftMessage = messageInput.getText().toString();
        }

        outState.putStringArrayList(
                STATE_MESSAGES,
                new ArrayList<>(messages)
        );

        outState.putBooleanArray(
                STATE_ROLES,
                createRolesArray()
        );

        outState.putString(STATE_DRAFT, draftMessage);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        if (messageInput != null) {
            draftMessage = messageInput.getText().toString();
        }

        if (endDialog != null) {
            endDialog.setOnDismissListener(null);
            endDialog.dismiss();
            endDialog = null;
        }

        messageInput = null;
        messageContainer = null;
        chatScroll = null;

        super.onDestroyView();
    }
}