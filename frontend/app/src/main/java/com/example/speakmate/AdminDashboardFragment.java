package com.example.speakmate;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.speakmate.api.AdminApi;
import com.example.speakmate.auth.SessionManager;
import com.example.speakmate.ui.AdminFragment;

public class AdminDashboardFragment extends AdminFragment {

    private boolean openingSection;
    private AdminApi adminApi;

    public AdminDashboardFragment() {
        super(R.layout.fragment_admin_dashboard);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        if (!hasAdminSession()) return;

        openingSection = false;
        adminApi = new AdminApi(SessionManager.get(requireContext()));

        Button users = view.findViewById(R.id.btnAdminUsers);
        users.setText("Quản lý người dùng ›");
        users.setOnClickListener(button -> openSection(AdminSectionFragment.USERS));

        view.findViewById(R.id.btnAdminTopics)
                .setOnClickListener(button ->
                        openSection(AdminSectionFragment.TOPICS)
                );

        Button decks = view.findViewById(R.id.btnAdminDecks);
        decks.setText("Bộ thẻ hệ thống ›");
        decks.setOnClickListener(button -> openSection(AdminSectionFragment.DECKS));

        view.findViewById(R.id.btnAdminAccount)
                .setOnClickListener(button ->
                        openSection(AdminSectionFragment.ACCOUNT)
                );
    }

    @Override
    public void onResume() {
        super.onResume();
        openingSection = false;
        if (adminApi != null && hasAdminSession()) loadTopicCount();
    }

    private void loadTopicCount() {
        TextView status = requireView().findViewById(R.id.tvAdminDashboardStatus);
        status.setText("Đang tải thống kê...");
        status.setOnClickListener(null);
        call(() -> {
            org.json.JSONObject data = adminApi.statistics();
            for (String field : new String[]{"users", "topics", "systemDecks", "completedSessions"}) data.getLong(field);
            return data;
        }, data -> {
            ((TextView) requireView().findViewById(R.id.tvAdminUserCount)).setText(data.optString("users"));
            ((TextView) requireView().findViewById(R.id.tvAdminTopicCount)).setText(data.optString("topics"));
            ((TextView) requireView().findViewById(R.id.tvAdminDeckCount)).setText(data.optString("systemDecks"));
            ((TextView) requireView().findViewById(R.id.tvAdminSessionCount)).setText(data.optString("completedSessions"));
            status.setText("Số liệu mới nhất từ máy chủ. Phiên hoàn thành là bài hội thoại đã kết thúc.");
        }, error -> {
            status.setText(error.getMessage() + " Chạm để tải lại.");
            status.setOnClickListener(button -> loadTopicCount());
            showAdminError(error);
        });
    }

    private void openSection(@NonNull String section) {
        if (!isAdded()
                || !isResumed()
                || openingSection
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        openingSection = true;

        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.fragmentContainer,
                        USERS_SECTION(section)
                )
                .addToBackStack("admin_section")
                .commit();
    }

    @NonNull
    private Fragment USERS_SECTION(@NonNull String section) {
        if (AdminSectionFragment.USERS.equals(section)) return new AdminUsersFragment();
        if (AdminSectionFragment.DECKS.equals(section)) return new AdminDecksFragment();
        if (AdminSectionFragment.TOPICS.equals(section)) {
            return new AdminTopicsFragment();
        }

        if (AdminSectionFragment.ACCOUNT.equals(section)) {
            return new AdminAccountFragment();
        }

        throw new IllegalArgumentException("Unsupported admin section");
    }
}
