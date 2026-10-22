package com.example.speakmate;

public class Flashcard {

    private final String word;
    private final String meaning;
    private final String example;

    public Flashcard(
            String word,
            String meaning,
            String example
    ) {
        this.word = word;
        this.meaning = meaning;
        this.example = example;
    }

    public String getWord() {
        return word;
    }

    public String getMeaning() {
        return meaning;
    }

    public String getExample() {
        return example;
    }
}