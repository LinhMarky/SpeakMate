package com.example.speakmate;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.speakmate.ui.NetworkFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class GeneratedDeckReviewFragment extends NetworkFragment {

    private static final String ARG_CARDS = "cards";
    private static final String ARG_NAME = "name";
    private static final String ARG_LEVEL = "level";
    private static final String ARG_SAMPLE = "sample";

    private static final String STATE_CARDS = "draft_cards";
    private static final String STATE_SELECTED = "draft_selected";
    private static final String STATE_NAME = "draft_name";
    private static final String STATE_CREATED_ID = "created_id";
    private static final String STATE_SAVED_MSG = "saved_msg";

    private final ArrayList<Flashcard> cards = new ArrayList<>();
    private final ArrayList<WordRow> rows = new ArrayList<>();

    private boolean[] selected;
    private String draftName = "";
    private String level = "A2";
    private String createdDeckId;
    private String saveStatusMessage;

    private boolean sample;
    private boolean openingDeck;
    private boolean saving;

    private androidx.appcompat.app.AlertDialog leaveDialog;

    public GeneratedDeckReviewFragment() {
        super(R.layout.fragment_generated_deck_review);
    }

    public static GeneratedDeckReviewFragment newInstance(
            String name,
            String level,
            List<Flashcard> cards,
            boolean sample
    ) {
        GeneratedDeckReviewFragment fragment =
                new GeneratedDeckReviewFragment();

        Bundle arguments = new Bundle();

        arguments.putString(ARG_NAME, name);
        arguments.putString(ARG_LEVEL, level);
        arguments.putString(
                ARG_CARDS,
                DeckSessionData.encode(cards)
        );
        arguments.putBoolean(ARG_SAMPLE, sample);

        fragment.setArguments(arguments);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arguments = requireArguments();

        level = arguments.getString(ARG_LEVEL, "A2");
        sample = arguments.getBoolean(ARG_SAMPLE, false);

        draftName = savedInstanceState == null
                ? arguments.getString(ARG_NAME, "")
                : savedInstanceState.getString(STATE_NAME, "");

        String encodedCards = savedInstanceState == null
                ? arguments.getString(ARG_CARDS, "[]")
                : savedInstanceState.getString(STATE_CARDS, "[]");

        cards.addAll(DeckSessionData.decode(encodedCards));

        selected = new boolean[cards.size()];

        for (int i = 0; i < selected.length; i++) {
            selected[i] = true;
        }

        if (savedInstanceState != null) {
            boolean[] restored =
                    savedInstanceState.getBooleanArray(STATE_SELECTED);

            if (restored != null && restored.length == cards.size()) {
                selected = restored.clone();
            }

            createdDeckId =
                    savedInstanceState.getString(STATE_CREATED_ID);
            saveStatusMessage =
                    savedInstanceState.getString(STATE_SAVED_MSG);
        }
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        rows.clear();
        openingDeck = false;
        saving = false;

        EditText nameInput =
                view.findViewById(R.id.etGeneratedDeckName);

        TextView note =
                view.findViewById(R.id.tvGeneratedReviewNote);

        nameInput.setText(draftName);
        setupDoneAction(nameInput);

        note.setText(
                sample
                        ? "KẾT QUẢ MẪU · Đây là từ vựng minh họa, "
                          + "không phải kết quả AI. Bạn có thể sửa và "
                          + "lưu bộ thẻ vào tài khoản và trên thiết bị."
                        : "Kiểm tra và chỉnh nội dung trước khi lưu. "
                          + "Bỏ chọn những từ bạn không muốn giữ."
        );

        LinearLayout container =
                view.findViewById(R.id.generatedReviewContainer);

        container.removeAllViews();

        for (int i = 0; i < cards.size(); i++) {
            Flashcard card = cards.get(i);

            View item = getLayoutInflater().inflate(
                    R.layout.item_generated_word,
                    container,
                    false
            );

            WordRow row = new WordRow(item);

            row.word.setText(card.getWord());
            row.meaning.setText(card.getMeaning());
            row.example.setText(card.getExample());
            row.selected.setChecked(selected[i]);

            setupDoneAction(row.example);

            rows.add(row);
            container.addView(item);

            row.selected.setOnCheckedChangeListener(
                    (button, checked) -> updateControls()
            );
        }

        view.findViewById(R.id.btnBackGeneratedReview)
                .setOnClickListener(button -> goBack());

        requireActivity()
                .getOnBackPressedDispatcher()
                .addCallback(
                        getViewLifecycleOwner(),
                        new androidx.activity.OnBackPressedCallback(true) {
                            @Override
                            public void handleOnBackPressed() {
                                goBack();
                            }
                        }
                );

        view.findViewById(R.id.btnSaveGeneratedDeck)
                .setOnClickListener(button -> saveDeck());

        view.findViewById(R.id.btnOpenGeneratedDeck)
                .setOnClickListener(button -> openDeck());

        updateControls();
    }

    @Override
    public void onResume() {
        super.onResume();

        openingDeck = false;

        // Bộ thẻ có thể đã bị xóa khi người dùng mở trang chi tiết.
        if (createdDeckId != null && !"synced".equals(createdDeckId)) {
            try {
                if (new DemoFlashcardRepository(requireContext())
                        .getDeckById(createdDeckId) == null) {
                    createdDeckId = null;
                }
            } catch (IllegalStateException ignored) {
                // Giữ trạng thái đã lưu để tránh tạo trùng khi đọc lỗi.
            }
        }

        updateControls();
    }

    private void updateControls() {
        View view = getView();

        if (view == null) {
            return;
        }

        int count = 0;
        boolean saved = createdDeckId != null;

        for (WordRow row : rows) {
            boolean keep = row.selected.isChecked();

            if (keep) {
                count++;
            }

            row.selected.setEnabled(!saved && !saving);
            row.word.setEnabled(!saved && !saving && keep);
            row.meaning.setEnabled(!saved && !saving && keep);
            row.example.setEnabled(!saved && !saving && keep);
        }

        TextView countText =
                view.findViewById(R.id.tvGeneratedSelectedCount);

        countText.setText(
                "Đã chọn " + count + "/" + rows.size() + " từ"
        );

        view.findViewById(R.id.etGeneratedDeckName)
                .setEnabled(!saved && !saving);

        view.findViewById(R.id.btnSaveGeneratedDeck)
                .setEnabled(!saved && !saving && count > 0);

        View openButton =
                view.findViewById(R.id.btnOpenGeneratedDeck);

        openButton.setVisibility(saved ? View.VISIBLE : View.GONE);
        openButton.setEnabled(!openingDeck);

        TextView status =
                view.findViewById(R.id.tvGeneratedSaveStatus);

        status.setVisibility(saved ? View.VISIBLE : View.GONE);

        if (saved) {
            status.setText(saveStatusMessage != null ? saveStatusMessage : "Đã lưu bộ thẻ vào tài khoản.");
        }
    }

    private void saveDeck() {
        View view = getView();

        if (view == null
                || !isResumed()
                || createdDeckId != null
                || saving) {
            return;
        }

        EditText nameInput =
                view.findViewById(R.id.etGeneratedDeckName);

        String name = nameInput.getText().toString().trim();

        if (name.isEmpty()) {
            nameInput.setError("Bạn hãy nhập tên bộ thẻ.");
            nameInput.requestFocus();
            return;
        }

        nameInput.setError(null);

        ArrayList<Flashcard> chosenCards = new ArrayList<>();
        Set<String> usedWords = new HashSet<>();

        for (WordRow row : rows) {
            if (!row.selected.isChecked()) {
                continue;
            }

            String word = row.word.getText().toString().trim();
            String meaning = row.meaning.getText().toString().trim();
            String example = row.example.getText().toString().trim();

            if (word.isEmpty()) {
                row.word.setError("Bạn hãy nhập từ tiếng Anh.");
                row.word.requestFocus();
                return;
            }

            if (meaning.isEmpty()) {
                row.meaning.setError("Bạn hãy nhập nghĩa.");
                row.meaning.requestFocus();
                return;
            }

            String normalized = word.replaceAll("\\s+", " ")
                    .toLowerCase(Locale.ROOT);

            if (!usedWords.add(normalized)) {
                row.word.setError("Từ này bị trùng.");
                row.word.requestFocus();
                return;
            }

            row.word.setError(null);
            row.meaning.setError(null);

            chosenCards.add(new Flashcard(word, meaning, example));
        }

        if (chosenCards.isEmpty()) {
            showStatus("Bạn hãy chọn ít nhất một từ.");
            return;
        }

        saving = true;
        updateControls();
        showStatus("Đang lưu " + chosenCards.size() + " từ vào tài khoản…");

        call(() -> api.saveGeneratedWords(chosenCards), response -> {
            saving = false;

            Map<String, ServerWordInfo> serverWords = new HashMap<>();
            int createdCount = 0;
            int existingCount = 0;

            if (response != null) {
                org.json.JSONArray createdArr = response.optJSONArray("created");
                org.json.JSONArray existingArr = response.optJSONArray("existing");

                if (createdArr != null) {
                    createdCount = createdArr.length();
                    collectServerWords(serverWords, createdArr);
                }
                if (existingArr != null) {
                    existingCount = existingArr.length();
                    collectServerWords(serverWords, existingArr);
                }
            }

            ArrayList<Flashcard> mappedCards = new ArrayList<>();
            for (Flashcard card : chosenCards) {
                String key = DeckSessionData.normalize(card.getWord());
                ServerWordInfo info = serverWords.get(key);
                long serverId = (info != null && info.id > 0) ? info.id : card.getSavedWordId();
                String serverStatus = (info != null && info.status != null && !info.status.isEmpty())
                        ? info.status : card.getStatus();
                mappedCards.add(new Flashcard(
                        serverId,
                        card.getWord(),
                        card.getMeaning(),
                        card.getExample(),
                        serverStatus
                ));
            }

            try {
                createdDeckId = new LocalDeckStore(requireContext())
                        .createDeck(name, level, mappedCards);
            } catch (Exception ignored) {
                createdDeckId = "synced";
            }
            draftName = name;
            hideKeyboard(view);
            nameInput.clearFocus();

            cards.clear();
            cards.addAll(mappedCards);

            saveStatusMessage = "Đã lưu " + createdCount + " từ mới vào \"Từ của tôi\""
                    + (existingCount > 0 ? " (" + existingCount + " từ đã có sẵn)" : "")
                    + " và đồng bộ vào tài khoản.";
            updateControls();
        }, error -> {
            saving = false;
            updateControls();
            showStatus("Chưa lưu được từ vựng lên máy chủ: " + error.getMessage() + "\nVui lòng thử lại.");
        });
    }

    private void collectServerWords(Map<String, ServerWordInfo> map, org.json.JSONArray array) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            org.json.JSONObject obj = array.optJSONObject(i);
            if (obj != null) {
                long id = obj.optLong("id", 0);
                String word = obj.optString("word", "");
                String status = obj.optString("status", "NEW");
                if (id > 0 && !word.isEmpty()) {
                    map.put(DeckSessionData.normalize(word), new ServerWordInfo(id, status));
                }
            }
        }
    }

    private static class ServerWordInfo {
        final long id;
        final String status;

        ServerWordInfo(long id, String status) {
            this.id = id;
            this.status = status;
        }
    }

    private void openDeck() {
        if (!isAdded()
                || !isResumed()
                || openingDeck
                || createdDeckId == null
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        openingDeck = true;
        updateControls();

        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.fragmentContainer,
                        DeckDetailFragment.newInstance(createdDeckId)
                )
                .addToBackStack("generated_deck_detail")
                .commit();
    }

    private void showStatus(String message) {
        View view = getView();

        if (view != null) {
            TextView status =
                    view.findViewById(R.id.tvGeneratedSaveStatus);

            status.setText(message);
            status.setVisibility(View.VISIBLE);
        }
    }

    private void captureDraft() {
        View view = getView();

        if (view == null) {
            return;
        }

        EditText name =
                view.findViewById(R.id.etGeneratedDeckName);

        draftName = name.getText().toString();

        cards.clear();
        selected = new boolean[rows.size()];

        for (int i = 0; i < rows.size(); i++) {
            WordRow row = rows.get(i);

            cards.add(new Flashcard(
                    row.word.getText().toString(),
                    row.meaning.getText().toString(),
                    row.example.getText().toString()
            ));

            selected[i] = row.selected.isChecked();
        }
    }

    private void setupDoneAction(@NonNull EditText input) {
        input.setOnEditorActionListener((text, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(text);
                text.clearFocus();
                return true;
            }

            return false;
        });
    }

    private void hideKeyboard(@NonNull View view) {
        WindowCompat.getInsetsController(
                requireActivity().getWindow(),
                view
        ).hide(WindowInsetsCompat.Type.ime());
    }

    private void goBack() {
        View view = getView();

        if (view == null
                || !isAdded()
                || !isResumed()
                || openingDeck
                || leaveDialog != null
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        androidx.core.view.WindowInsetsCompat insets =
                androidx.core.view.ViewCompat.getRootWindowInsets(view);

        boolean keyboardVisible = insets != null
                && insets.isVisible(
                androidx.core.view.WindowInsetsCompat.Type.ime()
        );

        // Lần Back đầu đóng bàn phím.
        if (keyboardVisible) {
            hideKeyboard(view);

            View focused = requireActivity().getCurrentFocus();

            if (focused != null) {
                focused.clearFocus();
            }

            return;
        }

        // Đã lưu hoặc không có từ vựng thì quay lại ngay.
        if (createdDeckId != null || cards.isEmpty()) {
            getParentFragmentManager().popBackStack();
            return;
        }

        androidx.appcompat.app.AlertDialog dialog =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                        requireContext()
                )
                        .setTitle("Rời màn hình khi chưa lưu?")
                        .setMessage(
                                "Danh sách từ vựng và những chỉnh sửa "
                                        + "hiện tại chưa được lưu thành bộ thẻ."
                                        + "\n\nNếu rời màn hình, bạn sẽ "
                                        + "không giữ được nội dung này."
                        )
                        .setNegativeButton("Tiếp tục chỉnh sửa", null)
                        .setPositiveButton(
                                "Rời màn hình",
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

        leaveDialog = dialog;

        dialog.setOnDismissListener(ignored -> {
            if (leaveDialog == dialog) {
                leaveDialog = null;
            }
        });

        dialog.show();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        captureDraft();

        outState.putString(STATE_NAME, draftName);
        outState.putString(
                STATE_CARDS,
                DeckSessionData.encode(cards)
        );
        outState.putBooleanArray(
                STATE_SELECTED,
                selected.clone()
        );
        outState.putString(STATE_CREATED_ID, createdDeckId);
        outState.putString(STATE_SAVED_MSG, saveStatusMessage);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        if (leaveDialog != null) {
            leaveDialog.setOnDismissListener(null);
            leaveDialog.dismiss();
            leaveDialog = null;
        }
        captureDraft();
        rows.clear();

        super.onDestroyView();
    }

    private static class WordRow {
        final CheckBox selected;
        final EditText word;
        final EditText meaning;
        final EditText example;

        WordRow(View view) {
            selected = view.findViewById(R.id.cbGeneratedWordSelected);
            word = view.findViewById(R.id.etGeneratedWord);
            meaning = view.findViewById(R.id.etGeneratedMeaning);
            example = view.findViewById(R.id.etGeneratedExample);
        }
    }
}
