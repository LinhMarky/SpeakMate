package com.example.speakmate;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public class DemoFlashcardRepository {

    private final LocalDeckStore localStore;
    private final FlashcardProgressStore progressStore;

    public DemoFlashcardRepository() {
        localStore = null;
        progressStore = null;
    }

    public DemoFlashcardRepository(Context context) {
        localStore = new LocalDeckStore(context);
        progressStore = new FlashcardProgressStore(context);
    }

    public List<FlashcardDeck> getDecks() {
        List<FlashcardDeck> decks = new ArrayList<>();

        decks.add(createDeck("travel", "Travel", "A2"));
        decks.add(createDeck("food", "Food and Drinks", "A1"));
        decks.add(createDeck("university", "University Life", "B1"));

        if (localStore != null) {
            for (FlashcardDeck deck : localStore.getDecks()) {
                decks.add(createDeck(
                        deck.getId(),
                        deck.getName(),
                        deck.getLevel()
                ));
            }
        }

        return decks;
    }

    private FlashcardDeck createDeck(
            String id,
            String name,
            String level
    ) {
        List<Flashcard> cards = getCards(id);

        int learned = progressStore == null
                ? 0
                : progressStore.getRememberedCount(id, cards);

        return new FlashcardDeck(
                id,
                name,
                level,
                cards.size(),
                learned
        );
    }

    public FlashcardDeck getDeckById(String deckId) {
        for (FlashcardDeck deck : getDecks()) {
            if (deck.getId().equals(deckId)) {
                return deck;
            }
        }

        return null;
    }

    public List<Flashcard> getCards(String deckId) {
        List<Flashcard> cards = new ArrayList<>();

        if (deckId == null) {
            return cards;
        }

        switch (deckId) {
            case "travel":
                cards.add(new Flashcard(
                        "Passport",
                        "Hộ chiếu",
                        "Please show your passport."
                ));
                cards.add(new Flashcard(
                        "Luggage",
                        "Hành lý",
                        "My luggage is very heavy."
                ));
                cards.add(new Flashcard(
                        "Reservation",
                        "Sự đặt chỗ",
                        "I have a reservation."
                ));
                break;

            case "food":
                cards.add(new Flashcard(
                        "Menu",
                        "Thực đơn",
                        "Can I see the menu, please?"
                ));
                cards.add(new Flashcard(
                        "Sandwich",
                        "Bánh mì kẹp",
                        "I'd like a chicken sandwich."
                ));
                cards.add(new Flashcard(
                        "Beverage",
                        "Đồ uống",
                        "What beverage would you like?"
                ));
                break;

            case "university":
                cards.add(new Flashcard(
                        "Assignment",
                        "Bài tập",
                        "I need to finish my assignment."
                ));
                cards.add(new Flashcard(
                        "Campus",
                        "Khuôn viên trường",
                        "Our campus has a large library."
                ));
                cards.add(new Flashcard(
                        "Major",
                        "Chuyên ngành",
                        "My major is computer science."
                ));
                break;

            default:
                if (localStore != null) {
                    return localStore.getCards(deckId);
                }
                break;
        }

        return cards;
    }
}