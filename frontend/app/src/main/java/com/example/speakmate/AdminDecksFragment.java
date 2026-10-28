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

import java.util.List;
import java.util.Locale;

public class AdminDecksFragment extends Fragment {

    private static final String STATE_HIDDEN_IDS =
            "admin_demo_hidden_deck_ids";

    // Chỉ hiển thị các bộ demo đã biết, không lấy bộ thẻ cá nhân.
    private static final String[] DEMO_DECK_IDS = {
            "travel",
            "food",
            "university"
    };

    private final java.util.ArrayList<String> hiddenDeckIds =
            new java.util.ArrayList<>();

    private DemoFlashcardRepository repository;

    private EditText searchInput;
    private TextWatcher searchWatcher;
    private AlertDialog activeDialog;

    private boolean openingForm;

    public AdminDecksFragment() {
        super(R.layout.fragment_admin_decks);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            java.util.ArrayList<String> restored =
                    savedInstanceState.getStringArrayList(
                            STATE_HIDDEN_IDS
                    );

            if (restored != null) {
                hiddenDeckIds.addAll(restored);
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

        repository = new DemoFlashcardRepository(requireContext());

        searchInput =
                view.findViewById(R.id.etAdminDeckSearch);

        TextView note =
                view.findViewById(R.id.tvAdminDecksStatus);

        note.setText(
                "DỮ LIỆU MẪU · Danh sách sử dụng các bộ thẻ demo. "
                        + "Ẩn/hiện chỉ để kiểm tra giao diện admin, "
                        + "chưa cập nhật thư viện của người học."
        );

        view.findViewById(R.id.btnBackAdminDecks)
                .setOnClickListener(button -> returnToDashboard());

        view.findViewById(R.id.btnAdminAddDeck)
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
                renderDecks();
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

        renderDecks();
    }

    @Override
    public void onViewStateRestored(
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewStateRestored(savedInstanceState);
        renderDecks();
    }

    @Override
    public void onResume() {
        super.onResume();
        openingForm = false;
    }

    private void renderDecks() {
        View root = getView();

        if (root == null
                || searchInput == null
                || repository == null) {
            return;
        }

        LinearLayout container =
                root.findViewById(R.id.adminDeckListContainer);

        TextView countText =
                root.findViewById(R.id.tvAdminDeckListCount);

        container.removeAllViews();

        String query = normalize(
                searchInput.getText().toString()
        );

        int count = 0;

        try {
            for (String deckId : DEMO_DECK_IDS) {
                FlashcardDeck deck =
                        repository.getDeckById(deckId);

                if (deck == null
                        || !normalize(deck.getName()).contains(query)) {
                    continue;
                }

                count++;

                View item = getLayoutInflater().inflate(
                        R.layout.item_admin_deck,
                        container,
                        false
                );

                TextView name =
                        item.findViewById(R.id.tvAdminDeckName);

                TextView info =
                        item.findViewById(R.id.tvAdminDeckInfo);

                TextView visibility =
                        item.findViewById(R.id.tvAdminDeckVisibility);

                AppCompatButton toggle =
                        item.findViewById(
                                R.id.btnAdminDeckToggleVisibility
                        );

                boolean hidden = hiddenDeckIds.contains(deckId);

                name.setText(deck.getName());

                info.setText(
                        deck.getTotalCards() + " từ"
                                + " · Trình độ " + deck.getLevel()
                );

                visibility.setText(
                        hidden
                                ? "Đang ẩn · Mẫu"
                                : "Đang hiển thị · Mẫu"
                );

                visibility.setTextColor(
                        ContextCompat.getColor(
                                requireContext(),
                                hidden
                                        ? R.color.sm_warning
                                        : R.color.sm_success
                        )
                );

                toggle.setText(
                        hidden
                                ? "Hiện bộ thẻ mẫu"
                                : "Ẩn bộ thẻ mẫu"
                );

                toggle.setTextColor(
                        ContextCompat.getColor(
                                requireContext(),
                                hidden
                                        ? R.color.sm_cyan
                                        : R.color.sm_warning
                        )
                );

                item.findViewById(R.id.btnAdminDeckDetail)
                        .setOnClickListener(
                                button -> showDetails(deck)
                        );

                toggle.setOnClickListener(
                        button -> confirmToggleVisibility(deck)
                );

                item.findViewById(R.id.btnAdminDeckEdit)
                        .setOnClickListener(
                                button -> openEditForm(deck)
                        );

                container.addView(item);
            }

            countText.setText(
                    count == 0
                            ? "Không tìm thấy bộ thẻ phù hợp."
                            : count + " bộ thẻ mẫu"
            );

        } catch (IllegalStateException exception) {
            container.removeAllViews();

            countText.setText(
                    "Chưa đọc được dữ liệu bộ thẻ."
            );
        }
    }

    private void showDetails(@NonNull FlashcardDeck deck) {
        if (!canOpenDialog() || repository == null) {
            return;
        }

        hideKeyboard(requireView());

        StringBuilder content = new StringBuilder();

        content.append("Trình độ: ")
                .append(deck.getLevel())
                .append("\n\n");

        try {
            List<Flashcard> cards =
                    repository.getCards(deck.getId());

            if (cards.isEmpty()) {
                content.append("Bộ thẻ chưa có từ vựng.");
            } else {
                for (int i = 0; i < cards.size(); i++) {
                    Flashcard card = cards.get(i);

                    content.append(i + 1)
                            .append(". ")
                            .append(card.getWord())
                            .append(" · ")
                            .append(card.getMeaning());

                    String example = card.getExample();

                    if (example != null && !example.trim().isEmpty()) {
                        content.append("\n")
                                .append(example);
                    }

                    content.append("\n\n");
                }
            }

        } catch (IllegalStateException exception) {
            content.append("Chưa đọc được danh sách từ vựng.");
        }

        content.append("\nĐây là nội dung demo.");

        AlertDialog dialog =
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(deck.getName())
                        .setMessage(content.toString())
                        .setPositiveButton("Đóng", null)
                        .create();

        showDialog(dialog);
    }

    private void confirmToggleVisibility(
            @NonNull FlashcardDeck deck
    ) {
        if (!canOpenDialog()) {
            return;
        }

        hideKeyboard(requireView());

        String deckId = deck.getId();
        boolean willHide = !hiddenDeckIds.contains(deckId);

        AlertDialog dialog =
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(
                                willHide
                                        ? "Ẩn bộ thẻ mẫu?"
                                        : "Hiện bộ thẻ mẫu?"
                        )
                        .setMessage(
                                "Thay đổi trạng thái của “"
                                        + deck.getName()
                                        + "” để kiểm tra giao diện. "
                                        + "Thao tác không thay đổi "
                                        + "thư viện của người học."
                        )
                        .setNegativeButton("Hủy", null)
                        .setPositiveButton(
                                willHide ? "Ẩn" : "Hiện",
                                (ignored, which) -> {
                                    if (willHide) {
                                        if (!hiddenDeckIds.contains(deckId)) {
                                            hiddenDeckIds.add(deckId);
                                        }
                                    } else {
                                        hiddenDeckIds.remove(deckId);
                                    }

                                    renderDecks();
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
                        new AdminDeckFormFragment()
                )
                .addToBackStack("admin_deck_form")
                .commit();
    }

    private void openEditForm(@NonNull FlashcardDeck deck) {
        if (!canOpenDialog() || repository == null) {
            return;
        }

        try {
            java.util.List<Flashcard> cards =
                    repository.getCards(deck.getId());

            AdminDeckFormFragment form =
                    AdminDeckFormFragment.newEditInstance(
                            deck,
                            cards
                    );

            hideKeyboard(requireView());
            openingForm = true;

            getParentFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(
                            R.id.fragmentContainer,
                            form
                    )
                    .addToBackStack("admin_edit_deck")
                    .commit();

        } catch (IllegalStateException exception) {
            android.widget.Toast.makeText(
                    requireContext(),
                    "Chưa đọc được nội dung bộ thẻ.",
                    android.widget.Toast.LENGTH_SHORT
            ).show();
        }
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
        outState.putStringArrayList(
                STATE_HIDDEN_IDS,
                new java.util.ArrayList<>(hiddenDeckIds)
        );

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
        repository = null;

        super.onDestroyView();
    }
}