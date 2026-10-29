package com.example.speakmate;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

public class AdminTopicFormFragment extends Fragment {

    private static final String STATE_INITIAL_FORM =
            "admin_topic_initial_form";

    private String initialFormSnapshot;

    private androidx.appcompat.app.AlertDialog exitDialog;

    private static final String ARG_EDIT_MODE = "edit_mode";
    private static final String ARG_NAME = "topic_name";
    private static final String ARG_DESCRIPTION = "topic_description";
    private static final String ARG_LEVEL = "topic_level";
    private static final String ARG_OPENING = "topic_opening";
    private static final String ARG_INSTRUCTIONS = "topic_instructions";

    public static AdminTopicFormFragment newEditInstance(
            String name,
            String description,
            String level,
            String opening,
            String instructions
    ) {
        AdminTopicFormFragment fragment =
                new AdminTopicFormFragment();

        Bundle arguments = new Bundle();

        arguments.putBoolean(ARG_EDIT_MODE, true);
        arguments.putString(ARG_NAME, name);
        arguments.putString(ARG_DESCRIPTION, description);
        arguments.putString(ARG_LEVEL, level);
        arguments.putString(ARG_OPENING, opening);
        arguments.putString(ARG_INSTRUCTIONS, instructions);

        fragment.setArguments(arguments);

        return fragment;
    }

    private static final String[] LEVELS = {
            "A1", "A2", "B1", "B2"
    };

    public AdminTopicFormFragment() {
        super(R.layout.fragment_admin_topic_form);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            initialFormSnapshot =
                    savedInstanceState.getString(STATE_INITIAL_FORM);
        }
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        Spinner levelSpinner =
                view.findViewById(R.id.spAdminTopicLevel);

        levelSpinner.setAdapter(createLevelAdapter());

        Bundle arguments = getArguments();

        boolean editing = arguments != null
                && arguments.getBoolean(ARG_EDIT_MODE, false);

        TextView title =
                view.findViewById(R.id.tvAdminTopicFormTitle);

        TextView saveButton =
                view.findViewById(R.id.btnSaveAdminTopic);

        title.setText(editing ? "Sửa chủ đề" : "Thêm chủ đề");

        saveButton.setText(
                editing ? "Lưu thay đổi" : "Lưu chủ đề"
        );

// Chỉ điền dữ liệu ban đầu khi mở form.
// Khi xoay màn hình, Android khôi phục nội dung người dùng đang sửa.
        if (savedInstanceState == null) {
            levelSpinner.setSelection(1);

            if (editing) {
                ((EditText) view.findViewById(R.id.etAdminTopicName))
                        .setText(arguments.getString(ARG_NAME, ""));

                ((EditText) view.findViewById(
                        R.id.etAdminTopicDescription
                )).setText(
                        arguments.getString(ARG_DESCRIPTION, "")
                );

                ((EditText) view.findViewById(R.id.etAdminTopicOpening))
                        .setText(arguments.getString(ARG_OPENING, ""));

                ((EditText) view.findViewById(
                        R.id.etAdminTopicInstructions
                )).setText(
                        arguments.getString(ARG_INSTRUCTIONS, "")
                );

                String selectedLevel =
                        arguments.getString(ARG_LEVEL, "A2");

                for (int i = 0; i < LEVELS.length; i++) {
                    if (LEVELS[i].equals(selectedLevel)) {
                        levelSpinner.setSelection(i);
                        break;
                    }
                }
            }
        }

        view.findViewById(R.id.btnBackAdminTopicForm)
                .setOnClickListener(button -> goBack());

        view.findViewById(R.id.btnSaveAdminTopic)
                .setOnClickListener(button -> validateForm(view));

        if (initialFormSnapshot == null) {
            initialFormSnapshot = createFormSnapshot(view);
        }

        requireActivity()
                .getOnBackPressedDispatcher()
                .addCallback(
                        getViewLifecycleOwner(),
                        new androidx.activity.OnBackPressedCallback(true) {
                            @Override
                            public void handleOnBackPressed() {
                                requestLeaveForm();
                            }
                        }
                );
    }

    private void validateForm(@NonNull View view) {
        EditText name =
                view.findViewById(R.id.etAdminTopicName);

        EditText description =
                view.findViewById(R.id.etAdminTopicDescription);

        EditText opening =
                view.findViewById(R.id.etAdminTopicOpening);

        EditText instructions =
                view.findViewById(R.id.etAdminTopicInstructions);

        TextView status =
                view.findViewById(R.id.tvAdminTopicFormStatus);

        status.setVisibility(View.GONE);

        if (!requireInput(name, "Bạn hãy nhập tên chủ đề.")) {
            return;
        }

        if (!requireInput(description, "Bạn hãy nhập mô tả.")) {
            return;
        }

        if (!requireInput(opening, "Bạn hãy nhập câu mở đầu.")) {
            return;
        }

        if (!requireInput(
                instructions,
                "Bạn hãy nhập hướng dẫn tình huống."
        )) {
            return;
        }

        status.setText(
                "Chưa thể lưu: chức năng quản lý chủ đề "
                        + "chưa kết nối máy chủ."
        );

        status.setVisibility(View.VISIBLE);
    }

    private boolean requireInput(
            @NonNull EditText input,
            @NonNull String message
    ) {
        if (input.getText().toString().trim().isEmpty()) {
            input.setError(message);
            input.requestFocus();
            return false;
        }

        input.setError(null);
        return true;
    }

    private ArrayAdapter<String> createLevelAdapter() {
        return new ArrayAdapter<String>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                LEVELS
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

    private void goBack() {
        requestLeaveForm();
    }

    private void requestLeaveForm() {
        View view = getView();

        if (view == null
                || !isAdded()
                || !isResumed()
                || exitDialog != null
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        androidx.core.view.WindowInsetsCompat insets =
                androidx.core.view.ViewCompat.getRootWindowInsets(view);

        boolean keyboardVisible = insets != null
                && insets.isVisible(
                androidx.core.view.WindowInsetsCompat.Type.ime()
        );

        if (keyboardVisible) {
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

            return;
        }

        String currentSnapshot = createFormSnapshot(view);

        if (initialFormSnapshot == null
                || initialFormSnapshot.equals(currentSnapshot)) {
            getParentFragmentManager().popBackStack();
            return;
        }

        androidx.appcompat.app.AlertDialog dialog =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                        requireContext()
                )
                        .setTitle("Bỏ thay đổi chưa lưu?")
                        .setMessage(
                                "Nội dung chủ đề bạn vừa nhập hoặc chỉnh sửa "
                                        + "sẽ bị mất khi rời màn hình này."
                        )
                        .setNegativeButton("Tiếp tục chỉnh sửa", null)
                        .setPositiveButton(
                                "Bỏ thay đổi",
                                (ignored, which) -> {
                                    if (isAdded()
                                            && isResumed()
                                            && !getParentFragmentManager()
                                            .isStateSaved()) {
                                        getParentFragmentManager()
                                                .popBackStack();
                                    }
                                }
                        )
                        .create();

        exitDialog = dialog;

        dialog.setOnDismissListener(ignored -> {
            if (exitDialog == dialog) {
                exitDialog = null;
            }
        });

        dialog.show();
    }

    private String createFormSnapshot(@NonNull View view) {
        StringBuilder result = new StringBuilder();

        EditText nameInput =
                view.findViewById(R.id.etAdminTopicName);

        EditText descriptionInput =
                view.findViewById(R.id.etAdminTopicDescription);

        Spinner levelSpinner =
                view.findViewById(R.id.spAdminTopicLevel);

        EditText openingInput =
                view.findViewById(R.id.etAdminTopicOpening);

        EditText instructionsInput =
                view.findViewById(R.id.etAdminTopicInstructions);

        appendSnapshotValue(
                result,
                nameInput.getText().toString()
        );

        appendSnapshotValue(
                result,
                descriptionInput.getText().toString()
        );

        appendSnapshotValue(
                result,
                String.valueOf(levelSpinner.getSelectedItemPosition())
        );

        appendSnapshotValue(
                result,
                openingInput.getText().toString()
        );

        appendSnapshotValue(
                result,
                instructionsInput.getText().toString()
        );

        return result.toString();
    }

    private void appendSnapshotValue(
            @NonNull StringBuilder result,
            @NonNull String value
    ) {
        result.append(value.length())
                .append(':')
                .append(value);
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(
                STATE_INITIAL_FORM,
                initialFormSnapshot
        );

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        if (exitDialog != null) {
            exitDialog.setOnDismissListener(null);
            exitDialog.dismiss();
            exitDialog = null;
        }

        super.onDestroyView();
    }
}
