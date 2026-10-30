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
import com.example.speakmate.api.AdminApi;
import com.example.speakmate.auth.SessionManager;
import com.example.speakmate.ui.AdminFragment;
import org.json.JSONException;
import org.json.JSONObject;

public class AdminTopicFormFragment extends AdminFragment {

    private static final String STATE_INITIAL_FORM =
            "admin_topic_initial_form";

    private String initialFormSnapshot;
    private static final String STATE_SAVE_PENDING = "admin_topic_save_pending";
    private static final String STATE_SAVE_COMPLETED = "admin_topic_save_completed";
    private static final String ARG_TOPIC = "admin_topic";
    private AdminApi topicApi;
    private long topicId;
    private boolean saving;
    private boolean saveUncertain;
    private boolean saveCompleted;

    private androidx.appcompat.app.AlertDialog exitDialog;

    public static AdminTopicFormFragment edit(@NonNull JSONObject topic) {
        AdminTopicFormFragment fragment =
                new AdminTopicFormFragment();

        Bundle arguments = new Bundle();

        arguments.putString(ARG_TOPIC, topic.toString());

        fragment.setArguments(arguments);

        return fragment;
    }

    private static final String[] LEVELS = {
            "A1", "A2", "B1", "B2", "C1", "C2"
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
            saveUncertain = savedInstanceState.getBoolean(STATE_SAVE_PENDING);
            saveCompleted = savedInstanceState.getBoolean(STATE_SAVE_COMPLETED);
        }
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        if (!hasAdminSession()) return;
        topicApi = new AdminApi(SessionManager.get(requireContext()));

        Spinner levelSpinner =
                view.findViewById(R.id.spAdminTopicLevel);

        levelSpinner.setAdapter(createLevelAdapter());

        JSONObject topic = readTopic();
        boolean editing = getArguments() != null
                && getArguments().containsKey(ARG_TOPIC);
        topicId = topic.optLong("id", 0);

        TextView title =
                view.findViewById(R.id.tvAdminTopicFormTitle);

        TextView saveButton =
                view.findViewById(R.id.btnSaveAdminTopic);

        title.setText(editing ? "Sửa chủ đề" : "Thêm chủ đề");

        saveButton.setText(
                editing ? "Lưu thay đổi" : "Lưu chủ đề"
        );

        // Android tự khôi phục bản nháp khi xoay màn hình.
        if (savedInstanceState == null) {
            levelSpinner.setSelection(1);

            if (editing) {
                ((EditText) view.findViewById(R.id.etAdminTopicName))
                        .setText(topic.optString("name", ""));

                ((EditText) view.findViewById(
                        R.id.etAdminTopicDescription
                )).setText(
                        topic.optString("description", "")
                );

                ((EditText) view.findViewById(
                        R.id.etAdminTopicInstructions
                )).setText(
                        topic.optString("scenario", "")
                );

                String selectedLevel =
                        topic.optString("level", "A2");

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

        if (editing && topicId <= 0) {
            saveUncertain = true;
            showStatus(view, "Không đọc được mã chủ đề. Hãy quay lại danh sách.");
        } else if (saveUncertain) {
            showStatus(view, "Chưa xác định kết quả lưu. Hãy quay lại tải danh sách trước khi thử lại.");
        }
        setFormEnabled(view, !saveUncertain);
    }

    private JSONObject readTopic() {
        try {
            String json = getArguments() == null ? null : getArguments().getString(ARG_TOPIC);
            return json == null ? new JSONObject() : new JSONObject(json);
        } catch (JSONException error) {
            return new JSONObject();
        }
    }

    private void validateForm(@NonNull View view) {
        if (saving || saveUncertain || !hasAdminSession()) return;
        EditText name =
                view.findViewById(R.id.etAdminTopicName);

        EditText description =
                view.findViewById(R.id.etAdminTopicDescription);

        EditText instructions =
                view.findViewById(R.id.etAdminTopicInstructions);

        TextView status =
                view.findViewById(R.id.tvAdminTopicFormStatus);

        status.setVisibility(View.GONE);

        if (!requireInput(name, "Bạn hãy nhập tên chủ đề.")) {
            return;
        }

        if (!validateLength(name, 100, "Tên chủ đề tối đa 100 ký tự.")) {
            return;
        }

        if (!validateLength(description, 2000, "Mô tả tối đa 2000 ký tự.")) {
            return;
        }

        if (!requireInput(
                instructions,
                "Bạn hãy nhập hướng dẫn tình huống."
        )) {
            return;
        }
        if (!validateLength(instructions, 4000, "Tình huống tối đa 4000 ký tự.")) return;

        JSONObject body = new JSONObject();
        try {
            Spinner level = view.findViewById(R.id.spAdminTopicLevel);
            body.put("name", name.getText().toString().trim());
            body.put("description", description.getText().toString().trim());
            body.put("level", LEVELS[level.getSelectedItemPosition()]);
            body.put("scenario", instructions.getText().toString().trim());
        } catch (JSONException error) {
            showStatus(view, "Chưa thể đọc nội dung chủ đề. Hãy thử lại.");
            return;
        }

        saving = true;
        setFormEnabled(view, false);
        showStatus(view, "Đang lưu chủ đề…");
        call(() -> topicApi.saveTopic(topicId, body), saved -> {
            saving = false;
            long savedId = saved.optLong("id", 0);
            if (savedId <= 0 || (topicId > 0 && savedId != topicId)) {
                saveUncertain = true;
                showStatus(view, "Phản hồi lưu không hợp lệ. Hãy quay lại tải danh sách.");
                return;
            }
            initialFormSnapshot = createFormSnapshot(view);
            saveCompleted = true;
            finishSavedForm();
        }, error -> {
            saving = false;
            // Mất kết nối có thể xảy ra sau khi máy chủ đã ghi dữ liệu.
            saveUncertain = error.status == 0 || error.status >= 500;
            setFormEnabled(view, !saveUncertain);
            showStatus(view, saveUncertain
                    ? "Chưa xác định kết quả lưu. Hãy quay lại tải danh sách trước khi thử lại."
                    : error.getMessage());
            showAdminError(error);
        });
    }

    private boolean validateLength(EditText input, int limit, String message) {
        if (input.getText().toString().trim().length() > limit) {
            input.setError(message);
            input.requestFocus();
            return false;
        }
        input.setError(null);
        return true;
    }

    private void setFormEnabled(View view, boolean enabled) {
        int[] ids = {R.id.etAdminTopicName, R.id.etAdminTopicDescription,
                R.id.spAdminTopicLevel, R.id.etAdminTopicInstructions, R.id.btnSaveAdminTopic};
        for (int id : ids) view.findViewById(id).setEnabled(enabled);
    }

    private void showStatus(View view, String message) {
        TextView status = view.findViewById(R.id.tvAdminTopicFormStatus);
        status.setText(message);
        status.setVisibility(View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        finishSavedForm();
    }

    private void finishSavedForm() {
        if (saveCompleted && isResumed() && !getParentFragmentManager().isStateSaved()) {
            saveCompleted = false;
            getParentFragmentManager().popBackStack();
        }
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

        if (saving) {
            if (view != null) showStatus(view, "Đang lưu, bạn chờ một chút nhé.");
            return;
        }

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
        outState.putBoolean(STATE_SAVE_PENDING, saving || saveUncertain);
        outState.putBoolean(STATE_SAVE_COMPLETED, saveCompleted);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        if (saving) {
            saving = false;
            saveUncertain = true;
        }
        if (exitDialog != null) {
            exitDialog.setOnDismissListener(null);
            exitDialog.dismiss();
            exitDialog = null;
        }

        super.onDestroyView();
    }
}
