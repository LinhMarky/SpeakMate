package com.example.speakmate;

import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Patterns;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.speakmate.ui.NetworkFragment;
import com.example.speakmate.auth.AuthApi;

public class RegisterFragment extends NetworkFragment {

    private boolean busy;

    @Override
    protected boolean requiresAuthentication() {
        return false;
    }

    public RegisterFragment() {
        super(R.layout.fragment_register);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        busy = false;

        EditText nameInput =
                view.findViewById(R.id.etRegisterName);

        EditText emailInput =
                view.findViewById(R.id.etRegisterEmail);

        EditText passwordInput =
                view.findViewById(R.id.etRegisterPassword);

        EditText confirmInput =
                view.findViewById(R.id.etRegisterConfirmPassword);

        CheckBox showPassword =
                view.findViewById(R.id.cbRegisterShowPassword);

        TextView errorText =
                view.findViewById(R.id.tvRegisterError);

        showPassword.setChecked(false);

        showPassword.setOnCheckedChangeListener(
                (button, isChecked) -> {
                    setPasswordVisible(passwordInput, isChecked);
                    setPasswordVisible(confirmInput, isChecked);
                }
        );

        view.findViewById(R.id.btnBackToLogin)
                .setOnClickListener(button -> goBack());

        view.findViewById(R.id.btnRegister)
                .setOnClickListener(button -> {
                    errorText.setVisibility(View.GONE);

                    String name = nameInput.getText()
                            .toString()
                            .trim();

                    String email = emailInput.getText()
                            .toString()
                            .trim();

                    String password = passwordInput.getText()
                            .toString();

                    String confirmation = confirmInput.getText()
                            .toString();

                    if (name.isEmpty()) {
                        nameInput.setError(
                                "Bạn hãy nhập tên hiển thị."
                        );
                        nameInput.requestFocus();
                        return;
                    }

                    if (email.isEmpty()
                            || !Patterns.EMAIL_ADDRESS
                            .matcher(email)
                            .matches()) {
                        emailInput.setError(
                                "Bạn hãy nhập email hợp lệ."
                        );
                        emailInput.requestFocus();
                        return;
                    }

                    if (password.length() < 8 || password.length() > 64
                            || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
                        passwordInput.setError(
                                "Mật khẩu cần 8–64 ký tự, tối đa 72 byte UTF-8."
                        );
                        passwordInput.requestFocus();
                        return;
                    }

                    if (confirmation.isEmpty()) {
                        confirmInput.setError(
                                "Bạn hãy nhập lại mật khẩu."
                        );
                        confirmInput.requestFocus();
                        return;
                    }

                    if (!password.equals(confirmation)) {
                        confirmInput.setError(
                                "Mật khẩu nhập lại chưa khớp."
                        );
                        confirmInput.requestFocus();
                        return;
                    }

                    if (busy) return;
                    busy = true;
                    view.findViewById(R.id.progressRegister).setVisibility(View.VISIBLE);
                    view.findViewById(R.id.btnRegister).setEnabled(false);
                    view.findViewById(R.id.btnBackToLogin).setEnabled(false);
                    call(() -> {
                        new AuthApi().register(name, email, password);
                        return true;
                    }, registered -> {
                        busy = false;
                        view.findViewById(R.id.progressRegister).setVisibility(View.GONE);
                        passwordInput.setText("");
                        confirmInput.setText("");
                        android.widget.Toast.makeText(requireContext(),
                                "Đăng ký thành công. Hãy đăng nhập để tiếp tục.", android.widget.Toast.LENGTH_LONG).show();
                        goBack();
                    }, error -> {
                        busy = false;
                        view.findViewById(R.id.progressRegister).setVisibility(View.GONE);
                        view.findViewById(R.id.btnRegister).setEnabled(true);
                        view.findViewById(R.id.btnBackToLogin).setEnabled(true);
                        errorText.setText(error.getMessage());
                        errorText.setVisibility(View.VISIBLE);
                    });
                });
    }

    private void goBack() {
        if (!isAdded()
                || !isResumed()
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        getParentFragmentManager().popBackStack();
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
