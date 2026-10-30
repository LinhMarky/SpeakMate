package com.example.speakmate;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.speakmate.auth.AuthSession;
import com.example.speakmate.auth.SessionManager;
import com.example.speakmate.ui.AdminFragment;

public class AdminAccountFragment extends AdminFragment {

    private boolean loggingOut;

    public AdminAccountFragment() {
        super(R.layout.fragment_admin_account);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        if (!hasAdminSession()) return;

        AuthSession session = SessionManager.get(requireContext()).load();
        ((TextView) view.findViewById(R.id.tvAdminAccountName))
                .setText("Tên hiển thị: " + session.getName());
        ((TextView) view.findViewById(R.id.tvAdminAccountUsername))
                .setText("Email: " + session.getEmail());
        ((TextView) view.findViewById(R.id.tvAdminAccountRole)).setText("Vai trò: ADMIN");
        ((TextView) view.findViewById(R.id.tvAdminAccountStatus))
                .setText("Phiên đăng nhập quản trị SpeakMate.");

        view.findViewById(R.id.btnBackAdminAccount)
                .setOnClickListener(button -> returnToDashboard());

        view.findViewById(R.id.btnAdminChangePassword).setVisibility(View.VISIBLE);
        view.findViewById(R.id.btnAdminChangePassword).setEnabled(session.user.optBoolean("passwordEnabled"));
        view.findViewById(R.id.btnAdminChangePassword).setOnClickListener(button ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, new AdminChangePasswordFragment())
                        .addToBackStack("admin_password").commit());

        view.findViewById(R.id.btnAdminLogout)
                .setOnClickListener(button ->
                        logout()
                );
    }

    private void returnToDashboard() {
        if (!isAdded()
                || !isResumed()
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        getParentFragmentManager().popBackStack();
    }

    private void logout() {
        if (loggingOut || !hasAdminSession()) return;
        loggingOut = true;
        requireView().findViewById(R.id.btnAdminLogout).setEnabled(false);
        SessionManager sessions = SessionManager.get(requireContext());
        call(() -> {
            sessions.request("POST", "auth/logout", null, 15_000);
            sessions.clear();
            return true;
        }, done -> ((MainActivity) requireActivity()).onSessionExpired(), error -> {
            loggingOut = false;
            requireView().findViewById(R.id.btnAdminLogout).setEnabled(true);
            showAdminError(error);
        });
    }
}
