package com.example.speakmate;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioButton;
import com.example.speakmate.api.PendingRequests;
import com.example.speakmate.auth.SessionManager;
import com.example.speakmate.ui.NetworkFragment;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SpeakingFragment extends NetworkFragment {

    private static final String STATE_LEVEL = "speaking_selected_level";
    private static final String STATE_TOPIC = "speaking_selected_topic_id";

    private int selectedLevelId = R.id.rbA2;
    private long selectedTopicId;

    private boolean openingConversation;
    private boolean loadingTopics;
    private PendingRequests pending;
    private RadioGroup topicGroup;
    private TextView status;

    public SpeakingFragment() {
        super(R.layout.fragment_speaking);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            selectedLevelId = savedInstanceState.getInt(
                    STATE_LEVEL,
                    R.id.rbA2
            );

            selectedTopicId = savedInstanceState.getLong(STATE_TOPIC, 0);
        } else {
            String savedLevel = requireContext()
                    .getSharedPreferences(
                            "speakmate_profile",
                            Context.MODE_PRIVATE
                    )
                    .getString("practice_level", "A2");

            selectedLevelId = getLevelId(savedLevel);
        }

        if (getLevelCode(selectedLevelId) == null) {
            selectedLevelId = R.id.rbA2;
        }

    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        openingConversation = false;
        loadingTopics = false;
        pending = new PendingRequests(requireContext());
        status = view.findViewById(R.id.tvSpeakingState);
        status.setOnClickListener(v -> loadTopics());

        RadioGroup levelGroup =
                view.findViewById(R.id.rgLevel);

        topicGroup = view.findViewById(R.id.rgTopic);
        topicGroup.removeAllViews();

        levelGroup.check(selectedLevelId);

        updateLevelDescription(view, selectedLevelId);

        levelGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (getLevelCode(checkedId) == null) {
                return;
            }

            selectedLevelId = checkedId;
            updateLevelDescription(view, checkedId);
        });

        topicGroup.setOnCheckedChangeListener((group, checkedId) -> {
            View selected = group.findViewById(checkedId);
            if (selected != null && selected.getTag() instanceof Long) {
                selectedTopicId = (Long) selected.getTag();
            }
        });

        view.findViewById(R.id.btnBeginConversation)
                .setOnClickListener(button -> openConversation(view));
        loadTopics();
    }

    @Override
    public void onResume() {
        super.onResume();

        openCreatedConversation();
    }

    private void openConversation(@NonNull View view) {
        if (!isAdded()
                || !isResumed()
                || openingConversation
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        if (SessionManager.get(requireContext()).load() == null) {
            ((MainActivity) requireActivity()).openLogin();
            return;
        }
        JSONObject request = pending.get("create");
        if (request == null) {
            if (selectedTopicId <= 0) return;
            try {
                request = new JSONObject().put("topicId", selectedTopicId)
                        .put("level", getLevelCode(selectedLevelId))
                        .put("createRequestId", UUID.randomUUID().toString());
                pending.put("create", request);
            } catch (Exception error) {
                status.setText("Chưa lưu được yêu cầu. Hãy thử lại.");
                return;
            }
        }
        if (request.optLong("conversationId") > 0) {
            openCreatedConversation();
            return;
        }
        openingConversation = true;
        controls();
        status.setText("Đang mở bài luyện…");
        JSONObject original = request;
        call(() -> api.create(original.getLong("topicId"), original.getString("level"),
                original.getString("createRequestId")), data -> {
            openingConversation = false;
            try {
                long id = data.getLong("id");
                if (id <= 0) throw new IllegalStateException();
                original.put("conversationId", id);
                pending.put("create", original);
                openCreatedConversation();
            } catch (Exception error) {
                status.setText("Bài chưa được mở. Bấm Khôi phục bài vừa tạo để kiểm tra lại.");
            }
            controls();
        }, error -> {
            openingConversation = false;
            if (error.status == 400 || error.status == 404 || "TOPIC_DISABLED".equals(error.code)) {
                pending.clear("create");
            }
            status.setText(error.getMessage());
            controls();
        });
    }

    private void loadTopics() {
        if (loadingTopics || openingConversation) return;
        if (SessionManager.get(requireContext()).load() == null) {
            status.setText("Đăng nhập để chọn tình huống và luyện với AI.");
            controls();
            return;
        }
        loadingTopics = true;
        status.setText("Đang tải chủ đề…");
        controls();
        call(() -> {
            JSONArray topics = new JSONArray();
            int page = 1;
            int pages;
            do {
                JSONObject data = api.get("topics?page=" + page + "&size=100");
                JSONArray rows = data.getJSONArray("result");
                for (int i = 0; i < rows.length(); i++) topics.put(rows.getJSONObject(i));
                pages = data.getJSONObject("meta").getInt("pages");
                page++;
            } while (page <= pages);
            return topics;
        }, rows -> {
            loadingTopics = false;
            topicGroup.removeAllViews();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject topic = rows.optJSONObject(i);
                if (topic == null || topic.optLong("id") <= 0) continue;
                RadioButton option = new RadioButton(requireContext());
                option.setId(View.generateViewId());
                option.setTag(topic.optLong("id"));
                option.setText(topic.optString("name") + "\n"
                        + (topic.isNull("description") ? "" : topic.optString("description"))
                        + " · Mức gợi ý " + topic.optString("level"));
                option.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.sm_text_primary));
                option.setTextSize(14);
                option.setButtonDrawable((android.graphics.drawable.Drawable) null);
                option.setBackgroundResource(R.drawable.bg_option);
                option.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_sm_nav_mic, 0,
                        R.drawable.selector_sm_topic_end, 0);
                float density = getResources().getDisplayMetrics().density;
                int padding = Math.round(12 * density);
                option.setCompoundDrawablePadding(padding);
                option.setMinHeight(Math.round(80 * density));
                option.setPadding(padding, Math.round(14 * density), padding, Math.round(14 * density));
                option.setSaveEnabled(false);
                RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(-1, -2);
                params.topMargin = i == 0 ? 0 : Math.round(10 * density);
                topicGroup.addView(option, params);
                if (selectedTopicId == 0 || selectedTopicId == topic.optLong("id")) {
                    selectedTopicId = topic.optLong("id");
                    option.setChecked(true);
                }
            }
            if (topicGroup.getCheckedRadioButtonId() == -1 && topicGroup.getChildCount() > 0) {
                ((RadioButton) topicGroup.getChildAt(0)).setChecked(true);
            }
            status.setText(rows.length() == 0 ? "Chưa có chủ đề đang mở. Bấm để tải lại."
                    : "Bạn sẽ bắt đầu nói trước. Bấm dòng này để tải lại chủ đề.");
            controls();
        }, error -> {
            loadingTopics = false;
            status.setText(error.getMessage() + " Bấm để tải lại.");
            controls();
        });
    }

    private void controls() {
        if (getView() == null) return;
        boolean signedIn = SessionManager.get(requireContext()).load() != null;
        android.widget.Button begin = getView().findViewById(R.id.btnBeginConversation);
        boolean recovery = pending.get("create") != null;
        begin.setText(!signedIn ? "Đăng nhập" : recovery ? "Khôi phục bài vừa tạo" : "Bắt đầu hội thoại");
        begin.setEnabled(!openingConversation && (!signedIn || recovery || !loadingTopics && topicGroup.getChildCount() > 0));
    }

    private void openCreatedConversation() {
        if (pending == null || !isResumed() || getParentFragmentManager().isStateSaved()) return;
        JSONObject request = pending.get("create");
        if (request == null || request.optLong("conversationId") <= 0) return;
        getParentFragmentManager().beginTransaction().setReorderingAllowed(true)
                .replace(R.id.fragmentContainer, ChatFragment.newInstance(request.optLong("conversationId")))
                .addToBackStack("chat").commit();
        pending.clear("create");
    }

    private int getLevelId(@Nullable String level) {
        if (level == null) {
            return R.id.rbA2;
        }

        switch (level) {
            case "A1":
                return R.id.rbA1;
            case "A2":
                return R.id.rbA2;
            case "B1":
                return R.id.rbB1;
            case "B2":
                return R.id.rbB2;
            case "C1":
                return R.id.rbC1;
            case "C2":
                return R.id.rbC2;
            default:
                return R.id.rbA2;
        }
    }

    @Nullable
    private String getLevelCode(int checkedId) {
        if (checkedId == R.id.rbA1) {
            return "A1";
        } else if (checkedId == R.id.rbA2) {
            return "A2";
        } else if (checkedId == R.id.rbB1) {
            return "B1";
        } else if (checkedId == R.id.rbB2) {
            return "B2";
        } else if (checkedId == R.id.rbC1) {
            return "C1";
        } else if (checkedId == R.id.rbC2) {
            return "C2";
        }

        return null;
    }

    private void updateLevelDescription(
            @NonNull View view,
            int checkedId
    ) {
        TextView description =
                view.findViewById(R.id.tvLevelDescription);

        String text;

        if (checkedId == R.id.rbA1) {
            text = "A1 · Câu ngắn, từ cơ bản và chủ đề quen thuộc.";
        } else if (checkedId == R.id.rbA2) {
            text = "A2 · Luyện các tình huống giao tiếp hằng ngày.";
        } else if (checkedId == R.id.rbB1) {
            text = "B1 · Kể trải nghiệm và giải thích ý kiến của bạn.";
        } else if (checkedId == R.id.rbB2) {
            text = "B2 · Thảo luận, so sánh và trình bày lập luận.";
        } else if (checkedId == R.id.rbC1) {
            text = "C1 · Diễn đạt chính xác, linh hoạt và trao đổi ý nghĩa hàm ẩn.";
        } else if (checkedId == R.id.rbC2) {
            text = "C2 · Lập luận sâu, diễn đạt sắc thái và ngôn ngữ tự nhiên.";
        } else {
            text = "Chọn trình độ bạn muốn luyện.";
        }

        description.setText(text);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_LEVEL, selectedLevelId);
        outState.putLong(STATE_TOPIC, selectedTopicId);

        super.onSaveInstanceState(outState);
    }
}
