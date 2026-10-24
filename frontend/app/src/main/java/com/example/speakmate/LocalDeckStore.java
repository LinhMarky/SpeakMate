package com.example.speakmate;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
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
        String json = preferences.getString(KEY_DECKS, "[]");

        try {
            return new JSONArray(json);
        } catch (JSONException exception) {
            // Không ghi đè dữ liệu nếu dữ liệu đã lưu bị lỗi.
            throw new IllegalStateException(
                    "Không đọc được dữ liệu bộ thẻ đã lưu.",
                    exception
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
                    result.add(new Flashcard(
                            card.optString("word"),
                            card.optString("meaning"),
                            card.optString("example")
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
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Tên bộ thẻ không được để trống."
            );
        }

        if (cards == null || cards.isEmpty()) {
            throw new IllegalArgumentException(
                    "Bộ thẻ phải có ít nhất một từ."
            );
        }

        JSONArray decks = readDecks();
        String id = "local_" + UUID.randomUUID();

        try {
            JSONObject deck = new JSONObject();
            JSONArray cardArray = new JSONArray();

            for (Flashcard card : cards) {
                JSONObject item = new JSONObject();

                item.put("word", card.getWord());
                item.put("meaning", card.getMeaning());
                item.put("example", card.getExample());

                cardArray.put(item);
            }

            deck.put("id", id);
            deck.put("name", name.trim());
            deck.put("level", level);
            deck.put("cards", cardArray);

            decks.put(deck);
        } catch (JSONException exception) {
            throw new IllegalStateException(
                    "Không tạo được dữ liệu bộ thẻ.",
                    exception
            );
        }

        // Chỉ báo lưu thành công sau khi dữ liệu được ghi.
        boolean saved = preferences.edit()
                .putString(KEY_DECKS, decks.toString())
                .commit();

        if (!saved) {
            throw new IllegalStateException(
                    "Không lưu được bộ thẻ. Vui lòng thử lại."
            );
        }

        return id;
    }
}