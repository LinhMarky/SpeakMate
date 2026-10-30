package com.example.speakmate;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.speakmate.api.AdminApi;
import com.example.speakmate.auth.SessionManager;
import com.example.speakmate.ui.AdminFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;
import org.json.*;

public class AdminDecksFragment extends AdminFragment {
    private final List<JSONObject> decks = new ArrayList<>();
    private AdminApi admin;
    private boolean busy, catalog;
    private int nextPage, pages;

    public AdminDecksFragment() { super(R.layout.fragment_admin_decks); }

    public static AdminDecksFragment catalog() {
        AdminDecksFragment fragment = new AdminDecksFragment();
        Bundle args = new Bundle();
        args.putBoolean("catalog", true);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        catalog = getArguments() != null && getArguments().getBoolean("catalog");
        if (!catalog && !hasAdminSession()) return;
        if (SessionManager.get(requireContext()).load() == null) {
            view.post(() -> ((MainActivity) requireActivity()).onSessionExpired());
            return;
        }
        busy = false;
        admin = new AdminApi(SessionManager.get(requireContext()));
        view.findViewById(R.id.btnBackAdminDecks).setOnClickListener(v -> getParentFragmentManager().popBackStack());
        View add = view.findViewById(R.id.btnAdminAddDeck);
        add.setVisibility(catalog ? View.GONE : View.VISIBLE);
        add.setOnClickListener(v -> openForm(null));
        view.findViewById(R.id.tvAdminDeckListCount).setOnClickListener(v -> load(1));
        ((EditText) view.findViewById(R.id.etAdminDeckSearch)).addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    @Override public void onResume() {
        super.onResume();
        if (admin != null) load(1);
    }

    private void load(int page) {
        if (busy || getView() == null) return;
        busy = true;
        status("Đang tải bộ thẻ…", null);
        render();
        call(() -> admin.decks(page, !catalog), data -> {
            busy = false;
            if (page == 1) decks.clear();
            JSONArray rows = data.optJSONArray("result");
            if (rows != null) for (int i = 0; i < rows.length(); i++) decks.add(rows.optJSONObject(i));
            pages = data.optJSONObject("meta").optInt("pages");
            nextPage = page + 1;
            render();
            status(nextPage <= pages ? "Chạm để tải thêm bộ thẻ" : "Đã tải hết. Tìm kiếm trong các bộ đã tải.",
                    nextPage <= pages ? v -> load(nextPage) : null);
        }, error -> {
            busy = false; render();
            status(error.getMessage() + " Chạm để thử lại.", v -> load(page));
            showAdminError(error);
        });
    }

    private void render() {
        if (getView() == null) return;
        LinearLayout list = requireView().findViewById(R.id.adminDeckListContainer);
        list.removeAllViews();
        String search = ((EditText) requireView().findViewById(R.id.etAdminDeckSearch)).getText().toString().trim().toLowerCase(Locale.ROOT);
        int count = 0;
        for (JSONObject deck : decks) {
            if (deck == null || !deck.optString("name").toLowerCase(Locale.ROOT).contains(search)) continue;
            count++;
            View item = getLayoutInflater().inflate(R.layout.item_admin_deck, list, false);
            JSONArray words = deck.optJSONArray("words");
            ((TextView) item.findViewById(R.id.tvAdminDeckName)).setText(deck.optString("name"));
            ((TextView) item.findViewById(R.id.tvAdminDeckInfo)).setText((words == null ? 0 : words.length()) + " từ · " + deck.optString("level"));
            ((TextView) item.findViewById(R.id.tvAdminDeckVisibility)).setText(deck.optBoolean("active") ? "Đang phát hành" : "Đang ẩn");
            item.findViewById(R.id.btnAdminDeckDetail).setOnClickListener(v -> details(deck));
            Button edit = item.findViewById(R.id.btnAdminDeckEdit);
            edit.setText(catalog ? "Lưu và học bộ này" : "Sửa bộ thẻ");
            edit.setEnabled(!busy);
            edit.setOnClickListener(v -> { if (catalog) saveForLearner(deck); else openForm(deck); });
            Button toggle = item.findViewById(R.id.btnAdminDeckToggleVisibility);
            toggle.setVisibility(catalog ? View.GONE : View.VISIBLE);
            toggle.setEnabled(!busy);
            boolean active = deck.optBoolean("active");
            toggle.setText(active ? "Ẩn bộ thẻ" : "Phát hành bộ thẻ");
            toggle.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(active ? "Ẩn bộ thẻ?" : "Phát hành bộ thẻ?")
                    .setMessage("Từ người học đã lưu được giữ nguyên.").setNegativeButton("Hủy", null)
                    .setPositiveButton("Xác nhận", (d, w) -> toggle(deck, !active)).show());
            list.addView(item);
        }
        ((TextView) requireView().findViewById(R.id.tvAdminDeckListCount)).setText(count + " bộ trong " + decks.size() + " bộ đã tải · Chạm làm mới");
        requireView().findViewById(R.id.btnAdminAddDeck).setEnabled(!busy);
    }

    private void details(JSONObject deck) {
        StringBuilder text = new StringBuilder();
        JSONArray words = deck.optJSONArray("words");
        if (words != null) for (int i = 0; i < words.length(); i++) {
            JSONObject word = words.optJSONObject(i);
            text.append(word.optString("word")).append(" — ").append(word.optString("meaning")).append("\n");
            if (!word.isNull("example")) text.append(word.optString("example")).append("\n");
            text.append("\n");
        }
        new MaterialAlertDialogBuilder(requireContext()).setTitle(deck.optString("name"))
                .setMessage(text.toString()).setPositiveButton("Đóng", null).show();
    }

    private void openForm(JSONObject deck) {
        if (busy || getParentFragmentManager().isStateSaved()) return;
        getParentFragmentManager().beginTransaction().replace(R.id.fragmentContainer,
                deck == null ? new AdminDeckFormFragment() : AdminDeckFormFragment.edit(deck))
                .addToBackStack("admin_deck_form").commit();
    }

    private void toggle(JSONObject deck, boolean active) {
        if (busy) return;
        busy = true;
        render();
        call(() -> {
            JSONObject result = admin.deckActive(deck.getLong("id"), active);
            if (result.getLong("id") != deck.getLong("id") || result.getBoolean("active") != active) throw new JSONException("Invalid deck acknowledgement");
            return result;
        }, result -> { busy = false; load(1); }, error -> {
            busy = false; render();
            status(error.getMessage() + " Chạm để kiểm tra trạng thái.", v -> load(1));
            showAdminError(error);
        });
    }

    private void saveForLearner(JSONObject deck) {
        if (busy) return;
        busy = true;
        render();
        status("Đang lưu từ vào tài khoản…", null);
        SessionManager sessions = SessionManager.get(requireContext());
        long userId = sessions.load().user.optLong("id");
        call(() -> admin.saveSystemDeck(deck.getLong("id")), rows -> {
            busy = false;
            if (sessions.load() == null || sessions.load().user.optLong("id") != userId) return;
            try {
                List<Flashcard> cards = new ArrayList<>();
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject word = rows.getJSONObject(i);
                    cards.add(new Flashcard(word.getLong("id"), word.getString("word"), word.getString("meaning"),
                            word.isNull("example") ? "" : word.optString("example"), word.getString("status")));
                }
                LocalDeckStore local = new LocalDeckStore(requireContext());
                var preferences = requireContext().getSharedPreferences("speakmate_catalog", 0);
                String key = userId + ":" + deck.getLong("id");
                String localId = preferences.getString(key, "");
                final String previous = localId;
                if (local.getDecks().stream().anyMatch(d -> d.getId().equals(previous))) {
                    local.updateDeck(localId, deck.getString("name"), deck.getString("level"), cards);
                } else {
                    localId = local.createDeck(deck.getString("name"), deck.getString("level"), cards);
                    preferences.edit().putString(key, localId).apply();
                }
                getParentFragmentManager().beginTransaction().replace(R.id.fragmentContainer, DeckDetailFragment.newInstance(localId))
                        .addToBackStack("system_deck_saved").commit();
            } catch (Exception error) {
                status("Từ đã lưu vào tài khoản. Mở Từ của tôi để học.", null);
                render();
            }
        }, error -> {
            busy = false; render();
            status(error.getMessage() + " Chạm tải lại.", v -> load(1));
            showAdminError(error);
        });
    }

    private void status(String text, View.OnClickListener listener) {
        TextView status = requireView().findViewById(R.id.tvAdminDecksStatus);
        status.setText(text);
        status.setOnClickListener(listener);
    }
}
