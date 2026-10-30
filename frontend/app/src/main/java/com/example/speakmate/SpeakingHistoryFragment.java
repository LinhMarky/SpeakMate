package com.example.speakmate;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.speakmate.ui.NetworkFragment;

import org.json.JSONObject;
import org.json.JSONArray;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpeakingHistoryFragment extends NetworkFragment {

    private boolean openingSession;
    private boolean loading;
    private int nextPage = 1;
    private int totalPages;
    private int requestVersion;
    private final List<JSONObject> sessions = new ArrayList<>();

    public static SpeakingHistoryFragment forVocabulary() {
        SpeakingHistoryFragment fragment = new SpeakingHistoryFragment();
        Bundle args = new Bundle();
        args.putBoolean("forVocabulary", true);
        fragment.setArguments(args);
        return fragment;
    }

    public SpeakingHistoryFragment() {
        super(R.layout.fragment_speaking_history);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btnBackSpeakingHistory)
                .setOnClickListener(button -> goBack());
    }

    @Override
    public void onResume() {
        super.onResume();

        openingSession = false;
        loading = false;
        loadHistory(1);
    }

    private void loadHistory(int page) {
        if (loading || getView() == null) return;
        loading = true;
        int expected = ++requestVersion;
        TextView status = requireView().findViewById(R.id.tvSpeakingHistoryStatus);
        status.setVisibility(View.VISIBLE);
        status.setText("Đang tải lịch sử tài khoản…");
        status.setOnClickListener(null);
        call(() -> api.get("conversations?page=" + page + "&size=20"), data -> {
            if (expected != requestVersion) return;
            loading = false;
            if (page == 1) sessions.clear();
            JSONArray rows = data.optJSONArray("result");
            if (rows != null) for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row != null && row.optLong("id") > 0) sessions.add(row);
            }
            JSONObject meta = data.optJSONObject("meta");
            totalPages = meta == null ? page : meta.optInt("pages", page);
            nextPage = page + 1;
            renderHistory();
        }, error -> {
            if (expected != requestVersion) return;
            loading = false;
            status.setText(error.getMessage() + " Chạm để tải lại.");
            status.setOnClickListener(v -> loadHistory(page));
        });
    }

    private void renderHistory() {
        LinearLayout container = requireView().findViewById(R.id.speakingHistoryContainer);
        TextView count = requireView().findViewById(R.id.tvSpeakingHistoryCount);
        TextView status = requireView().findViewById(R.id.tvSpeakingHistoryStatus);
        container.removeAllViews();
        count.setText("Đã tải " + sessions.size() + " bài luyện · Chạm để làm mới");
        count.setOnClickListener(v -> loadHistory(1));
        boolean vocabulary = getArguments() != null && getArguments().getBoolean("forVocabulary");
        status.setVisibility(View.VISIBLE);
        status.setText(sessions.isEmpty() ? "Chưa có bài luyện. Hãy bắt đầu nói chuyện với AI."
                : nextPage <= totalPages ? "Tải thêm bài luyện"
                : vocabulary ? "Chọn bài đã kết thúc để xem và lưu từ AI đề xuất."
                : "Đã tải hết lịch sử tài khoản.");
        status.setOnClickListener(nextPage <= totalPages ? v -> loadHistory(nextPage) : null);
        DateTimeFormatter date = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.getDefault())
                .withZone(ZoneId.systemDefault());
        for (JSONObject session : sessions) {
            View item = getLayoutInflater().inflate(R.layout.item_speaking_history, container, false);
            ((TextView) item.findViewById(R.id.tvHistoryTopic)).setText(session.optString("topicName"));
            String created = session.optString("createdAt");
            try { created = date.format(Instant.parse(created)); }
            catch (java.time.DateTimeException ignored) { created = "Chưa rõ thời gian"; }
            ((TextView) item.findViewById(R.id.tvHistoryDate)).setText(created);
            boolean open = "OPEN".equals(session.optString("status"));
            String review = session.optString("reviewStatus");
            String reviewText = "COMPLETED".equals(review) ? "Đã có nhận xét"
                    : "PROCESSING".equals(review) ? "Đang tạo nhận xét"
                    : "FAILED".equals(review) ? "Có thể thử lại nhận xét"
                    : "INSUFFICIENT_DATA".equals(review) ? "Chưa đủ dữ liệu nhận xét" : "Chưa có nhận xét";
            ((TextView) item.findViewById(R.id.tvHistoryDetails)).setText(
                    session.optString("level") + " · " + (open ? "Đang luyện" : "Đã kết thúc") + " · " + reviewText);
            TextView button = item.findViewById(R.id.btnOpenHistorySession);
            button.setText(open ? "Tiếp tục bài luyện" : "Xem tổng kết và từ mới");
            button.setOnClickListener(v -> openSession(session.optLong("id"), open));
            // Contract không có API xóa hội thoại.
            item.findViewById(R.id.btnDeleteHistorySession).setVisibility(View.GONE);
            container.addView(item);
        }
    }

    private void openSession(long id, boolean open) {
        if (!canAct()) return;
        openingSession = true;
        androidx.fragment.app.Fragment destination = open ? ChatFragment.newInstance(id)
                : SpeakingResultFragment.newInstance(id);
        if (!open) destination.requireArguments().putBoolean("fromHistory", true);
        getParentFragmentManager().beginTransaction().setReorderingAllowed(true)
                .replace(R.id.fragmentContainer, destination).addToBackStack("server_history_detail").commit();
    }

    private boolean canAct() {
        return isAdded()
                && isResumed()
                && getView() != null
                && !openingSession
                && !getParentFragmentManager().isStateSaved();
    }

    private void goBack() {
        if (!canAct()) {
            return;
        }

        boolean returned = getParentFragmentManager()
                .popBackStackImmediate();

        if (!returned) {
            ((MainActivity) requireActivity())
                    .navigateToTab(R.id.nav_home);
        }
    }

    @Override
    public void onDestroyView() {
        loading = false;
        super.onDestroyView();
    }
}
