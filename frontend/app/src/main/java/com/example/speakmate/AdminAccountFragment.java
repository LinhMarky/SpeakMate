package com.example.speakmate;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class AdminAccountFragment extends Fragment {

    public AdminAccountFragment() {
        super(R.layout.fragment_admin_account);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btnBackAdminAccount)
                .setOnClickListener(button -> returnToDashboard());

        view.findViewById(R.id.btnAdminChangePassword)
                .setOnClickListener(button -> openChangePassword());

        view.findViewById(R.id.btnAdminLogout)
                .setOnClickListener(button ->
                        showUnavailableMessage()
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

    private void openChangePassword() {
        if (!isAdded()
                || !isResumed()
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        // Chặn nhấn liên tục trong khi đang chuyển màn hình.
        View view = getView();

        if (view == null) {
            return;
        }

        View button = view.findViewById(R.id.btnAdminChangePassword);

        if (!button.isEnabled()) {
            return;
        }

        button.setEnabled(false);

        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.fragmentContainer,
                        new AdminChangePasswordFragment()
                )
                .addToBackStack("admin_change_password")
                .commit();
    }

    @Override
    public void onResume() {
        super.onResume();

        View view = getView();

        if (view != null) {
            view.findViewById(R.id.btnAdminChangePassword)
                    .setEnabled(true);
        }
    }

    private void showUnavailableMessage() {
        Toast.makeText(
                requireContext(),
                "Chưa có phiên đăng nhập quản trị.",
                Toast.LENGTH_SHORT
        ).show();
    }
}