package com.example.speakmate;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class HomeFragment extends Fragment {

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        java.util.List<FlashcardDeck> decks =
                new DemoFlashcardRepository().getDecks();

        int learned = 0;

        for (FlashcardDeck deck : decks) {
            learned += deck.getLearnedCards();
        }

        ((android.widget.TextView)
                view.findViewById(R.id.tvLearnedWordCount))
                .setText(String.valueOf(learned));

// Chưa triển khai lưu lịch sử phiên luyện.
        ((android.widget.TextView)
                view.findViewById(R.id.tvSessionCount))
                .setText("0");

        view.findViewById(R.id.cardRecentSession)
                .setVisibility(View.GONE);

        BottomNavigationView navigation =
                requireActivity().findViewById(R.id.bottomNavigation);

        view.findViewById(R.id.btnStartSpeaking)
                .setOnClickListener(v ->
                        navigation.setSelectedItemId(R.id.nav_speaking));

        view.findViewById(R.id.cardFlashcards)
                .setOnClickListener(v ->
                        navigation.setSelectedItemId(R.id.nav_flashcards));
    }
}