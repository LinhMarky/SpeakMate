package com.example.speakmate;

import java.util.ArrayList;
import java.util.List;

public class DemoFlashcardRepository {

    public List<FlashcardDeck> getDecks() {
        List<FlashcardDeck> decks = new ArrayList<>();

        decks.add(new FlashcardDeck(
                "travel",
                "Travel",
                "A2",
                getCards("travel").size(),
                0
        ));

        decks.add(new FlashcardDeck(
                "food",
                "Food and Drinks",
                "A1",
                getCards("food").size(),
                0
        ));

        decks.add(new FlashcardDeck(
                "university",
                "University Life",
                "B1",
                getCards("university").size(),
                0
        ));

        return decks;
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
        }

        return cards;
    }
}