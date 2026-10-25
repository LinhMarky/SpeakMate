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

    private static final String STATE_PREVIEW_INDEX =
            "deck_preview_index";

    private static final String STATE_PREVIEW_FLIPPED =
            "deck_preview_flipped";

    private static final String[] LEVELS = {
            "A1", "A2", "B1", "B2", "C1", "C2"
    };

    private List<Flashcard> previewCards = new ArrayList<>();

    private int previewIndex;
    private boolean previewFlipped;
    private boolean openingMode;

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
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            previewIndex = savedInstanceState.getInt(
                    STATE_PREVIEW_INDEX,
                    0
            );

            previewFlipped = savedInstanceState.getBoolean(
                    STATE_PREVIEW_FLIPPED,
                    false
            );
        }
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        openingMode = false;

        view.findViewById(R.id.btnBackDeck)
                .setOnClickListener(v -> closeDetail());

        view.findViewById(R.id.deckPreviewCard)
                .setOnClickListener(v -> {
                    if (!canAct() || previewCards.isEmpty()) {
                        return;
                    }

                    previewFlipped = !previewFlipped;
                    updatePreview();
                });

        view.findViewById(R.id.btnPreviousPreview)
                .setOnClickListener(v -> changePreview(-1));

        view.findViewById(R.id.btnNextPreview)
                .setOnClickListener(v -> changePreview(1));

        view.findViewById(R.id.btnExpandDeckPreview)
                .setOnClickListener(v -> openMode(0));

        view.findViewById(R.id.btnStudyDeck)
                .setOnClickListener(v -> openMode(0));

        view.findViewById(R.id.btnLearnDeck)
                .setOnClickListener(v -> openMode(1));

        view.findViewById(R.id.btnMatchDeck)
                .setOnClickListener(v -> openMode(2));

        view.findViewById(R.id.btnManageDeck)
                .setOnClickListener(v -> showManageMenu());
    }

    @Override
    public void onResume() {
        super.onResume();

        openingMode = false;
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
                && isResumed()
                && getView() != null
                && !openingMode
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
            setModeButtonsEnabled(false);

            view.findViewById(R.id.btnManageDeck)
                    .setVisibility(View.GONE);

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

        View manageButton =
                view.findViewById(R.id.btnManageDeck);

        container.removeAllViews();

        previewCards = new ArrayList<>();

        if (deck == null) {
            title.setText("Không tìm thấy bộ thẻ");
            info.setText("Vui lòng quay lại thư viện.");

            manageButton.setVisibility(View.GONE);

            setModeButtonsEnabled(false);
            updatePreview();

            return;
        }

        previewCards.addAll(repository.getCards(deckId));

        boolean editable = deckId.startsWith("local_");

        title.setText(deck.getName());

        info.setText(
                previewCards.size() + " từ"
                        + " · " + deck.getLevel()
                        + "\nĐã nhớ: " + deck.getLearnedCards()
                        + "/" + previewCards.size()
                        + "\n"
                        + (editable
                        ? "Tự tạo trên thiết bị"
                        : "Dữ liệu mẫu")
        );

        manageButton.setVisibility(
                editable ? View.VISIBLE : View.GONE
        );

        setModeButtonsEnabled(
                !openingMode && !previewCards.isEmpty()
        );

        updatePreview();

        for (int i = 0; i < previewCards.size(); i++) {
            final int index = i;

            Flashcard card = previewCards.get(i);

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
                addCardActions(container, index);
            }
        }
    }

    private void setModeButtonsEnabled(boolean enabled) {
        View view = getView();

        if (view == null) {
            return;
        }

        view.findViewById(R.id.btnStudyDeck)
                .setEnabled(enabled);

        view.findViewById(R.id.btnLearnDeck)
                .setEnabled(enabled);

        view.findViewById(R.id.btnMatchDeck)
                .setEnabled(enabled);

        view.findViewById(R.id.btnExpandDeckPreview)
                .setEnabled(enabled);
    }

    private void updatePreview() {
        View view = getView();

        if (view == null) {
            return;
        }

        TextView word =
                view.findViewById(R.id.tvPreviewWord);

        TextView side =
                view.findViewById(R.id.tvPreviewSide);

        TextView position =
                view.findViewById(R.id.tvPreviewPosition);

        boolean hasCards = !previewCards.isEmpty();

        view.findViewById(R.id.deckPreviewCard)
                .setEnabled(hasCards);

        view.findViewById(R.id.btnPreviousPreview)
                .setEnabled(hasCards && previewCards.size() > 1);

        view.findViewById(R.id.btnNextPreview)
                .setEnabled(hasCards && previewCards.size() > 1);

        if (!hasCards) {
            previewIndex = 0;
            previewFlipped = false;

            word.setText("Chưa có từ vựng");
            side.setText("XEM TRƯỚC");
            position.setText("0 / 0");

            return;
        }

        previewIndex = Math.max(
                0,
                Math.min(
                        previewIndex,
                        previewCards.size() - 1
                )
        );

        Flashcard card = previewCards.get(previewIndex);

        word.setText(
                previewFlipped
                        ? card.getMeaning()
                        : card.getWord()
        );

        side.setText(
                previewFlipped
                        ? "NGHĨA TIẾNG VIỆT"
                        : "TỪ TIẾNG ANH"
        );

        position.setText(
                (previewIndex + 1)
                        + " / "
                        + previewCards.size()
        );
    }

    private void changePreview(int direction) {
        if (!canAct() || previewCards.isEmpty()) {
            return;
        }

        previewIndex = (
                previewIndex
                        + direction
                        + previewCards.size()
        ) % previewCards.size();

        previewFlipped = false;

        updatePreview();
    }

    /*
     * 0: Thẻ ghi nhớ.
     * 1: Học tự luận.
     * 2: Ghép thẻ.
     */
    private void openMode(int mode) {
        if (!canAct() || activeDialog != null) {
            return;
        }

        try {
            DemoFlashcardRepository repository = repository();

            String deckId = getDeckId();

            if (repository.getDeckById(deckId) == null
                    || repository.getCards(deckId).isEmpty()) {
                refreshDeck();
                showMessage("Bộ thẻ chưa có từ vựng để học.");
                return;
            }

            Fragment destination;
            String backStackName;

            if (mode == 1) {
                destination = LearnFragment.newInstance(deckId);
                backStackName = "learn";
            } else if (mode == 2) {
                destination = MatchFragment.newInstance(deckId);
                backStackName = "match";
            } else {
                destination = StudyFragment.newInstance(deckId);
                backStackName = "study";
            }

            openingMode = true;
            setModeButtonsEnabled(false);

            getParentFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(
                            R.id.fragmentContainer,
                            destination
                    )
                    .addToBackStack(backStackName)
                    .commit();

        } catch (IllegalStateException exception) {
            openingMode = false;
            refreshDeck();

            showMessage(exception.getMessage());
        }
    }

    private void addCardActions(
            LinearLayout container,
            int index
    ) {
        LinearLayout actions =
                new LinearLayout(requireContext());

        actions.setOrientation(LinearLayout.HORIZONTAL);

        AppCompatButton edit = button("Sửa từ");
        AppCompatButton delete = button("Xóa từ");

        actions.addView(
                edit,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        actions.addView(
                delete,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        edit.setOnClickListener(v -> editCard(index));

        delete.setOnClickListener(
                v -> confirmDeleteCard(index)
        );

        container.addView(actions, rowParams());
    }

    private void showManageMenu() {
        if (!canEdit()) {
            return;
        }

        AlertDialog menu =
                new AlertDialog.Builder(requireContext())
                        .setTitle("Quản lý bộ thẻ")
                        .setItems(
                                new String[]{
                                        "Sửa tên / trình độ",
                                        "Thêm từ vựng",
                                        "Xóa bộ thẻ"
                                },
                                (dialog, which) -> {
                                    /*
                                     * Đóng menu trước khi mở hộp thoại mới.
                                     */
                                    dialog.dismiss();
                                    activeDialog = null;

                                    if (which == 0) {
                                        editDeckInfo();
                                    } else if (which == 1) {
                                        editCard(-1);
                                    } else {
                                        confirmDeleteDeck();
                                    }
                                }
                        )
                        .create();

        activeDialog = menu;

        menu.setOnDismissListener(ignored -> {
            if (activeDialog == menu) {
                activeDialog = null;
            }
        });

        menu.show();
    }

    private void editDeckInfo() {
        if (!canEdit()) {
            return;
        }

        try {
            DemoFlashcardRepository repository = repository();

            FlashcardDeck deck =
                    repository.getDeckById(getDeckId());

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
                String value =
                        name.getText().toString().trim();

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

            FlashcardDeck deck =
                    repository.getDeckById(getDeckId());

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

            EditText meaning = input(
                    "Nghĩa tiếng Việt",
                    false
            );

            EditText example = input(
                    "Câu ví dụ (không bắt buộc)",
                    true
            );

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
                                    editing
                                            ? "Sửa từ vựng"
                                            : "Thêm từ vựng"
                            )
                            .setView(form)
                            .setNegativeButton("Hủy", null)
                            .setPositiveButton("Lưu", null)
                            .create();

            showSaveDialog(dialog, () -> {
                String wordValue =
                        word.getText().toString().trim();

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
                        word.setError(
                                "Từ này đã có trong bộ thẻ."
                        );

                        word.requestFocus();
                        return;
                    }
                }

                List<Flashcard> updated =
                        new ArrayList<>(cards);

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

            FlashcardDeck deck =
                    repository.getDeckById(getDeckId());

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
                            .setMessage(
                                    "Xóa \"" + word
                                            + "\" khỏi bộ thẻ?"
                            )
                            .setNegativeButton("Hủy", null)
                            .setPositiveButton("Xóa", null)
                            .create();

            showSaveDialog(dialog, () -> {
                List<Flashcard> updated =
                        new ArrayList<>(cards);

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

    private LinearLayout form() {
        LinearLayout layout =
                new LinearLayout(requireContext());

        layout.setOrientation(LinearLayout.VERTICAL);

        layout.setPadding(
                dp(20),
                dp(8),
                dp(20),
                dp(12)
        );

        return layout;
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

        input.setPadding(
                dp(14),
                dp(14),
                dp(14),
                dp(14)
        );

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
                value * getResources()
                        .getDisplayMetrics().density
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
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(
                STATE_PREVIEW_INDEX,
                previewIndex
        );

        outState.putBoolean(
                STATE_PREVIEW_FLIPPED,
                previewFlipped
        );
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