package com.example.speakmate;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.speakmate.api.AdminApi;
import com.example.speakmate.auth.AuthApi;
import com.example.speakmate.auth.SessionManager;
import com.example.speakmate.ui.AdminFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminTopicsFragment extends AdminFragment {

    private final List<JSONObject> topics = new ArrayList<>();
    private AdminApi adminApi;
    private EditText searchInput;
    private TextWatcher searchWatcher;
    private AlertDialog activeDialog;
    private boolean openingForm;
    private boolean loading;
    private boolean mutating;
    private int nextPage = 1;
    private int totalPages;
    private long total;
    private int requestVersion;

    public AdminTopicsFragment() {
        super(R.layout.fragment_admin_topics);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        if (!hasAdminSession()) return;

        adminApi = new AdminApi(SessionManager.get(requireContext()));
        openingForm = false;
        loading = false;
        mutating = false;
        topics.clear();
        searchInput = view.findViewById(R.id.etAdminTopicSearch);

        view.findViewById(R.id.btnBackAdminTopics)
                .setOnClickListener(button -> returnToDashboard());
        view.findViewById(R.id.btnAdminAddTopic)
                .setOnClickListener(button -> openForm(null));
        view.findViewById(R.id.tvAdminTopicListCount)
                .setOnClickListener(button -> loadTopics(1));

        searchWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                renderTopics();
            }

            @Override
            public void afterTextChanged(Editable text) {
            }
        };
        searchInput.addTextChangedListener(searchWatcher);
        searchInput.setOnEditorActionListener((input, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(input);
                input.clearFocus();
                return true;
            }
            return false;
        });
        renderTopics();
    }

    @Override
    public void onResume() {
        super.onResume();
        openingForm = false;
        if (adminApi != null && hasAdminSession() && !loading && !mutating) loadTopics(1);
    }

    private void loadTopics(int page) {
        if (getView() == null || adminApi == null || loading || mutating) return;
        loading = true;
        int expected = ++requestVersion;
        showStatus("Đang tải chủ đề từ máy chủ…", null);
        renderTopics();

        call(() -> adminApi.topics(page, 20), data -> {
            if (expected != requestVersion) return;
            loading = false;
            if (page == 1) topics.clear();
            JSONArray rows = data.optJSONArray("result");
            if (rows != null) for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row != null && row.optLong("id") > 0) topics.add(row);
            }
            JSONObject meta = data.optJSONObject("meta");
            totalPages = meta == null ? page : meta.optInt("pages", page);
            total = meta == null ? topics.size() : meta.optLong("total", topics.size());
            nextPage = page + 1;
            renderTopics();
            showPageStatus();
        }, error -> {
            if (expected != requestVersion) return;
            loading = false;
            renderTopics();
            showStatus(error.getMessage() + " Chạm để thử lại.", v -> loadTopics(page));
            showAdminError(error);
        });
    }

    private void renderTopics() {
        if (getView() == null || searchInput == null) return;
        LinearLayout container = requireView().findViewById(R.id.adminTopicListContainer);
        TextView count = requireView().findViewById(R.id.tvAdminTopicListCount);
        requireView().findViewById(R.id.btnAdminAddTopic).setEnabled(!loading && !mutating);
        container.removeAllViews();
        String query = normalize(searchInput.getText().toString());
        int matches = 0;

        for (JSONObject topic : topics) {
            if (!normalize(topic.optString("name")).contains(query)
                    && !normalize(topic.optString("description")).contains(query)) continue;
            matches++;
            View item = getLayoutInflater().inflate(R.layout.item_admin_topic, container, false);
            ((TextView) item.findViewById(R.id.tvAdminTopicName)).setText(topic.optString("name"));
            ((TextView) item.findViewById(R.id.tvAdminTopicDescription)).setText(
                    topic.optString("description") + "\nTrình độ gợi ý: " + topic.optString("level"));
            boolean active = topic.optBoolean("active");
            TextView status = item.findViewById(R.id.tvAdminTopicStatus);
            status.setText(active ? "Đang hiển thị" : "Đang ẩn");
            status.setTextColor(ContextCompat.getColor(requireContext(),
                    active ? R.color.sm_success : R.color.sm_warning));
            AppCompatButton toggle = item.findViewById(R.id.btnAdminTopicToggleVisibility);
            toggle.setText(active ? "Ẩn chủ đề" : "Hiện chủ đề");
            toggle.setTextColor(ContextCompat.getColor(requireContext(),
                    active ? R.color.sm_warning : R.color.sm_cyan));
            toggle.setEnabled(!loading && !mutating);
            toggle.setOnClickListener(button -> confirmToggleVisibility(topic));
            View edit = item.findViewById(R.id.btnAdminTopicEdit);
            edit.setEnabled(!loading && !mutating);
            edit.setOnClickListener(button -> openForm(topic));
            item.findViewById(R.id.btnAdminTopicDetail)
                    .setOnClickListener(button -> showDetails(topic));
            container.addView(item);
        }

        count.setText(query.isEmpty()
                ? "Đã tải " + topics.size() + "/" + total + " chủ đề · Chạm để làm mới"
                : matches + " kết quả trong " + topics.size() + " chủ đề đã tải · Chạm để làm mới");
    }

    private void showPageStatus() {
        showStatus(topics.isEmpty() ? "Chưa có chủ đề. Hãy thêm chủ đề đầu tiên."
                        : nextPage <= totalPages ? "Chạm để tải thêm chủ đề"
                        : "Đã tải hết chủ đề. Tìm kiếm áp dụng trên danh sách đã tải.",
                nextPage <= totalPages ? v -> loadTopics(nextPage) : null);
    }

    private void showStatus(String message, @Nullable View.OnClickListener action) {
        if (getView() == null) return;
        TextView status = requireView().findViewById(R.id.tvAdminTopicsStatus);
        status.setText(message);
        status.setOnClickListener(action);
    }

    private void showDetails(JSONObject topic) {
        if (!canOpenDialog()) return;
        hideKeyboard(requireView());
        showDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(topic.optString("name"))
                .setMessage("Mô tả:\n" + topic.optString("description")
                        + "\n\nTrình độ gợi ý: " + topic.optString("level")
                        + "\n\nTình huống cho AI:\n" + topic.optString("scenario"))
                .setPositiveButton("Đóng", null)
                .create());
    }

    private void confirmToggleVisibility(JSONObject topic) {
        if (!canOpenDialog()) return;
        hideKeyboard(requireView());
        boolean active = !topic.optBoolean("active");
        showDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(active ? "Hiện chủ đề?" : "Ẩn chủ đề?")
                .setMessage("Cập nhật chủ đề “" + topic.optString("name")
                        + "” trên máy chủ. Chủ đề ẩn sẽ không còn trong danh sách chọn bài luyện mới.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton(active ? "Hiện" : "Ẩn", (dialog, which) -> setActive(topic, active))
                .create());
    }

    private void setActive(JSONObject topic, boolean active) {
        if (loading || mutating || getView() == null) return;
        mutating = true;
        renderTopics();
        showStatus("Đang lưu trạng thái chủ đề…", null);
        long id = topic.optLong("id");
        call(() -> {
            JSONObject updated = adminApi.setActive(id, active);
            if (updated.optLong("id") != id || !updated.has("active")
                    || updated.optBoolean("active") != active) {
                throw new AuthApi.ApiException(0, "INVALID_RESPONSE", "Chưa xác nhận được trạng thái đã lưu.");
            }
            return updated;
        }, updated -> {
            mutating = false;
            for (int i = 0; i < topics.size(); i++) {
                if (topics.get(i).optLong("id") == id) topics.set(i, updated);
            }
            renderTopics();
            showPageStatus();
        }, error -> {
            mutating = false;
            renderTopics();
            showStatus(error.getMessage() + " Chạm để tải lại trạng thái.", v -> loadTopics(1));
            showAdminError(error);
        });
    }

    private boolean canOpenDialog() {
        return isAdded() && isResumed() && getView() != null
                && activeDialog == null && !openingForm && !loading && !mutating
                && !getParentFragmentManager().isStateSaved();
    }

    private void showDialog(AlertDialog dialog) {
        activeDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (activeDialog == dialog) activeDialog = null;
        });
        dialog.show();
    }

    private void openForm(@Nullable JSONObject topic) {
        if (!canOpenDialog()) return;
        hideKeyboard(requireView());
        openingForm = true;
        getParentFragmentManager().beginTransaction().setReorderingAllowed(true)
                .replace(R.id.fragmentContainer, topic == null
                        ? new AdminTopicFormFragment() : AdminTopicFormFragment.edit(topic))
                .addToBackStack("admin_topic_form").commit();
    }

    private void returnToDashboard() {
        if (!isAdded() || !isResumed() || openingForm || getParentFragmentManager().isStateSaved()) return;
        if (getView() != null) hideKeyboard(requireView());
        getParentFragmentManager().popBackStack();
    }

    private String normalize(String text) {
        return text.trim().toLowerCase(Locale.ROOT);
    }

    private void hideKeyboard(View view) {
        WindowCompat.getInsetsController(requireActivity().getWindow(), view)
                .hide(WindowInsetsCompat.Type.ime());
    }

    @Override
    public void onDestroyView() {
        requestVersion++;
        loading = false;
        mutating = false;
        if (searchInput != null && searchWatcher != null) searchInput.removeTextChangedListener(searchWatcher);
        if (activeDialog != null) {
            activeDialog.setOnDismissListener(null);
            activeDialog.dismiss();
            activeDialog = null;
        }
        searchInput = null;
        searchWatcher = null;
        adminApi = null;
        super.onDestroyView();
    }
}
