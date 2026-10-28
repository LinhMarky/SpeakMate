package com.example.speakmate;

import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

public class AdminChangePasswordFragment extends Fragment {

    public AdminChangePasswordFragment() {
        super(R.layout.fragment_admin_change_password);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        EditText currentPassword =
                view.findViewById(R.id.etAdminCurrentPassword);

        EditText newPassword =
                view.findViewById(R.id.etAdminNewPassword);

        EditText confirmation =
                view.findViewById(R.id.etAdminConfirmPassword);

        CheckBox showPasswords =
                view.findViewById(R.id.cbAdminShowPasswords);

        showPasswords.setChecked(false);

        showPasswords.setOnCheckedChangeListener(
                (button, visible) -> {
                    setPasswordVisible(currentPassword, visible);
                    setPasswordVisible(newPassword, visible);
                    setPasswordVisible(confirmation, visible);
                }
        );

        confirmation.setOnEditorActionListener(
                (input, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        hideKeyboard(input);
                        input.clearFocus();
                        return true;
                    }

                    return false;
                }
        );

        view.findViewById(R.id.btnBackAdminPassword)
                .setOnClickListener(button -> goBack());

        view.findViewById(R.id.btnSaveAdminPassword)
                .setOnClickListener(button -> validateForm(view));
    }

    private void validateForm(@NonNull View view) {
        EditText currentInput =
                view.findViewById(R.id.etAdminCurrentPassword);

        EditText newInput =
                view.findViewById(R.id.etAdminNewPassword);

        EditText confirmInput =
                view.findViewById(R.id.etAdminConfirmPassword);

        TextView status =
                view.findViewById(R.id.tvAdminPasswordStatus);

        status.setVisibility(View.GONE);

        String current = currentInput.getText().toString();
        String next = newInput.getText().toString();
        String confirm = confirmInput.getText().toString();

        if (current.isEmpty()) {
            currentInput.setError("Bạn hãy nhập mật khẩu hiện tại.");
            currentInput.requestFocus();
            return;
        }

        if (next.isEmpty()) {
            newInput.setError("Bạn hãy nhập mật khẩu mới.");
            newInput.requestFocus();
            return;
        }

        if (confirm.isEmpty()) {
            confirmInput.setError("Bạn hãy nhập lại mật khẩu mới.");
            confirmInput.requestFocus();
            return;
        }

        if (!next.equals(confirm)) {
            confirmInput.setError("Mật khẩu nhập lại chưa khớp.");
            confirmInput.requestFocus();
            return;
        }

        hideKeyboard(view);

        View focused = requireActivity().getCurrentFocus();

        if (focused != null) {
            focused.clearFocus();
        }

        status.setText(
                "Chưa thể đổi mật khẩu vì chưa kết nối máy chủ "
                        + "và chưa có phiên đăng nhập."
        );

        status.setVisibility(View.VISIBLE);
    }

    private void setPasswordVisible(
            @NonNull EditText input,
            boolean visible
    ) {
        int cursor = input.getSelectionStart();

        input.setTransformationMethod(
                visible
                        ? HideReturnsTransformationMethod.getInstance()
                        : PasswordTransformationMethod.getInstance()
        );

        input.setSelection(
                Math.max(0, Math.min(cursor, input.length()))
        );
    }

    private void hideKeyboard(@NonNull View view) {
        WindowCompat.getInsetsController(
                requireActivity().getWindow(),
                view
        ).hide(WindowInsetsCompat.Type.ime());
    }

    private void goBack() {
        if (!isAdded()
                || !isResumed()
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        View view = getView();

        if (view != null) {
            hideKeyboard(view);
        }

        getParentFragmentManager().popBackStack();
    }
}