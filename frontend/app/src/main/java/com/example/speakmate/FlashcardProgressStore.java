package com.example.speakmate;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

public class FlashcardProgressStore {

    private static final String PREFS_NAME =
            "speakmate_flashcard_progress";

    private final SharedPreferences preferences;

    public FlashcardProgressStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );
    }

    private String createKey(String deckId, Flashcard card) {
        /*
         * Dữ liệu demo chưa có cardId.
         * Dùng deckId + từ vựng để nhận diện từng thẻ.
         */
        return deckId.length() + ":" + deckId + ":" + card.getWord();
    }

    public void saveAnswer(
            String deckId,
            Flashcard card,
            boolean remembered
    ) {
        preferences.edit()
                .putBoolean(
                        createKey(deckId, card),
                        remembered
                )
                .apply();
    }

    public boolean isRemembered(
            String deckId,
            Flashcard card
    ) {
        return preferences.getBoolean(
                createKey(deckId, card),
                false
        );
    }

    public int getRememberedCount(
            String deckId,
            List<Flashcard> cards
    ) {
        int count = 0;

        for (Flashcard card : cards) {
            if (isRemembered(deckId, card)) {
                count++;
            }
        }

        return count;
    }
}