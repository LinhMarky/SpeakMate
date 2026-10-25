package com.example.speakmate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class DeckSessionData {

    private DeckSessionData() {
    }

    static String encode(List<Flashcard> cards) {
        JSONArray array = new JSONArray();

        try {
            for (Flashcard card : cards) {
                JSONObject item = new JSONObject();

                item.put("word", card.getWord());
                item.put("meaning", card.getMeaning());

                item.put(
                        "example",
                        card.getExample() == null
                                ? ""
                                : card.getExample()
                );

                array.put(item);
            }

            return array.toString();
        } catch (JSONException exception) {
            throw new IllegalStateException(
                    "Không lưu được dữ liệu lượt học.",
                    exception
            );
        }
    }

    static ArrayList<Flashcard> decode(String json) {
        ArrayList<Flashcard> cards = new ArrayList<>();

        try {
            JSONArray array = new JSONArray(json);

            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);

                cards.add(new Flashcard(
                        item.getString("word"),
                        item.getString("meaning"),
                        item.optString("example", "")
                ));
            }

            return cards;
        } catch (JSONException exception) {
            throw new IllegalStateException(
                    "Không đọc được dữ liệu lượt học.",
                    exception
            );
        }
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}