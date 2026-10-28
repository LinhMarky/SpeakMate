package com.example.speakmate;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Locale;

public class AdminDeckFormFragment extends Fragment {

    private static final String ARG_EDIT_MODE = "edit_mode";
    private static final String ARG_DECK_NAME = "deck_name";
    private static final String ARG_DECK_LEVEL = "deck_level";

    public static AdminDeckFormFragment newEditInstance(
            FlashcardDeck deck,
            java.util.List<Flashcard> cards
    ) {
        AdminDeckFormFragment fragment =
                new AdminDeckFormFragment();

        Bundle arguments = new Bundle();

        arguments.putBoolean(ARG_EDIT_MODE, true);
        arguments.putString(ARG_DECK_NAME, deck.getName());
        arguments.putString(ARG_DECK_LEVEL, deck.getLevel());

        ArrayList<String> initialWords = new ArrayList<>();
        ArrayList<String> initialMeanings = new ArrayList<>();
        ArrayList<String> initialExamples = new ArrayList<>();

        for (Flashcard card : cards) {
            initialWords.add(card.getWord());
            initialMeanings.add(card.getMeaning());

            initialExamples.add(
                    card.getExample() == null
                            ? ""
                            : card.getExample()
            );
        }

        arguments.putStringArrayList(STATE_WORDS, initialWords);
        arguments.putStringArrayList(STATE_MEANINGS, initialMeanings);
        arguments.putStringArrayList(STATE_EXAMPLES, initialExamples);

        fragment.setArguments(arguments);

        return fragment;
    }

    private static final String[] LEVELS = {
            "A1", "A2", "B1", "B2", "C1", "C2"
    };

    private static final String STATE_WORDS = "admin_deck_words";
    private static final String STATE_MEANINGS = "admin_deck_meanings";
    private static final String STATE_EXAMPLES = "admin_deck_examples";

    private final ArrayList<String> words = new ArrayList<>();
    private final ArrayList<String> meanings = new ArrayList<>();
    private final ArrayList<String> examples = new ArrayList<>();

    private AlertDialog deleteDialog;

    private static final String STATE_EDITING_INDEX =
            "admin_editing_card_index";

    private int editingCardIndex = -1;

    private static final String STATE_INITIAL_FORM =
            "admin_deck_initial_form";

    private String initialFormSnapshot;

    private AlertDialog exitDialog;

    public AdminDeckFormFragment() {
        super(R.layout.fragment_admin_deck_form);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            initialFormSnapshot =
                    savedInstanceState.getString(STATE_INITIAL_FORM);
        }

        if (savedInstanceState != null) {
            editingCardIndex = savedInstanceState.getInt(
                    STATE_EDITING_INDEX,
                    -1
            );
        }

        // Ưu tiên bản đang sửa khi khôi phục màn hình.
        Bundle source = savedInstanceState != null
                ? savedInstanceState
                : getArguments();

        if (source == null) {
            return;
        }

        ArrayList<String> savedWords =
                source.getStringArrayList(STATE_WORDS);

        ArrayList<String> savedMeanings =
                source.getStringArrayList(STATE_MEANINGS);

        ArrayList<String> savedExamples =
                source.getStringArrayList(STATE_EXAMPLES);

        if (savedWords == null
                || savedMeanings == null
                || savedExamples == null
                || savedWords.size() != savedMeanings.size()
                || savedWords.size() != savedExamples.size()) {
            return;
        }

        for (int i = 0; i < savedWords.size(); i++) {
            if (savedWords.get(i) == null
                    || savedMeanings.get(i) == null
                    || savedExamples.get(i) == null) {
                return;
            }
        }

        words.clear();
        meanings.clear();
        examples.clear();

        words.addAll(savedWords);
        meanings.addAll(savedMeanings);
        examples.addAll(savedExamples);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        Spinner levelSpinner =
                view.findViewById(R.id.spAdminDeckLevel);

        levelSpinner.setAdapter(createLevelAdapter());

        EditText meaningInput =
                view.findViewById(R.id.etAdminCardMeaning);

        meaningInput.setOnEditorActionListener((input, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                androidx.core.view.WindowCompat.getInsetsController(
                        requireActivity().getWindow(),
                        input
                ).hide(
                        androidx.core.view.WindowInsetsCompat.Type.ime()
                );

                input.clearFocus();
                return true;
            }

            return false;
        });

        Bundle arguments = getArguments();

        boolean editing = arguments != null
                && arguments.getBoolean(ARG_EDIT_MODE, false);

        TextView title =
                view.findViewById(R.id.tvAdminDeckFormTitle);

        TextView saveButton =
                view.findViewById(R.id.btnSaveAdminDeck);

        title.setText(
                editing ? "Sửa bộ thẻ hệ thống" : "Thêm bộ thẻ hệ thống"
        );

        saveButton.setText(
                editing ? "Lưu thay đổi" : "Lưu bộ thẻ"
        );

        if (savedInstanceState == null) {
            levelSpinner.setSelection(1);

            if (editing) {
                EditText nameInput =
                        view.findViewById(R.id.etAdminDeckName);

                nameInput.setText(
                        arguments.getString(ARG_DECK_NAME, "")
                );

                String selectedLevel =
                        arguments.getString(ARG_DECK_LEVEL, "A2");

                for (int i = 0; i < LEVELS.length; i++) {
                    if (LEVELS[i].equals(selectedLevel)) {
                        levelSpinner.setSelection(i);
                        break;
                    }
                }
            }
        }

        view.findViewById(R.id.btnBackAdminDeckForm)
                .setOnClickListener(button -> goBack());

        view.findViewById(R.id.btnAddAdminCard)
                .setOnClickListener(button -> addCard(view));

        view.findViewById(R.id.btnSaveAdminDeck)
                .setOnClickListener(button -> validateForm(view));
        view.findViewById(R.id.btnCancelAdminCardEdit)
                .setOnClickListener(button -> resetCardEditor(view));

        if (editingCardIndex < 0 || editingCardIndex >= words.size()) {
            editingCardIndex = -1;
        }

        updateCardEditorButtons(view);

        renderCards(view);

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

    private void addCard(@NonNull View view) {
        EditText wordInput =
                view.findViewById(R.id.etAdminCardWord);

        EditText meaningInput =
                view.findViewById(R.id.etAdminCardMeaning);

        EditText exampleInput =
                view.findViewById(R.id.etAdminCardExample);

        String word = wordInput.getText().toString().trim();
        String meaning = meaningInput.getText().toString().trim();
        String example = exampleInput.getText().toString().trim();

        if (word.isEmpty()) {
            wordInput.setError("Bạn hãy nhập từ tiếng Anh.");
            wordInput.requestFocus();
            return;
        }

        if (meaning.isEmpty()) {
            meaningInput.setError("Bạn hãy nhập nghĩa tiếng Việt.");
            meaningInput.requestFocus();
            return;
        }

        String normalizedWord = normalize(word);

        for (int i = 0; i < words.size(); i++) {
            // Cho phép giữ nguyên từ của chính mục đang sửa.
            if (i == editingCardIndex) {
                continue;
            }

            if (normalize(words.get(i)).equals(normalizedWord)) {
                wordInput.setError("Từ này đã có trong bộ thẻ.");
                wordInput.requestFocus();
                return;
            }
        }

        if (editingCardIndex >= 0
                && editingCardIndex < words.size()) {
            words.set(editingCardIndex, word);
            meanings.set(editingCardIndex, meaning);
            examples.set(editingCardIndex, example);
        } else {
            words.add(word);
            meanings.add(meaning);
            examples.add(example);
        }

        resetCardEditor(view);

        view.findViewById(R.id.tvAdminDeckFormStatus)
                .setVisibility(View.GONE);

        renderCards(view);
    }

    private void renderCards(@NonNull View view) {
        LinearLayout container =
                view.findViewById(R.id.adminCardListContainer);

        TextView count =
                view.findViewById(R.id.tvAdminCardCount);

        container.removeAllViews();

        if (words.isEmpty()) {
            count.setText("Chưa có từ nào được thêm.");
            return;
        }

        count.setText("Đã thêm " + words.size() + " từ");

        for (int i = 0; i < words.size(); i++) {
            final int index = i;

            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.bg_card);
            card.setPadding(dp(16), dp(16), dp(16), dp(16));

            LinearLayout.LayoutParams cardParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            cardParams.bottomMargin = dp(10);

            TextView wordText = new TextView(requireContext());
            wordText.setText(words.get(i));
            wordText.setTextSize(16);
            wordText.setTypeface(
                    null,
                    android.graphics.Typeface.BOLD
            );

            wordText.setTextColor(
                    ContextCompat.getColor(
                            requireContext(),
                            R.color.sm_text_primary
                    )
            );

            card.addView(wordText);

            TextView meaningText = new TextView(requireContext());
            meaningText.setText(meanings.get(i));
            meaningText.setTextSize(14);

            meaningText.setTextColor(
                    ContextCompat.getColor(
                            requireContext(),
                            R.color.sm_text_secondary
                    )
            );

            LinearLayout.LayoutParams meaningParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            meaningParams.topMargin = dp(6);
            card.addView(meaningText, meaningParams);

            if (!examples.get(i).isEmpty()) {
                TextView exampleText = new TextView(requireContext());
                exampleText.setText(examples.get(i));
                exampleText.setTextSize(14);

                exampleText.setTextColor(
                        ContextCompat.getColor(
                                requireContext(),
                                R.color.sm_text_hint
                        )
                );

                LinearLayout.LayoutParams exampleParams =
                        new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                        );

                exampleParams.topMargin = dp(6);
                card.addView(exampleText, exampleParams);
            }

            // Hàng chứa hai nút Sửa và Xóa.
            LinearLayout actionRow = new LinearLayout(requireContext());
            actionRow.setOrientation(LinearLayout.HORIZONTAL);
            actionRow.setGravity(Gravity.CENTER_VERTICAL);
            actionRow.setBaselineAligned(false);

            LinearLayout.LayoutParams actionRowParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            actionRowParams.topMargin = dp(12);

// Nút sửa.
            AppCompatButton editButton =
                    new AppCompatButton(requireContext());

            editButton.setText("Sửa từ");
            editButton.setAllCaps(false);
            editButton.setTextSize(14);
            editButton.setGravity(Gravity.CENTER);
            editButton.setIncludeFontPadding(false);
            editButton.setMinWidth(0);
            editButton.setMinimumWidth(0);
            editButton.setMinHeight(0);
            editButton.setMinimumHeight(0);
            editButton.setPadding(dp(8), 0, dp(8), 0);
            editButton.setBackgroundResource(R.drawable.bg_card);
            editButton.setBackgroundTintList(null);

            editButton.setTextColor(
                    ContextCompat.getColor(
                            requireContext(),
                            R.color.sm_cyan
                    )
            );

            editButton.setOnClickListener(
                    button -> beginEditCard(index)
            );

            LinearLayout.LayoutParams editParams =
                    new LinearLayout.LayoutParams(
                            0,
                            dp(48),
                            1f
                    );

            editParams.setMarginEnd(dp(4));

            actionRow.addView(editButton, editParams);

// Nút xóa.
            AppCompatButton deleteButton =
                    new AppCompatButton(requireContext());

            deleteButton.setText("Xóa từ");
            deleteButton.setAllCaps(false);
            deleteButton.setTextSize(14);
            deleteButton.setGravity(Gravity.CENTER);
            deleteButton.setIncludeFontPadding(false);
            deleteButton.setMinWidth(0);
            deleteButton.setMinimumWidth(0);
            deleteButton.setMinHeight(0);
            deleteButton.setMinimumHeight(0);
            deleteButton.setPadding(dp(8), 0, dp(8), 0);
            deleteButton.setBackgroundResource(R.drawable.bg_card);
            deleteButton.setBackgroundTintList(null);

            deleteButton.setTextColor(
                    ContextCompat.getColor(
                            requireContext(),
                            R.color.sm_error
                    )
            );

            deleteButton.setOnClickListener(
                    button -> confirmDelete(index)
            );

            LinearLayout.LayoutParams deleteParams =
                    new LinearLayout.LayoutParams(
                            0,
                            dp(48),
                            1f
                    );

            deleteParams.setMarginStart(dp(4));

            actionRow.addView(deleteButton, deleteParams);

// Thêm cả hàng nút vào thẻ.
            card.addView(actionRow, actionRowParams);

            container.addView(card, cardParams);
        }
    }

    private void beginEditCard(int index) {
        View view = getView();

        if (view == null
                || !isResumed()
                || index < 0
                || index >= words.size()) {
            return;
        }

        editingCardIndex = index;

        EditText wordInput =
                view.findViewById(R.id.etAdminCardWord);

        EditText meaningInput =
                view.findViewById(R.id.etAdminCardMeaning);

        EditText exampleInput =
                view.findViewById(R.id.etAdminCardExample);

        wordInput.setText(words.get(index));
        meaningInput.setText(meanings.get(index));
        exampleInput.setText(examples.get(index));

        wordInput.setError(null);
        meaningInput.setError(null);

        updateCardEditorButtons(view);

        wordInput.requestFocus();
        wordInput.setSelection(wordInput.length());

        // Đưa ô nhập đang sửa vào vùng nhìn thấy.
        wordInput.post(() -> {
            if (getView() == view) {
                wordInput.requestRectangleOnScreen(
                        new android.graphics.Rect(
                                0,
                                0,
                                wordInput.getWidth(),
                                wordInput.getHeight()
                        ),
                        false
                );
            }
        });
    }

    private void resetCardEditor(@NonNull View view) {
        editingCardIndex = -1;

        EditText wordInput =
                view.findViewById(R.id.etAdminCardWord);

        EditText meaningInput =
                view.findViewById(R.id.etAdminCardMeaning);

        EditText exampleInput =
                view.findViewById(R.id.etAdminCardExample);

        wordInput.setText("");
        meaningInput.setText("");
        exampleInput.setText("");

        wordInput.setError(null);
        meaningInput.setError(null);

        updateCardEditorButtons(view);

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
    }

    private void updateCardEditorButtons(@NonNull View view) {
        TextView addButton =
                view.findViewById(R.id.btnAddAdminCard);

        boolean editing = editingCardIndex >= 0;

        addButton.setText(
                editing ? "Cập nhật từ" : "+ Thêm từ"
        );

        view.findViewById(R.id.btnCancelAdminCardEdit)
                .setVisibility(
                        editing ? View.VISIBLE : View.GONE
                );
    }

    private void confirmDelete(int index) {
        if (!isAdded()
                || !isResumed()
                || deleteDialog != null
                || index < 0
                || index >= words.size()) {
            return;
        }

        AlertDialog dialog =
                new AlertDialog.Builder(requireContext())
                        .setTitle("Xóa từ?")
                        .setMessage(
                                "Bạn có muốn xóa “"
                                        + words.get(index)
                                        + "” khỏi bộ thẻ đang tạo?"
                        )
                        .setNegativeButton("Hủy", null)
                        .setPositiveButton("Xóa", (ignored, which) -> {
                            if (index >= 0 && index < words.size()) {
                                words.remove(index);
                                meanings.remove(index);
                                examples.remove(index);

                                View view = getView();

                                if (editingCardIndex == index) {
                                    if (view != null) {
                                        resetCardEditor(view);
                                    } else {
                                        editingCardIndex = -1;
                                    }
                                } else if (editingCardIndex > index) {
                                    editingCardIndex--;
                                }

                                if (view != null) {
                                    renderCards(view);
                                }
                            }
                        })
                        .create();

        deleteDialog = dialog;

        dialog.setOnDismissListener(ignored -> {
            if (deleteDialog == dialog) {
                deleteDialog = null;
            }
        });

        dialog.show();
    }

    private void validateForm(@NonNull View view) {
        EditText nameInput =
                view.findViewById(R.id.etAdminDeckName);

        TextView status =
                view.findViewById(R.id.tvAdminDeckFormStatus);

        status.setVisibility(View.GONE);

        if (editingCardIndex >= 0) {
            status.setText(
                    "Bạn hãy bấm Cập nhật từ hoặc Hủy chỉnh sửa "
                            + "trước khi lưu bộ thẻ."
            );
            status.setVisibility(View.VISIBLE);
            return;
        }

        if (nameInput.getText().toString().trim().isEmpty()) {
            nameInput.setError("Bạn hãy nhập tên bộ thẻ.");
            nameInput.requestFocus();
            return;
        }

        nameInput.setError(null);

        if (words.isEmpty()) {
            status.setText("Bạn hãy thêm ít nhất một từ.");
            status.setVisibility(View.VISIBLE);
            return;
        }

        status.setText(
                "Chưa thể lưu: chức năng tạo bộ thẻ hệ thống "
                        + "chưa kết nối máy chủ."
        );

        status.setVisibility(View.VISIBLE);
    }

    private String normalize(@NonNull String value) {
        return value.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
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
                || deleteDialog != null
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

        AlertDialog dialog =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                        requireContext()
                )
                        .setTitle("Bỏ thay đổi chưa lưu?")
                        .setMessage(
                                "Nội dung bạn vừa nhập hoặc chỉnh sửa "
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
                view.findViewById(R.id.etAdminDeckName);

        Spinner levelSpinner =
                view.findViewById(R.id.spAdminDeckLevel);

        EditText wordInput =
                view.findViewById(R.id.etAdminCardWord);

        EditText meaningInput =
                view.findViewById(R.id.etAdminCardMeaning);

        EditText exampleInput =
                view.findViewById(R.id.etAdminCardExample);

        appendSnapshotValue(
                result,
                nameInput.getText().toString()
        );

        appendSnapshotValue(
                result,
                String.valueOf(levelSpinner.getSelectedItemPosition())
        );

        // Bao gồm cả từ đang nhập nhưng chưa bấm Thêm/Cập nhật.
        appendSnapshotValue(
                result,
                wordInput.getText().toString()
        );

        appendSnapshotValue(
                result,
                meaningInput.getText().toString()
        );

        appendSnapshotValue(
                result,
                exampleInput.getText().toString()
        );

        appendSnapshotValue(
                result,
                String.valueOf(words.size())
        );

        for (int i = 0; i < words.size(); i++) {
            appendSnapshotValue(result, words.get(i));
            appendSnapshotValue(result, meanings.get(i));
            appendSnapshotValue(result, examples.get(i));
        }

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
        outState.putStringArrayList(
                STATE_WORDS,
                new ArrayList<>(words)
        );

        outState.putStringArrayList(
                STATE_MEANINGS,
                new ArrayList<>(meanings)
        );

        outState.putStringArrayList(
                STATE_EXAMPLES,
                new ArrayList<>(examples)
        );

        outState.putInt(
                STATE_EDITING_INDEX,
                editingCardIndex
        );

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
        if (deleteDialog != null) {
            deleteDialog.setOnDismissListener(null);
            deleteDialog.dismiss();
            deleteDialog = null;
        }

        super.onDestroyView();
    }
}