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

    private String level;
    private String topic;

    private final ArrayList<String> messages = new ArrayList<>();
    private final ArrayList<Boolean> userMessages = new ArrayList<>();

    private LinearLayout messageContainer;
    private ScrollView chatScroll;
    private AlertDialog endDialog;

    public ChatFragment() {
        super(R.layout.fragment_chat);
    }

    public static ChatFragment newInstance(
            String level,
            String topic
    ) {
        ChatFragment fragment = new ChatFragment();

        Bundle arguments = new Bundle();
        arguments.putString("level", level);
        arguments.putString("topic", topic);

        fragment.setArguments(arguments);

        return fragment;
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        Bundle arguments = requireArguments();

        level = arguments.getString("level", "A2");
        topic = arguments.getString("topic", "Ordering Food");

        TextView topicText = view.findViewById(R.id.tvChatTopic);
        TextView levelText = view.findViewById(R.id.tvChatLevel);
        EditText input = view.findViewById(R.id.etMessage);

        messageContainer =
                view.findViewById(R.id.messageContainer);
        chatScroll = view.findViewById(R.id.chatScroll);

        topicText.setText(topic);
        levelText.setText("Trình độ " + level);

        restoreMessages(savedInstanceState);

        if (messages.isEmpty()) {
            messages.add(getOpeningMessage());
            userMessages.add(false);
        }

        for (int i = 0; i < messages.size(); i++) {
            displayMessage(
                    messages.get(i),
                    userMessages.get(i)
            );
        }

        view.findViewById(R.id.btnBackChat)
                .setOnClickListener(v -> confirmEndSession());

        view.findViewById(R.id.btnEndChat)
                .setOnClickListener(v -> confirmEndSession());

        view.findViewById(R.id.btnSendMessage)
                .setOnClickListener(v -> {
                    String content =
                            input.getText().toString().trim();

                    if (content.isEmpty()) {
                        input.setError("Bạn hãy nhập câu trả lời.");
                        return;
                    }

                    addMessage(content, true);
                    input.setText("");

                    // Phản hồi demo, chưa kết nối backend.
                    addMessage(getDemoReply(), false);

                    scrollToBottom();
                });

        scrollToBottom();
    }

    private void restoreMessages(@Nullable Bundle state) {
        if (state == null || !messages.isEmpty()) {
            return;
        }

        ArrayList<String> savedMessages =
                state.getStringArrayList("messages");

        boolean[] savedRoles =
                state.getBooleanArray("roles");

        if (savedMessages == null
                || savedRoles == null
                || savedMessages.size() != savedRoles.length) {
            return;
        }

        messages.addAll(savedMessages);

        for (boolean role : savedRoles) {
            userMessages.add(role);
        }
    }

    public void confirmEndSession() {
        if (!isResumed() || endDialog != null) {
            return;
        }

        endDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Kết thúc phiên luyện?")
                .setMessage(
                        "Bạn có thể xem tổng kết hoặc thoát phiên demo."
                )
                .setNegativeButton("Tiếp tục luyện", null)

                .setNeutralButton("Thoát phiên", (dialog, which) ->
                        ((MainActivity) requireActivity())
                                .navigateToTab(R.id.nav_home)
                )

                .setPositiveButton("Xem tổng kết", (dialog, which) -> {
                    int count = 0;

                    for (boolean fromUser : userMessages) {
                        if (fromUser) {
                            count++;
                        }
                    }

                    ((MainActivity) requireActivity())
                            .showSpeakingResult(level, topic, count);
                })
                .create();

        endDialog.setOnDismissListener(dialog -> endDialog = null);
        endDialog.show();
    }

    private String getOpeningMessage() {
        switch (topic) {
            case "Introducing Yourself":
                return "Hi! I'm your practice partner. What's your name?";

            case "Travel":
                return "Hello! Where would you like to travel?";

            case "University Life":
                return "Hi! What do you study at university?";

            default:
                return "Welcome! What would you like to order?";
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

            default:
                return "Would you like something to drink?";
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
        LinearLayout row = new LinearLayout(requireContext());
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

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
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

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putStringArrayList(
                "messages",
                new ArrayList<>(messages)
        );

        boolean[] roles = new boolean[userMessages.size()];

        for (int i = 0; i < userMessages.size(); i++) {
            roles[i] = userMessages.get(i);
        }

        outState.putBooleanArray("roles", roles);
    }

    @Override
    public void onDestroyView() {
        if (endDialog != null) {
            endDialog.setOnDismissListener(null);
            endDialog.dismiss();
            endDialog = null;
        }

        messageContainer = null;
        chatScroll = null;

        super.onDestroyView();
    }
}