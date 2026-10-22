package com.example.speakmate;

public class FlashcardDeck {

    private final String id;
    private final String name;
    private final String level;
    private final int totalCards;
    private final int learnedCards;

    public FlashcardDeck(
            String id,
            String name,
            String level,
            int totalCards,
            int learnedCards
    ) {
        this.id = id;
        this.name = name;
        this.level = level;
        this.totalCards = totalCards;
        this.learnedCards = learnedCards;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLevel() {
        return level;
    }

    public int getTotalCards() {
        return totalCards;
    }

    public int getLearnedCards() {
        return learnedCards;
    }
}