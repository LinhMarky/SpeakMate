package com.example.speakmate;

import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SpeakingFragment extends Fragment {

    public SpeakingFragment() {
        super(R.layout.fragment_speaking);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        RadioGroup levelGroup = view.findViewById(R.id.rgLevel);
        RadioGroup topicGroup = view.findViewById(R.id.rgTopic);
        TextView description = view.findViewById(
                R.id.tvLevelDescription
        );

        levelGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbA1) {
                description.setText(
                        "A1 · Câu ngắn, từ cơ bản và chủ đề quen thuộc."
                );
            } else if (checkedId == R.id.rbA2) {
                description.setText(
                        "A2 · Luyện các tình huống giao tiếp hằng ngày."
                );
            } else if (checkedId == R.id.rbB1) {
                description.setText(
                        "B1 · Kể trải nghiệm và giải thích ý kiến của bạn."
                );
            } else if (checkedId == R.id.rbB2) {
                description.setText(
                        "B2 · Thảo luận, so sánh và trình bày lập luận."
                );
            }
        });

        view.findViewById(R.id.btnBeginConversation)
                .setOnClickListener(button -> {
                    int levelId = levelGroup.getCheckedRadioButtonId();
                    int topicId = topicGroup.getCheckedRadioButtonId();

                    if (levelId == -1 || topicId == -1) {
                        Toast.makeText(
                                requireContext(),
                                "Vui lòng chọn trình độ và chủ đề.",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    RadioButton levelButton = view.findViewById(levelId);

                    String level = levelButton.getText().toString();

                    String topic;

                    if (topicId == R.id.rbIntroduction) {
                        topic = "Introducing Yourself";
                    } else if (topicId == R.id.rbTravel) {
                        topic = "Travel";
                    } else if (topicId == R.id.rbUniversity) {
                        topic = "University Life";
                    } else {
                        topic = "Ordering Food";
                    }

                    if (getParentFragmentManager().isStateSaved()) {
                        return;
                    }

// Ngăn nhấn nhanh tạo nhiều màn hình chat.
                    button.setEnabled(false);

                    getParentFragmentManager()
                            .beginTransaction()
                            .setReorderingAllowed(true)
                            .replace(
                                    R.id.fragmentContainer,
                                    ChatFragment.newInstance(level, topic)
                            )
                            .addToBackStack("chat")
                            .commit();
                });
    }

    @Override
    public void onResume() {
        super.onResume();

        requireView()
                .findViewById(R.id.btnBeginConversation)
                .setEnabled(true);
    }
}