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
        scroll.setBackgroundColor(color(R.color.sm_background));

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(20), dp(24), dp(24));

        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        AppCompatButton back = button("‹ Quay lại", false);
        content.addView(back, rowParams());

        back.setOnClickListener(v -> {
            if (!getParentFragmentManager().isStateSaved()) {
                getParentFragmentManager().popBackStackImmediate();
            }
        });

        TextView heading = text("Tạo bộ thẻ", 26);
        heading.setTypeface(null, Typeface.BOLD);
        content.addView(heading, rowParams());

        TextView description = text(
                "Thêm từ vựng của bạn và học bằng flashcard.",
                14
        );
        description.setTextColor(color(R.color.sm_text_secondary));
        content.addView(description, rowParams());

        nameInput = input("Tên bộ thẻ", false);
        nameInput.setText(draftName);
        content.addView(nameInput, rowParams());

        TextView levelLabel = text("Trình độ", 15);
        content.addView(levelLabel, rowParams());

        levelInput = new Spinner(requireContext());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                LEVELS
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        levelInput.setAdapter(adapter);
        levelInput.setSelection(Math.max(
                0,
                Math.min(draftLevel, LEVELS.length - 1)
        ));

        content.addView(levelInput, rowParams());

        countText = text("", 16);
        content.addView(countText, rowParams());

        AppCompatButton addButton = button("+ Thêm từ", false);
        content.addView(addButton, rowParams());

        addButton.setOnClickListener(v -> showCardEditor(-1));

        cardContainer = new LinearLayout(requireContext());
        cardContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(cardContainer, rowParams());

        saveButton = button("Lưu bộ thẻ", true);
        content.addView(saveButton, rowParams());

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
        if (cardContainer == null) {
            return;
        }

        countText.setText(draftCards.size() + " từ trong bộ thẻ");
        cardContainer.removeAllViews();

        for (int i = 0; i < draftCards.size(); i++) {
            final int index = i;
            Flashcard card = draftCards.get(i);

            LinearLayout item = new LinearLayout(requireContext());
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(16), dp(12), dp(16), dp(12));
            item.setBackgroundResource(R.drawable.bg_card);

            TextView word = text(
                    (i + 1) + ". " + card.getWord(),
                    18
            );
            word.setTypeface(null, Typeface.BOLD);
            item.addView(word, rowParams());

            TextView meaning = text(card.getMeaning(), 16);
            meaning.setTextColor(color(R.color.sm_cyan));
            item.addView(meaning, rowParams());

            if (card.getExample() != null
                    && !card.getExample().trim().isEmpty()) {
                TextView example = text(card.getExample(), 14);
                example.setTextColor(
                        color(R.color.sm_text_secondary)
                );
                item.addView(example, rowParams());
            }

            LinearLayout actions =
                    new LinearLayout(requireContext());

            actions.setOrientation(LinearLayout.HORIZONTAL);

            AppCompatButton edit = button("Sửa", false);
            AppCompatButton delete = button("Xóa", false);

            actions.addView(edit, new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            actions.addView(delete, new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            edit.setOnClickListener(v -> showCardEditor(index));

            delete.setOnClickListener(v -> {
                draftCards.remove(index);
                renderCards();
            });

            item.addView(actions, rowParams());
            cardContainer.addView(item, rowParams());
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
        input.setTextSize(16);
        input.setTextColor(color(R.color.sm_text_primary));
        input.setHintTextColor(color(R.color.sm_text_hint));
        input.setBackgroundResource(R.drawable.bg_card);
        input.setSupportBackgroundTintList(null);
        input.setPadding(dp(14), dp(14), dp(14), dp(14));

        if (multiline) {
            input.setInputType(
                    android.text.InputType.TYPE_CLASS_TEXT
                            | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            );
            input.setMinLines(2);
        } else {
            input.setSingleLine(true);
        }

        // Không tự lưu theo ID vì các ô được tạo động.
        input.setSaveEnabled(false);

        return input;
    }

    private AppCompatButton button(String label, boolean primary) {
        AppCompatButton button =
                new AppCompatButton(requireContext());

        button.setText(label);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));

        button.setBackgroundResource(
                primary
                        ? R.drawable.bg_button_primary
                        : R.drawable.bg_card
        );

        button.setSupportBackgroundTintList(null);

        button.setTextColor(color(
                primary ? R.color.sm_background : R.color.sm_cyan
        ));

        return button;
    }

    private TextView text(String value, int size) {
        TextView text = new TextView(requireContext());
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color(R.color.sm_text_primary));
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