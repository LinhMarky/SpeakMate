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
import androidx.fragment.app.Fragment;

public class RegisterFragment extends Fragment {

    public RegisterFragment() {
        super(R.layout.fragment_register);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        EditText nameInput =
                view.findViewById(R.id.etRegisterName);

        EditText usernameInput =
                view.findViewById(R.id.etRegisterUsername);

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

                    String username = usernameInput.getText()
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

                    if (username.isEmpty()) {
                        usernameInput.setError(
                                "Bạn hãy nhập tên đăng nhập."
                        );
                        usernameInput.requestFocus();
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

                    if (password.isEmpty()) {
                        passwordInput.setError(
                                "Bạn hãy nhập mật khẩu."
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

                    errorText.setText(
                            "Chức năng đăng ký chưa kết nối máy chủ."
                    );

                    errorText.setVisibility(View.VISIBLE);
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