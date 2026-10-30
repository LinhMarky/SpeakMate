package com.example.speakmate.ui;

import android.widget.Toast;

import com.example.speakmate.MainActivity;
import com.example.speakmate.auth.AuthApi;
import com.example.speakmate.auth.AuthSession;
import com.example.speakmate.auth.SessionManager;

/** Chặn mở màn quản trị khi không có phiên ADMIN; backend vẫn kiểm tra quyền từng API. */
public abstract class AdminFragment extends NetworkFragment {

    protected AdminFragment(int layout) {
        super(layout);
    }

    protected boolean hasAdminSession() {
        AuthSession session = SessionManager.get(requireContext()).load();
        if (session != null && session.isAdmin()) return true;

        // Đợi giao dịch mở fragment hoàn tất trước khi chuyển về đăng nhập.
        requireView().post(() -> {
            if (isAdded()) ((MainActivity) requireActivity()).onSessionExpired();
        });
        return false;
    }

    protected void showAdminError(AuthApi.ApiException error) {
        Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG).show();
        if (error.status == 403) {
            ((MainActivity) requireActivity()).onSessionExpired();
        }
    }
}
