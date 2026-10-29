package com.example.speakmate;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Locale;

public class CreateDeckFragment extends Fragment {

    private static final String[] LEVELS = {
            "A1", "A2", "B1", "B2", "C1", "C2"
    };

    private final ArrayList<Flashcard> draftCards =
            new ArrayList<>();

    private EditText nameInput;
    private Spinner levelInput;
    private LinearLayout cardContainer;
    private TextView countText;
    private AppCompatButton saveButton;

    private AlertDialog cardDialog;

    private String draftName = "";
    private int draftLevel;
    private boolean saving;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            return;
        }

        draftName = savedInstanceState.getString("name", "");
        draftLevel = savedInstanceState.getInt("level", 0);

        ArrayList<String> words =
                savedInstanceState.getStringArrayList("words");

        ArrayList<String> meanings =
                savedInstanceState.getStringArrayList("meanings");

        ArrayList<String> examples =
                savedInstanceState.getStringArrayList("examples");

        if (words == null || meanings == null || examples == null) {
            return;
        }

        int size = Math.min(
                words.size(),
                Math.min(meanings.size(), examples.size())
        );

        for (int i = 0; i < size; i++) {
            draftCards.add(new Flashcard(
                    words.get(i),
                    meanings.get(i),
                    examples.get(i)
            ));
        }
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        ScrollView scroll = new ScrollView(requireContext());

        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setBackgroundColor(color(R.color.sm_background));

        LinearLayout content = new LinearLayout(requireContext());

        content.setOrientation(LinearLayout.VERTICAL);
        content.setFocusableInTouchMode(true);

        content.setPadding(
                dp(24),
                dp(20),
                dp(24),
                dp(24)
        );

        scroll.addView(
                content,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        // Thanh tiêu đề.
        LinearLayout header = new LinearLayout(requireContext());

        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        AppCompatButton back = button("‹", false);

        back.setTextSize(28);
        back.setContentDescription("Quay lại thư viện bộ thẻ");
        back.setMinHeight(0);
        back.setMinimumHeight(0);
        back.setPadding(0, 0, 0, 0);

        header.addView(
                back,
                new LinearLayout.LayoutParams(
                        dp(48),
                        dp(48)
                )
        );

        TextView heading = text("Tạo bộ thẻ", 22);

        heading.setTypeface(
                Typeface.create("sans-serif", Typeface.BOLD)
        );

        LinearLayout.LayoutParams headingParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        headingParams.setMarginStart(dp(14));

        header.addView(heading, headingParams);

        content.addView(
                header,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        back.setOnClickListener(v -> {
            if (!isAdded()
                    || getParentFragmentManager().isStateSaved()) {
                return;
            }

            androidx.core.view.WindowCompat.getInsetsController(
                    requireActivity().getWindow(),
                    scroll
            ).hide(
                    androidx.core.view.WindowInsetsCompat.Type.ime()
            );

            View focused = requireActivity().getCurrentFocus();

            if (focused != null) {
                focused.clearFocus();
            }

            getParentFragmentManager().popBackStackImmediate();
        });

        TextView description = text(
                "Tạo bộ từ vựng của riêng bạn để học và ôn tập.",
                14
        );

        description.setTextColor(
                color(R.color.sm_text_secondary)
        );

        LinearLayout.LayoutParams descriptionParams = rowParams();
        descriptionParams.topMargin = dp(16);

        content.addView(description, descriptionParams);

        // Tên bộ thẻ.
        TextView nameLabel = text("Tên bộ thẻ", 15);

        nameLabel.setTypeface(
                Typeface.create("sans-serif-medium", Typeface.NORMAL)
        );

        LinearLayout.LayoutParams nameLabelParams = rowParams();
        nameLabelParams.topMargin = dp(24);

        content.addView(nameLabel, nameLabelParams);

        nameInput = input("Ví dụ: Từ vựng du lịch", false);
        nameInput.setText(draftName);

        content.addView(nameInput, rowParams());

        // Trình độ.
        TextView levelLabel = text("Trình độ", 15);

        levelLabel.setTypeface(
                Typeface.create("sans-serif-medium", Typeface.NORMAL)
        );

        LinearLayout.LayoutParams levelLabelParams = rowParams();
        levelLabelParams.topMargin = dp(20);

        content.addView(levelLabel, levelLabelParams);

        levelInput = new Spinner(
                requireContext(),
                Spinner.MODE_DROPDOWN
        );

        levelInput.setBackgroundResource(R.drawable.bg_card);

        levelInput.setPopupBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(
                        color(R.color.sm_surface)
                )
        );

        levelInput.setPadding(
                dp(4),
                0,
                dp(4),
                0
        );

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
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

                styleItem(item, false);
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

                styleItem(item, true);
                return item;
            }

            private void styleItem(View item, boolean dropdown) {
                if (!(item instanceof TextView)) {
                    return;
                }

                TextView label = (TextView) item;

                label.setTextColor(color(R.color.sm_text_primary));
                label.setTextSize(16);

                label.setTypeface(
                        Typeface.create(
                                "sans-serif-medium",
                                Typeface.NORMAL
                        )
                );

                label.setGravity(
                        android.view.Gravity.CENTER_VERTICAL
                );

                label.setMinHeight(dp(48));

                label.setPadding(
                        dp(14),
                        dp(10),
                        dp(14),
                        dp(10)
                );

                if (dropdown) {
                    label.setBackgroundColor(
                            color(R.color.sm_surface)
                    );
                }
            }
        };

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        levelInput.setAdapter(adapter);

        levelInput.setSelection(
                Math.max(
                        0,
                        Math.min(draftLevel, LEVELS.length - 1)
                )
        );

        LinearLayout.LayoutParams levelParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(56)
                );

        levelParams.topMargin = dp(10);

        content.addView(levelInput, levelParams);

        // Danh sách từ.
        countText = text("", 16);

        LinearLayout.LayoutParams countParams = rowParams();
        countParams.topMargin = dp(24);

        content.addView(countText, countParams);

        AppCompatButton addButton = button("+ Thêm từ", false);

        content.addView(addButton, rowParams());

        addButton.setOnClickListener(
                v -> showCardEditor(-1)
        );

        cardContainer = new LinearLayout(requireContext());
        cardContainer.setOrientation(LinearLayout.VERTICAL);

        content.addView(cardContainer, rowParams());

        // Giữ nguyên xử lý lưu bộ thẻ.
        saveButton = button("Lưu bộ thẻ", true);

        LinearLayout.LayoutParams saveParams = rowParams();
        saveParams.topMargin = dp(20);

        content.addView(saveButton, saveParams);

        saveButton.setOnClickListener(v -> saveDeck());

        saving = false;
        renderCards();

        return scroll;
    }

    private void showCardEditor(int index) {
        if (saving || cardDialog != null) {
            return;
        }

        boolean editing = index >= 0;

        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(8));

        EditText word = input("Từ tiếng Anh", false);
        EditText meaning = input("Nghĩa tiếng Việt", false);
        EditText example = input("Câu ví dụ (không bắt buộc)", true);

        form.addView(word, rowParams());
        form.addView(meaning, rowParams());
        form.addView(example, rowParams());

        if (editing) {
            Flashcard card = draftCards.get(index);

            word.setText(card.getWord());
            meaning.setText(card.getMeaning());
            example.setText(card.getExample());
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(editing ? "Sửa từ vựng" : "Thêm từ vựng")
                .setView(form)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu", null)
                .create();

        cardDialog = dialog;

        dialog.setOnDismissListener(ignored -> {
            if (cardDialog == dialog) {
                cardDialog = null;
            }
        });

        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            String wordValue =
                                    word.getText().toString().trim();

                            String meaningValue =
                                    meaning.getText().toString().trim();

                            String exampleValue =
                                    example.getText().toString().trim();

                            if (wordValue.isEmpty()) {
                                word.setError("Vui lòng nhập từ tiếng Anh.");
                                word.requestFocus();
                                return;
                            }

                            if (meaningValue.isEmpty()) {
                                meaning.setError("Vui lòng nhập nghĩa.");
                                meaning.requestFocus();
                                return;
                            }

                            for (int i = 0; i < draftCards.size(); i++) {
                                if (i != index
                                        && normalize(draftCards.get(i).getWord())
                                        .equals(normalize(wordValue))) {
                                    word.setError(
                                            "Từ này đã có trong bộ thẻ."
                                    );
                                    word.requestFocus();
                                    return;
                                }
                            }

                            Flashcard card = new Flashcard(
                                    wordValue,
                                    meaningValue,
                                    exampleValue
                            );

                            if (editing) {
                                draftCards.set(index, card);
                            } else {
                                draftCards.add(card);
                            }

                            renderCards();
                            dialog.dismiss();
                        })
        );

        dialog.show();
    }

    private void renderCards() {
        if (cardContainer == null || countText == null) {
            return;
        }

        countText.setText(
                draftCards.size() + " từ trong bộ thẻ"
        );

        countText.setTextColor(color(R.color.sm_cyan));

        countText.setTypeface(
                Typeface.create("sans-serif-medium", Typeface.NORMAL)
        );

        cardContainer.removeAllViews();

        for (int i = 0; i < draftCards.size(); i++) {
            final int index = i;
            Flashcard card = draftCards.get(i);

            LinearLayout item = new LinearLayout(requireContext());

            item.setOrientation(LinearLayout.VERTICAL);

            item.setPadding(
                    dp(16),
                    dp(16),
                    dp(16),
                    dp(16)
            );

            item.setBackgroundResource(R.drawable.bg_card);

            LinearLayout heading =
                    new LinearLayout(requireContext());

            heading.setOrientation(LinearLayout.HORIZONTAL);
            heading.setGravity(android.view.Gravity.CENTER_VERTICAL);

            android.widget.ImageView icon =
                    new android.widget.ImageView(requireContext());

            icon.setImageResource(R.drawable.ic_sm_stat_words);

            icon.setScaleType(
                    android.widget.ImageView.ScaleType.FIT_CENTER
            );

            icon.setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO
            );

            LinearLayout.LayoutParams iconParams =
                    new LinearLayout.LayoutParams(
                            dp(36),
                            dp(36)
                    );

            iconParams.setMarginEnd(dp(12));

            heading.addView(icon, iconParams);

            TextView word = text(
                    (i + 1) + ". " + card.getWord(),
                    18
            );

            word.setTypeface(
                    Typeface.create("sans-serif", Typeface.BOLD)
            );

            heading.addView(
                    word,
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                    )
            );

            item.addView(
                    heading,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );

            TextView meaning = text(card.getMeaning(), 16);
            meaning.setTextColor(color(R.color.sm_cyan));

            item.addView(meaning, rowParams());

            if (card.getExample() != null
                    && !card.getExample().trim().isEmpty()) {
                TextView example = text(
                        card.getExample(),
                        14
                );

                example.setTextColor(
                        color(R.color.sm_text_secondary)
                );

                example.setTypeface(
                        Typeface.create(
                                "sans-serif",
                                Typeface.ITALIC
                        )
                );

                item.addView(example, rowParams());
            }

            LinearLayout actions =
                    new LinearLayout(requireContext());

            actions.setOrientation(LinearLayout.HORIZONTAL);

            AppCompatButton edit = button("Sửa từ", false);
            AppCompatButton delete = button("Xóa từ", false);

            delete.setTextColor(color(R.color.sm_error));

            LinearLayout.LayoutParams editParams =
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                    );

            editParams.setMarginEnd(dp(4));

            LinearLayout.LayoutParams deleteParams =
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                    );

            deleteParams.setMarginStart(dp(4));

            actions.addView(edit, editParams);
            actions.addView(delete, deleteParams);

            edit.setOnClickListener(
                    v -> showCardEditor(index)
            );

            delete.setOnClickListener(v -> {
                draftCards.remove(index);
                renderCards();
            });

            LinearLayout.LayoutParams actionParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            actionParams.topMargin = dp(14);

            item.addView(actions, actionParams);

            LinearLayout.LayoutParams itemParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            itemParams.topMargin = dp(12);

            cardContainer.addView(item, itemParams);
        }
    }
    private void saveDeck() {
        if (saving
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        String name = nameInput.getText().toString().trim();

        if (name.isEmpty()) {
            nameInput.setError("Vui lòng nhập tên bộ thẻ.");
            nameInput.requestFocus();
            return;
        }

        if (draftCards.isEmpty()) {
            Toast.makeText(
                    requireContext(),
                    "Hãy thêm ít nhất một từ.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        saving = true;
        saveButton.setEnabled(false);

        String level = LEVELS[levelInput.getSelectedItemPosition()];

        try {
            new LocalDeckStore(requireContext()).createDeck(
                    name,
                    level,
                    draftCards
            );
        } catch (IllegalStateException | IllegalArgumentException exception) {
            saving = false;
            saveButton.setEnabled(true);

            Toast.makeText(
                    requireContext(),
                    exception.getMessage(),
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        Toast.makeText(
                requireContext(),
                "Đã lưu bộ thẻ.",
                Toast.LENGTH_SHORT
        ).show();

        getParentFragmentManager().popBackStackImmediate();
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private AppCompatEditText input(String hint, boolean multiline) {
        AppCompatEditText input =
                new AppCompatEditText(requireContext());

        input.setHint(hint);
        input.setTextSize(15);

        input.setTypeface(
                Typeface.create("sans-serif", Typeface.NORMAL)
        );

        input.setTextColor(color(R.color.sm_text_primary));
        input.setHintTextColor(color(R.color.sm_text_hint));

        input.setBackgroundResource(
                R.drawable.bg_home_button_secondary
        );

        input.setBackgroundTintList(null);
        input.setMinHeight(dp(56));

        input.setPadding(
                dp(14),
                dp(14),
                dp(14),
                dp(14)
        );

        if (multiline) {
            input.setInputType(
                    android.text.InputType.TYPE_CLASS_TEXT
                            | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                            | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            );

            input.setMinLines(2);
            input.setMaxLines(4);
            input.setGravity(
                    android.view.Gravity.TOP
                            | android.view.Gravity.START
            );
        } else {
            input.setSingleLine(true);

            input.setInputType(
                    android.text.InputType.TYPE_CLASS_TEXT
                            | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            );
        }

        input.setImeOptions(
                android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        );

        input.setOnEditorActionListener((field, actionId, event) -> {
            if (actionId
                    != android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                return false;
            }

            android.view.inputmethod.InputMethodManager keyboard =
                    (android.view.inputmethod.InputMethodManager)
                            requireContext().getSystemService(
                                    android.content.Context.INPUT_METHOD_SERVICE
                            );

            if (keyboard != null) {
                keyboard.hideSoftInputFromWindow(
                        field.getWindowToken(),
                        0
                );
            }

            field.clearFocus();
            return true;
        });

        // Dữ liệu nháp vẫn do Fragment quản lý.
        input.setSaveEnabled(false);

        return input;
    }

    private AppCompatButton button(String label, boolean primary) {
        AppCompatButton button =
                new AppCompatButton(requireContext());

        button.setText(label);
        button.setTextSize(15);
        button.setAllCaps(false);

        button.setTypeface(
                Typeface.create("sans-serif-medium", Typeface.NORMAL)
        );

        button.setGravity(android.view.Gravity.CENTER);
        button.setIncludeFontPadding(false);

        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(52));

        button.setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(10)
        );

        button.setElevation(0f);
        button.setStateListAnimator(null);

        button.setBackgroundResource(
                primary
                        ? R.drawable.bg_card
                        : R.drawable.bg_home_button_secondary
        );

        button.setBackgroundTintList(
                primary
                        ? android.content.res.ColorStateList.valueOf(
                        color(R.color.sm_cyan)
                )
                        : null
        );

        button.setTextColor(
                color(
                        primary
                                ? R.color.sm_background
                                : R.color.sm_cyan
                )
        );

        return button;
    }

    private TextView text(String value, int size) {
        TextView text = new TextView(requireContext());

        text.setText(value);
        text.setTextSize(size);

        text.setTypeface(
                Typeface.create("sans-serif", Typeface.NORMAL)
        );

        text.setTextColor(color(R.color.sm_text_primary));

        text.setLineSpacing(
                dp(3),
                1f
        );

        return text;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        params.topMargin = dp(10);
        return params;
    }

    private int color(int resource) {
        return ContextCompat.getColor(requireContext(), resource);
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        captureDraft();

        outState.putString("name", draftName);
        outState.putInt("level", draftLevel);

        ArrayList<String> words = new ArrayList<>();
        ArrayList<String> meanings = new ArrayList<>();
        ArrayList<String> examples = new ArrayList<>();

        for (Flashcard card : draftCards) {
            words.add(card.getWord());
            meanings.add(card.getMeaning());
            examples.add(card.getExample());
        }

        outState.putStringArrayList("words", words);
        outState.putStringArrayList("meanings", meanings);
        outState.putStringArrayList("examples", examples);
    }

    private void captureDraft() {
        if (nameInput != null) {
            draftName = nameInput.getText().toString();
        }

        if (levelInput != null) {
            draftLevel = levelInput.getSelectedItemPosition();
        }
    }

    @Override
    public void onDestroyView() {
        captureDraft();

        if (cardDialog != null) {
            cardDialog.dismiss();
            cardDialog = null;
        }

        nameInput = null;
        levelInput = null;
        cardContainer = null;
        countText = null;
        saveButton = null;

        super.onDestroyView();
    }
}