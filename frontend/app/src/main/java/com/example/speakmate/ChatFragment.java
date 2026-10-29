package com.example.speakmate;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.example.speakmate.api.PendingRequests;
import com.example.speakmate.ui.NetworkFragment;
import org.json.JSONArray;
import org.json.JSONObject;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.UUID;

public class ChatFragment extends NetworkFragment {

    private long conversationId;
    private PendingRequests pending;
    private JSONObject detail;
    private TextView networkStatus;
    private boolean loading, sending, ready, open = true, mayRetry;
    private String sendError;
    private boolean ending;
    private final Handler pollHandler = new Handler(Looper.getMainLooper());

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

    public static ChatFragment newInstance(long conversationId) {
        ChatFragment fragment = new ChatFragment();
        Bundle arguments = new Bundle();
        arguments.putLong("conversationId", conversationId);
        fragment.setArguments(arguments);
        return fragment;
    }

    public static ChatFragment newReviewInstance(long conversationId) {
        ChatFragment fragment = newInstance(conversationId);
        fragment.getArguments().putBoolean(ARG_REVIEW_ONLY, true);
        return fragment;
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
            conversationId = arguments.getLong("conversationId");
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

    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        leavingSession = false;
        loading = false;
        sending = false;
        ending = false;
        ready = reviewOnly;
        pending = new PendingRequests(requireContext());
        networkStatus = view.findViewById(R.id.tvChatState);
        networkStatus.setOnClickListener(v -> loadConversation(true));
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

        if (conversationId > 0) loadConversation(false);
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

    private String pendingKey() {
        return "turn-" + conversationId;
    }

    private boolean canSend() {
        return ready && open && !loading && !sending && !ending && detail != null
                && pending.get(pendingKey()) == null && detail.optInt("userTurnCount") < 20;
    }

    private void updateInputControls() {
        View view = getView();
        if (view == null || pending == null) return;
        boolean free = !reviewOnly && !leavingSession && !ending && !permissionRequestPending;
        boolean editable = free && !voicePending && canSend();
        // Micro vẫn phải nhận ACTION_UP khi người dùng thả tay.
        view.findViewById(R.id.btnVoiceInput).setEnabled(free && canSend());
        view.findViewById(R.id.etMessage).setEnabled(editable);
        android.widget.Button send = view.findViewById(R.id.btnSendMessage);
        send.setText(mayRetry ? "Thử lại" : "Gửi");
        send.setEnabled(free && !voicePending && !sending && !loading && (mayRetry || canSend()));
        view.findViewById(R.id.btnEndChat).setEnabled(!leavingSession && !ending && !voicePending);
        view.findViewById(R.id.btnBackChat).setEnabled(!leavingSession);
    }

    private void loadConversation(boolean retryAfterRead) {
        if (loading || leavingSession || conversationId <= 0) return;
        loading = true;
        updateInputControls();
        call(() -> api.get("conversations/" + conversationId), data -> {
            loading = false;
            detail = data;
            ready = true;
            renderConversation();
            if (!open && !reviewOnly) {
                showServerResult();
                return;
            }
            if (retryAfterRead && mayRetry && !sending) {
                JSONObject request = pending.get(pendingKey());
                if (request != null) transmit(request);
            } else {
                JSONObject waiting = data.optJSONObject("pendingReply");
                if (waiting != null && "PROCESSING".equals(waiting.optString("status"))) poll();
            }
            updateInputControls();
        }, error -> {
            loading = false;
            networkStatus.setText(error.getMessage() + " Bấm vào đây để tải lại.");
            updateInputControls();
        });
    }

    private void renderConversation() {
        // Transcript chính thức lấy từ server; máy chỉ giữ lượt đang chờ.
        open = "OPEN".equals(detail.optString("status"));
        topic = detail.optString("topicName");
        level = detail.optString("level");
        ((TextView) requireView().findViewById(R.id.tvChatTopic)).setText(topic);
        ((TextView) requireView().findViewById(R.id.tvChatLevel)).setText(
                "Mức luyện " + level + " · " + detail.optInt("userTurnCount") + "/20 lượt");
        JSONArray transcript = detail.optJSONArray("messages");
        JSONObject local = pending.get(pendingKey());
        boolean completedLocal = false;
        messages.clear();
        userMessages.clear();
        messageContainer.removeAllViews();
        if (transcript != null) {
            for (int i = 0; i < transcript.length(); i++) {
                JSONObject message = transcript.optJSONObject(i);
                if (message == null) continue;
                boolean user = "USER".equals(message.optString("sender"));
                String content = message.optString("content");
                messages.add(content);
                userMessages.add(user);
                displayMessage(content, user);
                if (!user && local != null
                        && message.optString("requestId").equals(local.optString("requestId"))) {
                    completedLocal = true;
                }
            }
        }
        try {
            if (completedLocal || !open) {
                pending.clear(pendingKey());
                local = null;
                messageInput.setText("");
                draftMessage = "";
            }
            JSONObject waiting = detail.optJSONObject("pendingReply");
            if (open && waiting != null && transcript != null) {
                for (int i = 0; i < transcript.length(); i++) {
                    JSONObject message = transcript.optJSONObject(i);
                    if (message != null && message.optLong("id") == waiting.optLong("userMessageId")) {
                        local = new JSONObject().put("content", message.optString("content"))
                                .put("requestId", message.optString("requestId"));
                        pending.put(pendingKey(), local);
                        break;
                    }
                }
            }
            mayRetry = open && local != null
                    && (waiting == null || "RETRYABLE".equals(waiting.optString("status")));
            if (local != null) messageInput.setText(local.optString("content"));
            if (!open) networkStatus.setText("Bài đã kết thúc. Tin nhắn đã được lưu.");
            else if (sending || waiting != null && "PROCESSING".equals(waiting.optString("status")))
                networkStatus.setText("Đang chờ trợ lý trả lời…");
            else if (mayRetry) networkStatus.setText((sendError == null ? "Lượt vừa gửi chưa có kết quả." : sendError)
                    + " Bấm Thử lại để tiếp tục đúng lượt.");
            else if (detail.optInt("userTurnCount") >= 20)
                networkStatus.setText("Đã đủ 20 lượt. Hãy kết thúc để xem nhận xét.");
            else networkStatus.setText(messages.isEmpty() ? "Bạn bắt đầu nhé. Nói hoặc nhập câu tiếng Anh." : "Đến lượt bạn.");
        } catch (Exception error) {
            networkStatus.setText("Không lưu được trạng thái lượt chờ trên máy. Hãy tải lại.");
        }
        if (completedLocal) speakLatestPartnerMessage(false);
        // Không tự đọc lại tin cũ khi quay lại màn hình.
        lastAutoSpokenIndex = messages.size() - 1;
        scrollConversation(false);
    }

    private void sendMessage() {
        if (reviewOnly || !canAct() || sending || loading || endDialog != null) return;
        if (mayRetry) {
            loadConversation(true);
            return;
        }
        if (!canSend()) return;
        String content = messageInput.getText().toString().trim();
        if (content.isEmpty()) {
            messageInput.setError("Bạn hãy nhập hoặc nói câu trả lời.");
            return;
        }
        if (content.length() > 2000) {
            messageInput.setError("Mỗi lượt tối đa 2000 ký tự.");
            return;
        }
        try {
            JSONObject request = new JSONObject().put("content", content)
                    .put("requestId", UUID.randomUUID().toString());
            // Lưu trước khi gửi; timeout hay mở lại màn hình vẫn dùng cùng UUID.
            pending.put(pendingKey(), request);
            hideKeyboard();
            messageInput.clearFocus();
            transmit(request);
        } catch (Exception error) {
            networkStatus.setText("Không lưu được câu đang gửi. Hãy thử lại.");
        }
    }

    private void transmit(JSONObject request) {
        sending = true;
        sendError = null;
        mayRetry = false;
        stopChatSpeech();
        networkStatus.setText("Đang chờ trợ lý trả lời…");
        updateInputControls();
        call(() -> api.send(conversationId, request.getString("content"), request.getString("requestId")), response -> {
            sending = false;
            loadConversation(false);
            updateInputControls();
        }, error -> {
            sending = false;
            sendError = error.getMessage();
            if (error.status == 400) {
                try { pending.clear(pendingKey()); } catch (Exception ignored) { }
            }
            networkStatus.setText(error.getMessage() + " Bấm vào đây để kiểm tra và thử lại.");
            // Kiểm tra server trước khi gửi lại sau timeout hoặc lỗi AI.
            loadConversation(false);
            updateInputControls();
        });
    }

    private void poll() {
        pollHandler.removeCallbacksAndMessages(null);
        if (isResumed()) pollHandler.postDelayed(() -> loadConversation(false), 3000);
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
                                conversationId > 0
                                        ? "Kết thúc để nhận góp ý. Thoát phiên sẽ giữ bài đang mở trong lịch sử."
                                        : "Xem tổng kết để nhìn lại phiên luyện, "
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
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!ending);
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
        if (!canAct() || ending) {
            return;
        }

        if (conversationId > 0) {
            ending = true;
            stopChatSpeech();
            updateInputControls();
            networkStatus.setText("Đang kết thúc và tạo nhận xét…");
            call(() -> api.finish(conversationId, false), result -> {
                ending = false;
                open = false;
                showServerResult();
            }, error -> {
                ending = false;
                networkStatus.setText(error.getMessage());
                // Server có thể đã đóng bài trước khi kết nối bị gián đoạn.
                loadConversation(false);
                updateInputControls();
            });
            return;
        }

        ArrayList<String> transcript = new ArrayList<>(messages);
        boolean[] roles = createRolesArray();
        leavingSession = true;
        updateInputControls();
        ((MainActivity) requireActivity()).showSpeakingResult(level, topic, transcript, roles);
    }

    private void showServerResult() {
        if (!isResumed() || leavingSession || getParentFragmentManager().isStateSaved()) return;
        leavingSession = true;
        stopChatSpeech();
        ((MainActivity) requireActivity()).showSpeakingResult(conversationId);
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

    private void displayMessage(
            String content,
            boolean fromUser
    ) {
        if (!isAdded() || messageContainer == null) {
            return;
        }

        android.content.Context context = requireContext();

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(
                (fromUser ? Gravity.END : Gravity.START)
                        | Gravity.TOP
        );

        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        rowParams.bottomMargin = dp(18);

        // Avatar chỉ để trang trí.
        android.widget.ImageView avatar =
                new android.widget.ImageView(context);

        avatar.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO
        );
        avatar.setContentDescription(null);
        avatar.setClickable(false);
        avatar.setFocusable(false);

        avatar.setScaleType(
                android.widget.ImageView.ScaleType.FIT_CENTER
        );

        avatar.setBackgroundResource(
                R.drawable.bg_sm_chat_avatar
        );

        avatar.setImageResource(
                fromUser
                        ? R.drawable.ic_sm_topic_intro
                        : R.drawable.sm_mascot_robot
        );

        avatar.setPadding(0, 0, 0, 0);

        LinearLayout.LayoutParams avatarParams =
                new LinearLayout.LayoutParams(
                        dp(36),
                        dp(36)
                );

        avatarParams.topMargin = dp(2);

        if (fromUser) {
            avatarParams.setMarginStart(dp(8));
        } else {
            avatarParams.setMarginEnd(dp(8));
        }

        // Nội dung tin nhắn.
        TextView bubble = new TextView(context);
        bubble.setText(content == null ? "" : content);
        bubble.setTextSize(15);

        bubble.setTypeface(
                android.graphics.Typeface.create(
                        "sans-serif",
                        android.graphics.Typeface.NORMAL
                )
        );

        bubble.setTextColor(
                ContextCompat.getColor(
                        context,
                        R.color.sm_text_primary
                )
        );

        bubble.setTextIsSelectable(true);
        bubble.setLineSpacing(dp(3), 1f);

        bubble.setBackgroundResource(
                fromUser
                        ? R.drawable.bg_sm_chat_user
                        : R.drawable.bg_sm_chat_ai
        );

        bubble.setPadding(
                dp(14),
                dp(12),
                dp(14),
                dp(12)
        );

        LinearLayout.LayoutParams bubbleParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        if (fromUser) {
            row.addView(bubble, bubbleParams);
            row.addView(avatar, avatarParams);
        } else {
            row.addView(avatar, avatarParams);
            row.addView(bubble, bubbleParams);
        }

        /*
         * Giới hạn chiều rộng theo vùng chat thực tế.
         * Cập nhật lại khi hàng được đo hoặc đổi kích thước.
         */
        row.addOnLayoutChangeListener(
                (v, left, top, right, bottom,
                 oldLeft, oldTop, oldRight, oldBottom) -> {

                    int rowWidth = right - left;

                    if (rowWidth <= 0) {
                        return;
                    }

                    int availableWidth = Math.max(
                            1,
                            rowWidth
                                    - row.getPaddingLeft()
                                    - row.getPaddingRight()
                    );

                    int bubbleMaxWidth = Math.max(
                            1,
                            Math.min(
                                    Math.round(availableWidth * 0.78f),
                                    availableWidth - dp(44)
                            )
                    );

                    if (bubble.getMaxWidth() != bubbleMaxWidth) {
                        bubble.setMaxWidth(bubbleMaxWidth);
                    }
                }
        );

        // Chiều rộng dự phòng trước lần đo đầu tiên.
        int availableWidth =
                messageContainer.getWidth()
                        - messageContainer.getPaddingLeft()
                        - messageContainer.getPaddingRight();

        if (availableWidth <= 0) {
            availableWidth = Math.max(
                    1,
                    getResources().getDisplayMetrics().widthPixels
                            - dp(48)
            );
        }

        bubble.setMaxWidth(
                Math.max(
                        1,
                        Math.min(
                                Math.round(availableWidth * 0.78f),
                                availableWidth - dp(44)
                        )
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
        pollHandler.removeCallbacksAndMessages(null);
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
        pollHandler.removeCallbacksAndMessages(null);
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
