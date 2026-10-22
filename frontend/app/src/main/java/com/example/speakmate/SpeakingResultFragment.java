package com.example.speakmate;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SpeakingResultFragment extends Fragment {

    public SpeakingResultFragment() {
        super(R.layout.fragment_speaking_result);
    }

    public static SpeakingResultFragment newInstance(
            String level,
            String topic,
            int userTurnCount
    ) {
        SpeakingResultFragment fragment =
                new SpeakingResultFragment();

        Bundle arguments = new Bundle();

        arguments.putString("level", level);
        arguments.putString("topic", topic);
        arguments.putInt("userTurnCount", userTurnCount);

        fragment.setArguments(arguments);

        return fragment;
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        Bundle arguments = requireArguments();

        String level = arguments.getString("level", "A2");
        String topic = arguments.getString(
                "topic",
                "Ordering Food"
        );

        int count = arguments.getInt("userTurnCount", 0);

        TextView topicText =
                view.findViewById(R.id.tvResultTopic);

        TextView statsText =
                view.findViewById(R.id.tvResultStats);

        TextView feedbackText =
                view.findViewById(R.id.tvResultFeedback);

        TextView vocabularyText =
                view.findViewById(R.id.tvResultVocabulary);

        topicText.setText(topic + " · " + level);

        statsText.setText(
                "Trình độ: " + level
                        + "\nSố lượt trả lời: " + count
        );

        if (count == 0) {
            feedbackText.setText(
                    "Bạn chưa gửi câu trả lời trong phiên này. "
                            + "Hãy thử trả lời ít nhất một câu "
                            + "ở buổi luyện tiếp theo."
            );
        } else {
            feedbackText.setText(
                    "Bạn đã gửi " + count + " lượt trả lời. "
                            + "Bạn có thể luyện lại chủ đề để thử "
                            + "những cách diễn đạt khác nhau."
            );
        }

        vocabularyText.setText(getVocabulary(topic));

        view.findViewById(R.id.btnBackToPractice)
                .setOnClickListener(v ->
                        ((MainActivity) requireActivity())
                                .navigateToTab(R.id.nav_speaking)
                );

        view.findViewById(R.id.btnResultHome)
                .setOnClickListener(v ->
                        ((MainActivity) requireActivity())
                                .navigateToTab(R.id.nav_home)
                );
    }

    private String getVocabulary(String topic) {
        switch (topic) {
            case "Introducing Yourself":
                return "Introduce · Giới thiệu"
                        + "\nHobby · Sở thích"
                        + "\nHometown · Quê quán";

            case "Travel":
                return "Passport · Hộ chiếu"
                        + "\nLuggage · Hành lý"
                        + "\nDestination · Điểm đến";

            case "University Life":
                return "Major · Chuyên ngành"
                        + "\nAssignment · Bài tập"
                        + "\nCampus · Khuôn viên trường";

            default:
                return "Menu · Thực đơn"
                        + "\nOrder · Gọi món"
                        + "\nReservation · Sự đặt chỗ";
        }
    }
}