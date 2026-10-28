package com.example.speakmate;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

public class AdminUsersFragment extends Fragment {

    private static final String STATE_LOCKED = "demo_users_locked";

    private static final String[] STATUS_LABELS = {
            "Tất cả trạng thái",
            "Đang hoạt động",
            "Đã khóa"
    };

    // Dữ liệu minh họa, không phải tài khoản trên máy chủ.
    private static final String[] NAMES = {
            "Người học mẫu 01",
            "Người học mẫu 02",
            "Người học mẫu 03"
    };

    private static final String[] USERNAMES = {
            "learner_demo_01",
            "learner_demo_02",
            "learner_demo_03"
    };

    private static final String[] EMAILS = {
            "learner01@example.com",
            "learner02@example.com",
            "learner03@example.com"
    };

    private boolean[] locked = {false, false, true};

    private EditText searchInput;
    private Spinner statusSpinner;
    private TextWatcher searchWatcher;
    private AlertDialog activeDialog;

    public AdminUsersFragment() {
        super(R.layout.fragment_admin_users);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            boolean[] restored =
                    savedInstanceState.getBooleanArray(STATE_LOCKED);

            if (restored != null && restored.length == NAMES.length) {
                locked = restored.clone();
            }
        }
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        searchInput = view.findViewById(R.id.etAdminUserSearch);
        statusSpinner = view.findViewById(R.id.spAdminUserStatus);

        statusSpinner.setAdapter(createStatusAdapter());

        TextView statusNote =
                view.findViewById(R.id.tvAdminUsersStatus);

        statusNote.setText(
                "DỮ LIỆU MẪU · Các tài khoản và thao tác khóa/mở khóa "
                        + "chỉ để kiểm tra giao diện. "
                        + "Thay đổi chưa được lưu lên máy chủ."
        );

        view.findViewById(R.id.btnBackAdminUsers)
                .setOnClickListener(button -> returnToDashboard());

        searchWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence text,
                    int start,
                    int count,
                    int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence text,
                    int start,
                    int before,
                    int count
            ) {
                renderUsers();
            }

            @Override
            public void afterTextChanged(Editable text) {
            }
        };

        searchInput.addTextChangedListener(searchWatcher);

        searchInput.setOnEditorActionListener(
                (input, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_SEARCH
                            || actionId == EditorInfo.IME_ACTION_DONE) {
                        hideKeyboard(input);
                        input.clearFocus();
                        return true;
                    }

                    return false;
                }
        );

        statusSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View item,
                            int position,
                            long id
                    ) {
                        renderUsers();
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent
                    ) {
                        renderUsers();
                    }
                }
        );

        renderUsers();
    }

    @Override
    public void onViewStateRestored(
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewStateRestored(savedInstanceState);
        renderUsers();
    }

    private void renderUsers() {
        View root = getView();

        if (root == null
                || searchInput == null
                || statusSpinner == null) {
            return;
        }

        LinearLayout container =
                root.findViewById(R.id.adminUserListContainer);

        TextView countText =
                root.findViewById(R.id.tvAdminUserListCount);

        container.removeAllViews();

        String query = normalize(
                searchInput.getText().toString()
        );

        int filter = statusSpinner.getSelectedItemPosition();
        int visibleCount = 0;

        for (int i = 0; i < NAMES.length; i++) {
            boolean matchesSearch =
                    normalize(NAMES[i]).contains(query)
                            || normalize(USERNAMES[i]).contains(query)
                            || normalize(EMAILS[i]).contains(query);

            boolean matchesStatus =
                    filter <= 0
                            || (filter == 1 && !locked[i])
                            || (filter == 2 && locked[i]);

            if (!matchesSearch || !matchesStatus) {
                continue;
            }

            visibleCount++;

            final int index = i;

            View item = getLayoutInflater().inflate(
                    R.layout.item_admin_user,
                    container,
                    false
            );

            TextView name =
                    item.findViewById(R.id.tvAdminUserName);

            TextView email =
                    item.findViewById(R.id.tvAdminUserEmail);

            TextView status =
                    item.findViewById(R.id.tvAdminUserStatus);

            AppCompatButton toggle =
                    item.findViewById(R.id.btnAdminUserToggleStatus);

            name.setText(NAMES[i]);
            email.setText(EMAILS[i]);

            status.setText(
                    locked[i] ? "Đã khóa · Mẫu" : "Đang hoạt động · Mẫu"
            );

            status.setTextColor(
                    ContextCompat.getColor(
                            requireContext(),
                            locked[i]
                                    ? R.color.sm_error
                                    : R.color.sm_success
                    )
            );

            toggle.setText(
                    locked[i] ? "Mở khóa tài khoản mẫu" : "Khóa tài khoản mẫu"
            );

            toggle.setTextColor(
                    ContextCompat.getColor(
                            requireContext(),
                            locked[i]
                                    ? R.color.sm_cyan
                                    : R.color.sm_error
                    )
            );

            item.findViewById(R.id.btnAdminUserDetail)
                    .setOnClickListener(button -> showDetails(index));

            toggle.setOnClickListener(
                    button -> confirmToggleStatus(index)
            );

            container.addView(item);
        }

        if (visibleCount == 0) {
            countText.setText("Không tìm thấy tài khoản phù hợp.");
        } else {
            countText.setText(visibleCount + " tài khoản mẫu");
        }
    }

    private void showDetails(int index) {
        if (!canOpenDialog()) {
            return;
        }

        hideKeyboard(requireView());

        AlertDialog dialog =
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(NAMES[index])
                        .setMessage(
                                "Tên đăng nhập: " + USERNAMES[index]
                                        + "\n\nEmail: " + EMAILS[index]
                                        + "\n\nVai trò: Người học"
                                        + "\n\nTrạng thái: "
                                        + (locked[index]
                                        ? "Đã khóa"
                                        : "Đang hoạt động")
                                        + "\n\nĐây là tài khoản minh họa."
                        )
                        .setPositiveButton("Đóng", null)
                        .create();

        showDialog(dialog);
    }

    private void confirmToggleStatus(int index) {
        if (!canOpenDialog()) {
            return;
        }

        hideKeyboard(requireView());

        boolean willLock = !locked[index];

        AlertDialog dialog =
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(
                                willLock
                                        ? "Khóa tài khoản mẫu?"
                                        : "Mở khóa tài khoản mẫu?"
                        )
                        .setMessage(
                                "Thay đổi trạng thái của "
                                        + NAMES[index]
                                        + " để thử giao diện. "
                                        + "Thao tác không tác động "
                                        + "đến tài khoản thật."
                        )
                        .setNegativeButton("Hủy", null)
                        .setPositiveButton(
                                willLock ? "Khóa" : "Mở khóa",
                                (ignored, which) -> {
                                    locked[index] = willLock;
                                    renderUsers();
                                }
                        )
                        .create();

        showDialog(dialog);
    }

    private boolean canOpenDialog() {
        return isAdded()
                && isResumed()
                && getView() != null
                && activeDialog == null
                && !getParentFragmentManager().isStateSaved();
    }

    private void showDialog(@NonNull AlertDialog dialog) {
        activeDialog = dialog;

        dialog.setOnDismissListener(ignored -> {
            if (activeDialog == dialog) {
                activeDialog = null;
            }
        });

        dialog.show();
    }

    private ArrayAdapter<String> createStatusAdapter() {
        return new ArrayAdapter<String>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                STATUS_LABELS
        ) {
            @NonNull
            @Override
            public View getView(
                    int position,
                    @Nullable View convertView,
                    @NonNull ViewGroup parent
            ) {
                View item = super.getView(
                        position,
                        convertView,
                        parent
                );

                styleText(item);
                return item;
            }

            @Override
            public View getDropDownView(
                    int position,
                    @Nullable View convertView,
                    @NonNull ViewGroup parent
            ) {
                View item = super.getDropDownView(
                        position,
                        convertView,
                        parent
                );

                styleText(item);

                item.setBackgroundColor(
                        ContextCompat.getColor(
                                requireContext(),
                                R.color.sm_surface
                        )
                );

                return item;
            }

            private void styleText(@NonNull View item) {
                if (!(item instanceof TextView)) {
                    return;
                }

                TextView text = (TextView) item;

                text.setTextColor(
                        ContextCompat.getColor(
                                requireContext(),
                                R.color.sm_text_primary
                        )
                );

                text.setTextSize(15);
                text.setGravity(Gravity.CENTER_VERTICAL);
                text.setMinHeight(dp(48));
                text.setPadding(dp(12), dp(8), dp(12), dp(8));
            }
        };
    }

    private String normalize(@NonNull String text) {
        return text.trim().toLowerCase(Locale.ROOT);
    }

    private void hideKeyboard(@NonNull View view) {
        WindowCompat.getInsetsController(
                requireActivity().getWindow(),
                view
        ).hide(WindowInsetsCompat.Type.ime());
    }

    private void returnToDashboard() {
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

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBooleanArray(STATE_LOCKED, locked.clone());
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        if (searchInput != null && searchWatcher != null) {
            searchInput.removeTextChangedListener(searchWatcher);
        }

        if (activeDialog != null) {
            activeDialog.setOnDismissListener(null);
            activeDialog.dismiss();
            activeDialog = null;
        }

        searchInput = null;
        statusSpinner = null;
        searchWatcher = null;

        super.onDestroyView();
    }
}