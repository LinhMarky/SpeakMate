package com.example.speakmate;

import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class LoginFragment extends Fragment {

    private boolean openingRegister;

    public LoginFragment() {
        super(R.layout.fragment_login);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btnForgotPassword)
                .setOnClickListener(button ->
                        android.widget.Toast.makeText(
                                requireContext(),
                                "Chức năng quên mật khẩu đang được cập nhật.",
                                android.widget.Toast.LENGTH_SHORT
                        ).show()
                );

        openingRegister = false;

        EditText usernameInput =
                view.findViewById(R.id.etLoginUsername);

        EditText passwordInput =
                view.findViewById(R.id.etLoginPassword);

        CheckBox showPassword =
                view.findViewById(R.id.cbLoginShowPassword);

        TextView errorText =
                view.findViewById(R.id.tvLoginError);

        showPassword.setChecked(false);

        showPassword.setOnCheckedChangeListener(
                (button, isChecked) ->
                        setPasswordVisible(passwordInput, isChecked)
        );

        view.findViewById(R.id.btnLogin)
                .setOnClickListener(button -> {
                    String username = usernameInput.getText()
                            .toString()
                            .trim();

                    String password = passwordInput.getText()
                            .toString();

                    errorText.setVisibility(View.GONE);

                    if (username.isEmpty()) {
                        usernameInput.setError(
                                "Bạn hãy nhập tên đăng nhập."
                        );
                        usernameInput.requestFocus();
                        return;
                    }

                    if (password.isEmpty()) {
                        passwordInput.setError(
                                "Bạn hãy nhập mật khẩu."
                        );
                        passwordInput.requestFocus();
                        return;
                    }

                    errorText.setText(
                            "Chức năng đăng nhập chưa kết nối máy chủ."
                    );

                    errorText.setVisibility(View.VISIBLE);
                });

        view.findViewById(R.id.btnOpenRegister)
                .setOnClickListener(button -> openRegister());

        view.findViewById(R.id.btnContinueAsGuest)
                .setOnClickListener(button -> continueAsGuest());
    }

    @Override
    public void onResume() {
        super.onResume();

        openingRegister = false;

        View view = getView();

        if (view != null) {
            view.findViewById(R.id.btnOpenRegister)
                    .setEnabled(true);
        }
    }

    private void openRegister() {
        if (!isAdded()
                || !isResumed()
                || openingRegister
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        View view = getView();

        if (view == null) {
            return;
        }

        openingRegister = true;

        view.findViewById(R.id.btnOpenRegister)
                .setEnabled(false);

        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.fragmentContainer,
                        new RegisterFragment()
                )
                .addToBackStack("register")
                .commit();
    }

    private void continueAsGuest() {
        if (!isAdded()
                || !isResumed()
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        View view = getView();

        if (view == null) {
            return;
        }

        // Đóng bàn phím nếu người dùng đang nhập.
        androidx.core.view.WindowCompat.getInsetsController(
                requireActivity().getWindow(),
                view
        ).hide(
                androidx.core.view.WindowInsetsCompat.Type.ime()
        );

        View focused = requireActivity().getCurrentFocus();

        if (focused != null) {
            focused.clearFocus();
        }

        // Quay lại màn hình trước khi mở đăng nhập.
        boolean returned = getParentFragmentManager()
                .popBackStackImmediate();

        // Dự phòng nếu sau này màn đăng nhập được mở mà không có lịch sử.
        if (!returned) {
            ((MainActivity) requireActivity())
                    .navigateToTab(R.id.nav_home);
        }
    }

    private void setPasswordVisible(
            @NonNull EditText input,
            boolean visible
    ) {
        int cursorPosition = input.getSelectionStart();

        input.setTransformationMethod(
                visible
                        ? HideReturnsTransformationMethod.getInstance()
                        : PasswordTransformationMethod.getInstance()
        );

        int safePosition = Math.max(
                0,
                Math.min(cursorPosition, input.length())
        );

        input.setSelection(safePosition);
    }
}