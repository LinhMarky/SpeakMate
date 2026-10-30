package com.example.speakmate;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class LocalDeckStore {

    private static final String PREFS_NAME = "speakmate_local_decks";
    private static final String KEY_DECKS = "decks";

    private final SharedPreferences preferences;

    public LocalDeckStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );
    }

    private JSONArray readDecks() {
        try {
            return new JSONArray(
                    preferences.getString(KEY_DECKS, "[]")
            );
        } catch (JSONException exception) {
            throw new IllegalStateException(
                    "Không đọc được dữ liệu bộ thẻ đã lưu.",
                    exception
            );
        }
    }

    private void writeDecks(JSONArray decks) {
        boolean saved = preferences.edit()
                .putString(KEY_DECKS, decks.toString())
                .commit();

        if (!saved) {
            throw new IllegalStateException(
                    "Không lưu được thay đổi. Vui lòng thử lại."
            );
        }
    }

    public List<FlashcardDeck> getDecks() {
        List<FlashcardDeck> result = new ArrayList<>();
        JSONArray decks = readDecks();

        for (int i = 0; i < decks.length(); i++) {
            JSONObject deck = decks.optJSONObject(i);

            if (deck == null) {
                continue;
            }

            JSONArray cards = deck.optJSONArray("cards");

            result.add(new FlashcardDeck(
                    deck.optString("id"),
                    deck.optString("name"),
                    deck.optString("level"),
                    cards == null ? 0 : cards.length(),
                    0
            ));
        }

        return result;
    }

    public List<Flashcard> getCards(String deckId) {
        List<Flashcard> result = new ArrayList<>();
        JSONArray decks = readDecks();

        for (int i = 0; i < decks.length(); i++) {
            JSONObject deck = decks.optJSONObject(i);

            if (deck == null
                    || !deck.optString("id").equals(deckId)) {
                continue;
            }

            JSONArray cards = deck.optJSONArray("cards");

            if (cards == null) {
                return result;
            }

            for (int j = 0; j < cards.length(); j++) {
                JSONObject card = cards.optJSONObject(j);

                if (card != null) {
                    long savedWordId = card.optLong("savedWordId", 0);
                    String status = card.optString("status", "LOCAL");

                    result.add(new Flashcard(
                            savedWordId,
                            card.optString("word"),
                            card.optString("meaning"),
                            card.optString("example"),
                            status
                    ));
                }
            }

            return result;
        }

        return result;
    }

    public String createDeck(
            String name,
            String level,
            List<Flashcard> cards
    ) {
        String id = "local_" + UUID.randomUUID();

        JSONObject deck = buildDeck(id, name, level, cards);

        JSONArray decks = readDecks();
        decks.put(deck);

        writeDecks(decks);

        return id;
    }

    public void updateDeck(
            String deckId,
            String name,
            String level,
            List<Flashcard> cards
    ) {
        requireLocalId(deckId);

        JSONObject updatedDeck =
                buildDeck(deckId, name, level, cards);

        JSONArray decks = readDecks();

        for (int i = 0; i < decks.length(); i++) {
            JSONObject deck = decks.optJSONObject(i);

            if (deck != null
                    && deckId.equals(deck.optString("id"))) {
                try {
                    decks.put(i, updatedDeck);
                } catch (JSONException exception) {
                    throw new IllegalStateException(
                            "Không cập nhật được bộ thẻ.",
                            exception
                    );
                }

                writeDecks(decks);
                return;
            }
        }

        throw new IllegalArgumentException(
                "Bộ thẻ không còn tồn tại."
        );
    }

    public void deleteDeck(String deckId) {
        requireLocalId(deckId);

        JSONArray decks = readDecks();
        JSONArray remaining = new JSONArray();

        boolean found = false;

        for (int i = 0; i < decks.length(); i++) {
            JSONObject deck = decks.optJSONObject(i);

            if (deck != null
                    && deckId.equals(deck.optString("id"))) {
                found = true;
            } else {
                remaining.put(decks.opt(i));
            }
        }

        if (!found) {
            throw new IllegalArgumentException(
                    "Bộ thẻ không còn tồn tại."
            );
        }

        writeDecks(remaining);
    }

    private void requireLocalId(String deckId) {
        if (deckId == null || !deckId.startsWith("local_")) {
            throw new IllegalArgumentException(
                    "Chỉ được chỉnh sửa bộ thẻ tự tạo."
            );
        }
    }

    private JSONObject buildDeck(
            String id,
            String name,
            String level,
            List<Flashcard> cards
    ) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Tên bộ thẻ không được để trống."
            );
        }

        if (level == null || level.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Vui lòng chọn trình độ."
            );
        }

        if (cards == null || cards.isEmpty()) {
            throw new IllegalArgumentException(
                    "Bộ thẻ phải có ít nhất một từ."
            );
        }

        Set<String> words = new HashSet<>();

        try {
            JSONObject deck = new JSONObject();
            JSONArray cardArray = new JSONArray();

            for (Flashcard card : cards) {
                if (card == null
                        || card.getWord() == null
                        || card.getWord().trim().isEmpty()
                        || card.getMeaning() == null
                        || card.getMeaning().trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Mỗi thẻ phải có từ tiếng Anh và nghĩa."
                    );
                }

                String word = card.getWord().trim();
                String normalized = word.toLowerCase(Locale.ROOT);

                if (!words.add(normalized)) {
                    throw new IllegalArgumentException(
                            "Từ bị trùng trong bộ thẻ: " + word
                    );
                }

                JSONObject item = new JSONObject();

                item.put("word", word);
                item.put("meaning", card.getMeaning().trim());

                item.put(
                        "example",
                        card.getExample() == null
                                ? ""
                                : card.getExample().trim()
                );

                if (card.getSavedWordId() > 0) {
                    item.put("savedWordId", card.getSavedWordId());
                }

                if (card.getStatus() != null && !card.getStatus().isEmpty()) {
                    item.put("status", card.getStatus());
                }

                cardArray.put(item);
            }

            deck.put("id", id);
            deck.put("name", name.trim());
            deck.put("level", level.trim());
            deck.put("cards", cardArray);

            return deck;
        } catch (JSONException exception) {
            throw new IllegalStateException(
                    "Không tạo được dữ liệu bộ thẻ.",
                    exception
            );
        }
    }
}
