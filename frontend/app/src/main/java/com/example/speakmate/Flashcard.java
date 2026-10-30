package com.example.speakmate;

public class Flashcard {

    private final String word;
    private final String meaning;
    private final String example;

    private final long savedWordId;
    private final String status;

    public Flashcard(
            String word,
            String meaning,
            String example
    ) {
        this(0, word, meaning, example, "LOCAL");
    }

    public Flashcard(long savedWordId, String word, String meaning, String example, String status) {
        this.savedWordId = savedWordId;
        this.status = status;
        this.word = word;
        this.meaning = meaning;
        this.example = example;
    }

    public String getWord() {
        return word;
    }

    public long getSavedWordId() {
        return savedWordId;
    }

    public String getStatus() {
        return status;
    }

    public String getMeaning() {
        return meaning;
    }

    public String getExample() {
        return example;
    }
}
