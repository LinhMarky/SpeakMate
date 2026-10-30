package vn.tailinh.speakmate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class SystemDeckWord {
    @Column(nullable = false, length = 100)
    private String word;
    @Column(nullable = false, length = 255)
    private String meaning;
    @Column(columnDefinition = "TEXT")
    private String example;

    public SystemDeckWord(String word, String meaning, String example) {
        this.word = word;
        this.meaning = meaning;
        this.example = example;
    }
}
