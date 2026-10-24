package com.example.speakmate;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import java.util.List;

public class DeckDetailFragment extends Fragment {

    private static final String ARG_DECK_ID = "deckId";

    private static final String[] LEVELS = {
            "A1", "A2", "B1", "B2", "C1", "C2"
    };

    private boolean openingStudy;
    private AlertDialog activeDialog;

    public DeckDetailFragment() {
        super(R.layout.fragment_deck_detail);
    }

    public static DeckDetailFragment newInstance(String deckId) {
        DeckDetailFragment fragment = new DeckDetailFragment();

        Bundle arguments = new Bundle();
        arguments.putString(ARG_DECK_ID, deckId);

        fragment.setArguments(arguments);

        return fragment;
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        openingStudy = false;

        view.findViewById(R.id.btnBackDeck)
                .setOnClickListener(v -> closeDetail());

        view.findViewById(R.id.btnStudyDeck)
                .setOnClickListener(v -> openStudy());
    }

    @Override
    public void onResume() {
        super.onResume();

        openingStudy = false;
        refreshDeck();
    }

    private String getDeckId() {
        Bundle arguments = getArguments();

        if (arguments == null) {
            return "";
        }

        String id = arguments.getString(ARG_DECK_ID);
        return id == null ? "" : id;
    }

    private boolean canAct() {
        return isAdded()
                && getView() != null
                && !openingStudy
                && !getParentFragmentManager().isStateSaved();
    }

    private boolean canEdit() {
        return canAct()
                && getDeckId().startsWith("local_")
                && activeDialog == null;
    }

    private DemoFlashcardRepository repository() {
        return new DemoFlashcardRepository(requireContext());
    }

    private void closeDetail() {
        if (!isAdded()
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        getParentFragmentManager().popBackStackImmediate();
    }

    private void refreshDeck() {
        View view = getView();

        if (view == null) {
            return;
        }

        try {
            renderDeck(view);
        } catch (IllegalStateException exception) {
            view.findViewById(R.id.btnStudyDeck)
                    .setEnabled(false);

            showMessage(exception.getMessage());
        }
    }

    private void renderDeck(View view) {
        DemoFlashcardRepository repository = repository();

        String deckId = getDeckId();

        FlashcardDeck deck =
                repository.getDeckById(deckId);

        TextView title =
                view.findViewById(R.id.tvDetailDeckName);

        TextView info =
                view.findViewById(R.id.tvDetailDeckInfo);

        LinearLayout container =
                view.findViewById(R.id.cardListContainer);

        View studyButton =
                view.findViewById(R.id.btnStudyDeck);

        container.removeAllViews();

        if (deck == null) {
            title.setText("Không tìm thấy bộ thẻ");
            info.setText("Vui lòng quay lại thư viện.");
            studyButton.setEnabled(false);
            return;
        }

        List<Flashcard> cards =
                repository.getCards(deckId);

        boolean editable = deckId.startsWith("local_");

        title.setText(deck.getName());

        info.setText(
                "Trình độ: " + deck.getLevel()
                        + "\nSố từ: " + cards.size()
                        + "\nĐã nhớ: " + deck.getLearnedCards()
                        + "\nNguồn: "
                        + (editable
                        ? "Tự tạo trên thiết bị"
                        : "Dữ liệu demo")
        );

        studyButton.setEnabled(
                !openingStudy && !cards.isEmpty()
        );

        if (editable) {
            AppCompatButton editDeck =
                    button("Sửa tên / trình độ");

            AppCompatButton addCard =
                    button("+ Thêm từ");

            AppCompatButton deleteDeck =
                    button("Xóa bộ thẻ");

            container.addView(editDeck, rowParams());
            container.addView(addCard, rowParams());
            container.addView(deleteDeck, rowParams());

            editDeck.setOnClickListener(v -> editDeckInfo());
            addCard.setOnClickListener(v -> editCard(-1));
            deleteDeck.setOnClickListener(v -> confirmDeleteDeck());
        }

        for (int i = 0; i < cards.size(); i++) {
            final int index = i;
            Flashcard card = cards.get(i);

            View item = getLayoutInflater().inflate(
                    R.layout.item_vocabulary,
                    container,
                    false
            );

            TextView word =
                    item.findViewById(R.id.tvVocabularyWord);

            TextView meaning =
                    item.findViewById(R.id.tvVocabularyMeaning);

            TextView example =
                    item.findViewById(R.id.tvVocabularyExample);

            word.setText(card.getWord());
            meaning.setText(card.getMeaning());
            example.setText(card.getExample());

            boolean hasExample = card.getExample() != null
                    && !card.getExample().trim().isEmpty();

            example.setVisibility(
                    hasExample ? View.VISIBLE : View.GONE
            );

            container.addView(item);

            if (editable) {
                LinearLayout actions =
                        new LinearLayout(requireContext());

                actions.setOrientation(LinearLayout.HORIZONTAL);

                AppCompatButton edit = button("Sửa từ");
                AppCompatButton delete = button("Xóa từ");

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

                edit.setOnClickListener(v -> editCard(index));
                delete.setOnClickListener(v -> confirmDeleteCard(index));

                container.addView(actions, rowParams());
            }
        }
    }

    private void editDeckInfo() {
        if (!canEdit()) {
            return;
        }

        try {
            DemoFlashcardRepository repository = repository();
            FlashcardDeck deck = repository.getDeckById(getDeckId());

            if (deck == null) {
                refreshDeck();
                return;
            }

            List<Flashcard> cards =
                    repository.getCards(getDeckId());

            LinearLayout form = form();

            EditText name = input("Tên bộ thẻ", false);
            name.setText(deck.getName());

            Spinner level = new Spinner(requireContext());

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    LEVELS
            );

            adapter.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item
            );

            level.setAdapter(adapter);

            for (int i = 0; i < LEVELS.length; i++) {
                if (LEVELS[i].equals(deck.getLevel())) {
                    level.setSelection(i);
                    break;
                }
            }

            form.addView(name, rowParams());
            form.addView(level, rowParams());

            AlertDialog dialog =
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Sửa thông tin bộ thẻ")
                            .setView(form)
                            .setNegativeButton("Hủy", null)
                            .setPositiveButton("Lưu", null)
                            .create();

            showSaveDialog(dialog, () -> {
                String value = name.getText().toString().trim();

                if (value.isEmpty()) {
                    name.setError("Vui lòng nhập tên bộ thẻ.");
                    name.requestFocus();
                    return;
                }

                if (saveChanges(
                        value,
                        LEVELS[level.getSelectedItemPosition()],
                        cards
                )) {
                    dialog.dismiss();
                }
            });
        } catch (IllegalStateException exception) {
            showMessage(exception.getMessage());
        }
    }

    private void editCard(int index) {
        if (!canEdit()) {
            return;
        }

        try {
            DemoFlashcardRepository repository = repository();
            FlashcardDeck deck = repository.getDeckById(getDeckId());

            if (deck == null) {
                refreshDeck();
                return;
            }

            List<Flashcard> cards = new ArrayList<>(
                    repository.getCards(getDeckId())
            );

            if (index < -1 || index >= cards.size()) {
                return;
            }

            boolean editing = index >= 0;

            LinearLayout form = form();

            EditText word = input("Từ tiếng Anh", false);
            EditText meaning = input("Nghĩa tiếng Việt", false);
            EditText example = input("Câu ví dụ (không bắt buộc)", true);

            if (editing) {
                Flashcard card = cards.get(index);

                word.setText(card.getWord());
                meaning.setText(card.getMeaning());
                example.setText(card.getExample());
            }

            form.addView(word, rowParams());
            form.addView(meaning, rowParams());
            form.addView(example, rowParams());

            AlertDialog dialog =
                    new AlertDialog.Builder(requireContext())
                            .setTitle(
                                    editing ? "Sửa từ vựng" : "Thêm từ vựng"
                            )
                            .setView(form)
                            .setNegativeButton("Hủy", null)
                            .setPositiveButton("Lưu", null)
                            .create();

            showSaveDialog(dialog, () -> {
                String wordValue = word.getText().toString().trim();
                String meaningValue =
                        meaning.getText().toString().trim();

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

                for (int i = 0; i < cards.size(); i++) {
                    if (i != index
                            && cards.get(i).getWord()
                            .equalsIgnoreCase(wordValue)) {
                        word.setError("Từ này đã có trong bộ thẻ.");
                        word.requestFocus();
                        return;
                    }
                }

                List<Flashcard> updated = new ArrayList<>(cards);

                Flashcard card = new Flashcard(
                        wordValue,
                        meaningValue,
                        example.getText().toString().trim()
                );

                if (editing) {
                    updated.set(index, card);
                } else {
                    updated.add(card);
                }

                if (saveChanges(
                        deck.getName(),
                        deck.getLevel(),
                        updated
                )) {
                    dialog.dismiss();
                }
            });
        } catch (IllegalStateException exception) {
            showMessage(exception.getMessage());
        }
    }

    private boolean saveChanges(
            String name,
            String level,
            List<Flashcard> cards
    ) {
        if (!canAct()) {
            return false;
        }

        try {
            new LocalDeckStore(requireContext()).updateDeck(
                    getDeckId(),
                    name,
                    level,
                    cards
            );

            refreshDeck();
            showMessage("Đã lưu thay đổi.");

            return true;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showMessage(exception.getMessage());
            return false;
        }
    }

    private void confirmDeleteCard(int index) {
        if (!canEdit()) {
            return;
        }

        try {
            DemoFlashcardRepository repository = repository();
            FlashcardDeck deck = repository.getDeckById(getDeckId());

            if (deck == null) {
                refreshDeck();
                return;
            }

            List<Flashcard> cards = new ArrayList<>(
                    repository.getCards(getDeckId())
            );

            if (index < 0 || index >= cards.size()) {
                return;
            }

            if (cards.size() <= 1) {
                showMessage(
                        "Bộ thẻ phải còn ít nhất một từ. "
                                + "Bạn có thể xóa cả bộ thẻ."
                );
                return;
            }

            String word = cards.get(index).getWord();

            AlertDialog dialog =
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Xóa từ vựng?")
                            .setMessage("Xóa \"" + word + "\" khỏi bộ thẻ?")
                            .setNegativeButton("Hủy", null)
                            .setPositiveButton("Xóa", null)
                            .create();

            showSaveDialog(dialog, () -> {
                List<Flashcard> updated = new ArrayList<>(cards);
                updated.remove(index);

                if (saveChanges(
                        deck.getName(),
                        deck.getLevel(),
                        updated
                )) {
                    dialog.dismiss();
                }
            });
        } catch (IllegalStateException exception) {
            showMessage(exception.getMessage());
        }
    }

    private void confirmDeleteDeck() {
        if (!canEdit()) {
            return;
        }

        AlertDialog dialog =
                new AlertDialog.Builder(requireContext())
                        .setTitle("Xóa bộ thẻ?")
                        .setMessage(
                                "Toàn bộ từ vựng trong bộ thẻ sẽ bị xóa. "
                                        + "Bạn không thể hoàn tác."
                        )
                        .setNegativeButton("Hủy", null)
                        .setPositiveButton("Xóa bộ thẻ", null)
                        .create();

        showSaveDialog(dialog, () -> {
            if (!canAct()) {
                return;
            }

            try {
                new LocalDeckStore(requireContext())
                        .deleteDeck(getDeckId());

                dialog.dismiss();
                showMessage("Đã xóa bộ thẻ.");

                closeDetail();
            } catch (IllegalArgumentException | IllegalStateException exception) {
                showMessage(exception.getMessage());
            }
        });
    }

    private void showSaveDialog(
            AlertDialog dialog,
            Runnable saveAction
    ) {
        activeDialog = dialog;

        dialog.setOnDismissListener(ignored -> {
            if (activeDialog == dialog) {
                activeDialog = null;
            }
        });

        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> saveAction.run())
        );

        dialog.show();
    }

    private void openStudy() {
        if (!canAct() || activeDialog != null) {
            return;
        }

        try {
            DemoFlashcardRepository repository = repository();
            String deckId = getDeckId();

            if (repository.getDeckById(deckId) == null
                    || repository.getCards(deckId).isEmpty()) {
                refreshDeck();
                return;
            }

            openingStudy = true;

            requireView().findViewById(R.id.btnStudyDeck)
                    .setEnabled(false);

            getParentFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(
                            R.id.fragmentContainer,
                            StudyFragment.newInstance(deckId)
                    )
                    .addToBackStack("study")
                    .commit();
        } catch (IllegalStateException exception) {
            openingStudy = false;
            refreshDeck();
            showMessage(exception.getMessage());
        }
    }

    private LinearLayout form() {
        LinearLayout form = new LinearLayout(requireContext());

        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(12));

        return form;
    }

    private EditText input(String hint, boolean multiline) {
        AppCompatEditText input =
                new AppCompatEditText(requireContext());

        input.setHint(hint);
        input.setTextSize(16);

        input.setTextColor(ContextCompat.getColor(
                requireContext(),
                R.color.sm_text_primary
        ));

        input.setHintTextColor(ContextCompat.getColor(
                requireContext(),
                R.color.sm_text_hint
        ));

        input.setBackgroundResource(R.drawable.bg_card);
        input.setSupportBackgroundTintList(null);
        input.setPadding(dp(14), dp(14), dp(14), dp(14));

        if (multiline) {
            input.setInputType(
                    InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            );
            input.setMinLines(2);
        } else {
            input.setSingleLine(true);
        }

        return input;
    }

    private AppCompatButton button(String label) {
        AppCompatButton button =
                new AppCompatButton(requireContext());

        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setMinHeight(dp(48));

        button.setBackgroundResource(R.drawable.bg_card);
        button.setSupportBackgroundTintList(null);

        button.setTextColor(ContextCompat.getColor(
                requireContext(),
                R.color.sm_cyan
        ));

        return button;
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

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }

    private void showMessage(String message) {
        if (getContext() == null) {
            return;
        }

        Toast.makeText(
                requireContext(),
                message == null ? "Có lỗi xảy ra." : message,
                Toast.LENGTH_LONG
        ).show();
    }

    @Override
    public void onDestroyView() {
        if (activeDialog != null) {
            activeDialog.dismiss();
            activeDialog = null;
        }

        super.onDestroyView();
    }
}