package com.example.speakmate;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Dùng chung số liệu bộ thẻ; từ đồng bộ lấy trạng thái mới nhất từ tài khoản. */
public final class FlashcardLibrary {

    private FlashcardLibrary() {
    }

    public static List<FlashcardDeck> decks(Context context, List<Flashcard> savedCards) {
        List<FlashcardDeck> result = new ArrayList<>();
        Map<Long, Flashcard> saved = index(savedCards);
        if (!saved.isEmpty()) {
            int mastered = 0;
            for (Flashcard card : saved.values()) if (mastered(card)) mastered++;
            result.add(new FlashcardDeck(FlashcardDeck.SAVED_WORDS_ID, "Từ của tôi", "Đồng bộ tài khoản",
                    saved.size(), mastered));
        }

        LocalDeckStore local = new LocalDeckStore(context);
        FlashcardProgressStore progress = new FlashcardProgressStore(context);
        for (FlashcardDeck deck : local.getDecks()) {
            List<Flashcard> cards = local.getCards(deck.getId());
            int remembered = 0;
            for (Flashcard card : cards) {
                boolean learned = card.getSavedWordId() > 0
                        ? mastered(saved.get(card.getSavedWordId()))
                        : progress.isRemembered(deck.getId(), card);
                if (learned) remembered++;
            }
            result.add(new FlashcardDeck(deck.getId(), deck.getName(), deck.getLevel(), cards.size(), remembered));
        }
        return result;
    }

    public static Statistics statistics(Context context, List<Flashcard> savedCards) {
        List<FlashcardDeck> decks = decks(context, savedCards);
        Map<Long, Flashcard> saved = index(savedCards);
        int total = saved.size();
        int remembered = 0;
        int completed = 0;
        for (Flashcard card : saved.values()) if (mastered(card)) remembered++;
        LocalDeckStore local = new LocalDeckStore(context);
        FlashcardProgressStore progress = new FlashcardProgressStore(context);
        for (FlashcardDeck deck : decks) {
            if (deck.getTotalCards() > 0 && deck.getLearnedCards() == deck.getTotalCards()) completed++;
            if (FlashcardDeck.SAVED_WORDS_ID.equals(deck.getId())) continue;
            for (Flashcard card : local.getCards(deck.getId())) {
                // Một từ AI có thể nằm trong bộ có tên và Từ của tôi; chỉ cộng một lần.
                if (card.getSavedWordId() > 0) continue;
                total++;
                if (progress.isRemembered(deck.getId(), card)) remembered++;
            }
        }
        return new Statistics(decks.size(), completed, total, remembered);
    }

    private static Map<Long, Flashcard> index(List<Flashcard> cards) {
        Map<Long, Flashcard> result = new HashMap<>();
        for (Flashcard card : cards) if (card.getSavedWordId() > 0) result.put(card.getSavedWordId(), card);
        return result;
    }

    private static boolean mastered(Flashcard card) {
        return card != null && "MASTERED".equals(card.getStatus());
    }

    public static final class Statistics {
        public final int deckCount, completedDeckCount, totalWords, rememberedWords;

        private Statistics(int deckCount, int completedDeckCount, int totalWords, int rememberedWords) {
            this.deckCount = deckCount;
            this.completedDeckCount = completedDeckCount;
            this.totalWords = totalWords;
            this.rememberedWords = rememberedWords;
        }
    }
}
