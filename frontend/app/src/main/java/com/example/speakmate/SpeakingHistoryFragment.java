package com.example.speakmate;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SpeakingHistoryFragment extends Fragment {

    private boolean openingSession;
    private AlertDialog deleteDialog;

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
        renderHistory();
    }

    private void renderHistory() {
        View view = getView();

        if (view == null) {
            return;
        }

        LinearLayout container =
                view.findViewById(R.id.speakingHistoryContainer);

        TextView countText =
                view.findViewById(R.id.tvSpeakingHistoryCount);

        TextView status =
                view.findViewById(R.id.tvSpeakingHistoryStatus);

        container.removeAllViews();
        status.setVisibility(View.GONE);

        List<SpeakingSession> sessions;

        try {
            sessions = new SpeakingHistoryStore(requireContext())
                    .getSessions();

        } catch (IllegalStateException exception) {
            countText.setText("Chưa tải được lịch sử");

            status.setText(
                    "Không đọc được lịch sử trên thiết bị. "
                            + "Dữ liệu đã lưu chưa bị xóa."
            );

            status.setVisibility(View.VISIBLE);
            return;
        }

        countText.setText(sessions.size() + " phiên đã lưu");

        if (sessions.isEmpty()) {
            status.setText(
                    "Chưa có phiên luyện nào được lưu."
                            + "\n\nHãy bắt đầu một phiên luyện nói "
                            + "và chọn Xem tổng kết khi kết thúc."
            );

            status.setVisibility(View.VISIBLE);
            return;
        }

        DateFormat dateFormat = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT,
                Locale.getDefault()
        );

        for (SpeakingSession session : sessions) {
            View item = getLayoutInflater().inflate(
                    R.layout.item_speaking_history,
                    container,
                    false
            );

            TextView topic =
                    item.findViewById(R.id.tvHistoryTopic);

            TextView date =
                    item.findViewById(R.id.tvHistoryDate);

            TextView details =
                    item.findViewById(R.id.tvHistoryDetails);

            topic.setText(session.getTopic());

            date.setText(
                    dateFormat.format(
                            new Date(session.getFinishedAt())
                    )
            );

            details.setText(
                    "Mức bài luyện: " + session.getLevel()
                            + " · " + session.getUserTurnCount()
                            + " lượt trả lời"
            );

            item.findViewById(R.id.btnOpenHistorySession)
                    .setOnClickListener(
                            button -> openSession(session.getId())
                    );

            item.findViewById(R.id.btnDeleteHistorySession)
                    .setOnClickListener(
                            button -> confirmDelete(session)
                    );

            container.addView(item);
        }
    }

    private void openSession(String sessionId) {
        if (!canAct() || deleteDialog != null) {
            return;
        }

        View currentView = requireView();
        openingSession = true;

        ((MainActivity) requireActivity())
                .openSavedSpeakingSession(sessionId);

        // Nếu mở thất bại và vẫn ở màn lịch sử,
        // cho phép người dùng thử lại.
        currentView.post(() -> {
            if (getView() == currentView && isResumed()) {
                openingSession = false;
            }
        });
    }

    private void confirmDelete(@NonNull SpeakingSession session) {
        if (!canAct() || deleteDialog != null) {
            return;
        }

        AlertDialog dialog =
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Xóa phiên luyện?")
                        .setMessage(
                                "Xóa “" + session.getTopic()
                                        + "” khỏi lịch sử trên thiết bị?"
                                        + "\n\nBộ flashcard đã tạo từ "
                                        + "phiên này vẫn được giữ lại."
                        )
                        .setNegativeButton("Hủy", null)
                        .setPositiveButton(
                                "Xóa",
                                (ignored, which) -> {
                                    if (!isAdded()) {
                                        return;
                                    }

                                    try {
                                        new SpeakingHistoryStore(
                                                requireContext()
                                        ).deleteSession(session.getId());

                                        renderHistory();

                                    } catch (IllegalStateException exception) {
                                        Toast.makeText(
                                                requireContext(),
                                                "Chưa xóa được phiên luyện.",
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }
                                }
                        )
                        .create();

        deleteDialog = dialog;

        dialog.setOnDismissListener(ignored -> {
            if (deleteDialog == dialog) {
                deleteDialog = null;
            }
        });

        dialog.show();
    }

    private boolean canAct() {
        return isAdded()
                && isResumed()
                && getView() != null
                && !openingSession
                && !getParentFragmentManager().isStateSaved();
    }

    private void goBack() {
        if (!canAct() || deleteDialog != null) {
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
        if (deleteDialog != null) {
            deleteDialog.setOnDismissListener(null);
            deleteDialog.dismiss();
            deleteDialog = null;
        }

        super.onDestroyView();
    }
}