package com.example.speakmate;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
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

public class AdminTopicsFragment extends Fragment {

    private static final String STATE_VISIBLE =
            "admin_demo_topics_visible";

    private static final String[] NAMES = {
            "Introducing Yourself",
            "Ordering Food",
            "Travel",
            "University Life"
    };

    private static final String[] DESCRIPTIONS = {
            "Giới thiệu bản thân, sở thích và quê quán.",
            "Gọi món và giao tiếp tại nhà hàng.",
            "Trao đổi về điểm đến và kế hoạch du lịch.",
            "Trò chuyện về chuyên ngành và cuộc sống đại học."
    };

    private static final String[] LEVELS = {
            "A1", "A2", "A2", "B1"
    };

    private static final String[] OPENINGS = {
            "Hi! What's your name?",
            "Welcome! What would you like to order?",
            "Hello! Where would you like to travel?",
            "Hi! What do you study at university?"
    };

    private static final String[] INSTRUCTIONS = {
            "Đóng vai một người bạn mới. "
                    + "Hỏi từng câu về tên, quê quán và sở thích.",

            "Đóng vai nhân viên nhà hàng. "
                    + "Hỏi người học muốn gọi món gì "
                    + "và tiếp tục tình huống gọi món.",

            "Đóng vai một người bạn cùng lên kế hoạch du lịch. "
                    + "Hỏi về điểm đến, hoạt động và phương tiện.",

            "Đóng vai một sinh viên cùng trường. "
                    + "Hỏi về chuyên ngành, môn học và hoạt động."
    };

    private boolean[] visible = {
            true, true, true, false
    };

    private EditText searchInput;
    private TextWatcher searchWatcher;
    private AlertDialog activeDialog;
    private boolean openingForm;

    public AdminTopicsFragment() {
        super(R.layout.fragment_admin_topics);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            boolean[] restored =
                    savedInstanceState.getBooleanArray(STATE_VISIBLE);

            if (restored != null && restored.length == NAMES.length) {
                visible = restored.clone();
            }
        }
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        openingForm = false;

        searchInput =
                view.findViewById(R.id.etAdminTopicSearch);

        TextView note =
                view.findViewById(R.id.tvAdminTopicsStatus);

        note.setText(
                "DỮ LIỆU MẪU · Tìm kiếm và ẩn/hiện chỉ để thử "
                        + "giao diện quản trị. Thay đổi không ảnh hưởng "
                        + "đến các chủ đề ở màn luyện nói."
        );

        view.findViewById(R.id.btnBackAdminTopics)
                .setOnClickListener(button -> returnToDashboard());

        view.findViewById(R.id.btnAdminAddTopic)
                .setOnClickListener(button -> openForm());

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
                renderTopics();
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

        renderTopics();
    }

    @Override
    public void onViewStateRestored(
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewStateRestored(savedInstanceState);
        renderTopics();
    }

    @Override
    public void onResume() {
        super.onResume();
        openingForm = false;
    }

    private void renderTopics() {
        View root = getView();

        if (root == null || searchInput == null) {
            return;
        }

        LinearLayout container =
                root.findViewById(R.id.adminTopicListContainer);

        TextView countText =
                root.findViewById(R.id.tvAdminTopicListCount);

        container.removeAllViews();

        String query = normalize(
                searchInput.getText().toString()
        );

        int count = 0;

        for (int i = 0; i < NAMES.length; i++) {
            boolean matches =
                    normalize(NAMES[i]).contains(query)
                            || normalize(DESCRIPTIONS[i]).contains(query);

            if (!matches) {
                continue;
            }

            count++;

            final int index = i;

            View item = getLayoutInflater().inflate(
                    R.layout.item_admin_topic,
                    container,
                    false
            );

            TextView name =
                    item.findViewById(R.id.tvAdminTopicName);

            TextView description =
                    item.findViewById(R.id.tvAdminTopicDescription);

            TextView status =
                    item.findViewById(R.id.tvAdminTopicStatus);

            AppCompatButton toggle =
                    item.findViewById(
                            R.id.btnAdminTopicToggleVisibility
                    );

            name.setText(NAMES[i]);

            description.setText(
                    DESCRIPTIONS[i]
                            + "\nTrình độ gợi ý: " + LEVELS[i]
            );

            status.setText(
                    visible[i]
                            ? "Đang hiển thị · Mẫu"
                            : "Đang ẩn · Mẫu"
            );

            status.setTextColor(
                    ContextCompat.getColor(
                            requireContext(),
                            visible[i]
                                    ? R.color.sm_success
                                    : R.color.sm_warning
                    )
            );

            toggle.setText(
                    visible[i]
                            ? "Ẩn chủ đề mẫu"
                            : "Hiện chủ đề mẫu"
            );

            toggle.setTextColor(
                    ContextCompat.getColor(
                            requireContext(),
                            visible[i]
                                    ? R.color.sm_warning
                                    : R.color.sm_cyan
                    )
            );

            item.findViewById(R.id.btnAdminTopicDetail)
                    .setOnClickListener(
                            button -> showDetails(index)
                    );

            toggle.setOnClickListener(
                    button -> confirmToggleVisibility(index)
            );

            item.findViewById(R.id.btnAdminTopicEdit)
                    .setOnClickListener(
                            button -> openEditForm(index)
                    );

            container.addView(item);
        }

        countText.setText(
                count == 0
                        ? "Không tìm thấy chủ đề phù hợp."
                        : count + " chủ đề mẫu"
        );
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
                                "Mô tả:\n" + DESCRIPTIONS[index]
                                        + "\n\nTrình độ gợi ý: "
                                        + LEVELS[index]
                                        + "\n\nCâu mở đầu:\n"
                                        + OPENINGS[index]
                                        + "\n\nHướng dẫn tình huống:\n"
                                        + INSTRUCTIONS[index]
                                        + "\n\nNội dung minh họa, "
                                        + "chưa lấy từ máy chủ."
                        )
                        .setPositiveButton("Đóng", null)
                        .create();

        showDialog(dialog);
    }

    private void confirmToggleVisibility(int index) {
        if (!canOpenDialog()) {
            return;
        }

        hideKeyboard(requireView());

        boolean willShow = !visible[index];

        AlertDialog dialog =
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(
                                willShow
                                        ? "Hiện chủ đề mẫu?"
                                        : "Ẩn chủ đề mẫu?"
                        )
                        .setMessage(
                                "Thay đổi trạng thái của “"
                                        + NAMES[index]
                                        + "” để kiểm tra giao diện. "
                                        + "Thao tác chưa cập nhật "
                                        + "dữ liệu trên máy chủ."
                        )
                        .setNegativeButton("Hủy", null)
                        .setPositiveButton(
                                willShow ? "Hiện" : "Ẩn",
                                (ignored, which) -> {
                                    visible[index] = willShow;
                                    renderTopics();
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
                && !openingForm
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

    private void openForm() {
        if (!canOpenDialog()) {
            return;
        }

        hideKeyboard(requireView());
        openingForm = true;

        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.fragmentContainer,
                        new AdminTopicFormFragment()
                )
                .addToBackStack("admin_topic_form")
                .commit();
    }

    private void openEditForm(int index) {
        if (!canOpenDialog()
                || index < 0
                || index >= NAMES.length) {
            return;
        }

        hideKeyboard(requireView());
        openingForm = true;

        AdminTopicFormFragment form =
                AdminTopicFormFragment.newEditInstance(
                        NAMES[index],
                        DESCRIPTIONS[index],
                        LEVELS[index],
                        OPENINGS[index],
                        INSTRUCTIONS[index]
                );

        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.fragmentContainer,
                        form
                )
                .addToBackStack("admin_edit_topic")
                .commit();
    }

    private void returnToDashboard() {
        if (!isAdded()
                || !isResumed()
                || openingForm
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        View view = getView();

        if (view != null) {
            hideKeyboard(view);
        }

        getParentFragmentManager().popBackStack();
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

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBooleanArray(STATE_VISIBLE, visible.clone());
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
        searchWatcher = null;

        super.onDestroyView();
    }
}