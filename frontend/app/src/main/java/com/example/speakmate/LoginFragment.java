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
import com.example.speakmate.ui.NetworkFragment;
import com.example.speakmate.auth.AuthApi;
import com.example.speakmate.auth.SessionManager;
import android.util.Patterns;

public class LoginFragment extends NetworkFragment {

    private boolean openingRegister;
    private boolean busy;

    @Override
    protected boolean requiresAuthentication() {
        return false;
    }

    public LoginFragment() {
        super(R.layout.fragment_login);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        busy = false;

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

                    if (!Patterns.EMAIL_ADDRESS.matcher(username).matches()) {
                        usernameInput.setError(
                                "Bạn hãy nhập email hợp lệ."
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

                    if (busy) return;
                    setBusy(view, true);
                    SessionManager sessions = SessionManager.get(requireContext());
                    call(() -> {
                        var session = new AuthApi().login(username, password);
                        sessions.save(session);
                        return session;
                    }, session -> {
                        setBusy(view, false);
                        passwordInput.setText("");
                        ((MainActivity) requireActivity()).onSignedIn();
                    }, error -> {
                        setBusy(view, false);
                        errorText.setText(error.getMessage());
                        errorText.setVisibility(View.VISIBLE);
                    });
                });

        view.findViewById(R.id.btnOpenRegister)
                .setOnClickListener(button -> openRegister());

        view.findViewById(R.id.btnContinueAsGuest)
                .setOnClickListener(button -> continueAsGuest());

        view.findViewById(R.id.btnLoginGoogle)
                .setOnClickListener(button -> {
                    if (!isAdded() || !isResumed()) {
                        return;
                    }

                    androidx.core.view.WindowCompat
                            .getInsetsController(
                                    requireActivity().getWindow(),
                                    view
                            )
                            .hide(
                                    androidx.core.view.WindowInsetsCompat
                                            .Type.ime()
                            );

                    View focused = requireActivity().getCurrentFocus();

                    if (focused != null) {
                        focused.clearFocus();
                    }

                    android.widget.Toast.makeText(
                            requireContext(),
                            "Đăng nhập Google chưa được kết nối.",
                            android.widget.Toast.LENGTH_SHORT
                    ).show();
                });
    }

    private void setBusy(View view, boolean loading) {
        busy = loading;
        view.findViewById(R.id.progressLogin).setVisibility(loading ? View.VISIBLE : View.GONE);
        for (int id : new int[]{R.id.btnLogin, R.id.btnLoginGoogle, R.id.btnOpenRegister, R.id.btnContinueAsGuest,
                R.id.etLoginUsername, R.id.etLoginPassword}) {
            view.findViewById(id).setEnabled(!loading);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        openingRegister = false;

        View view = getView();

        if (view != null) {
            view.findViewById(R.id.btnOpenRegister)
                    .setEnabled(!busy);
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
