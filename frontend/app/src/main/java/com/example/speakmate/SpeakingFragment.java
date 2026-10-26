package com.example.speakmate;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SpeakingFragment extends Fragment {

    private static final String STATE_LEVEL = "speaking_selected_level";
    private static final String STATE_TOPIC = "speaking_selected_topic";

    private int selectedLevelId = R.id.rbA2;
    private int selectedTopicId = R.id.rbFood;

    private boolean openingConversation;

    public SpeakingFragment() {
        super(R.layout.fragment_speaking);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            selectedLevelId = savedInstanceState.getInt(
                    STATE_LEVEL,
                    R.id.rbA2
            );

            selectedTopicId = savedInstanceState.getInt(
                    STATE_TOPIC,
                    R.id.rbFood
            );
        } else {
            String savedLevel = requireContext()
                    .getSharedPreferences(
                            "speakmate_profile",
                            Context.MODE_PRIVATE
                    )
                    .getString("practice_level", "A2");

            selectedLevelId = getLevelId(savedLevel);
        }

        if (getLevelCode(selectedLevelId) == null) {
            selectedLevelId = R.id.rbA2;
        }

        if (getTopicCode(selectedTopicId) == null) {
            selectedTopicId = R.id.rbFood;
        }
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        openingConversation = false;

        RadioGroup levelGroup =
                view.findViewById(R.id.rgLevel);

        RadioGroup topicGroup =
                view.findViewById(R.id.rgTopic);

        levelGroup.check(selectedLevelId);
        topicGroup.check(selectedTopicId);

        updateLevelDescription(view, selectedLevelId);

        levelGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (getLevelCode(checkedId) == null) {
                return;
            }

            selectedLevelId = checkedId;
            updateLevelDescription(view, checkedId);
        });

        topicGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (getTopicCode(checkedId) != null) {
                selectedTopicId = checkedId;
            }
        });

        view.findViewById(R.id.btnBeginConversation)
                .setOnClickListener(button -> openConversation(view));
    }

    @Override
    public void onResume() {
        super.onResume();

        openingConversation = false;

        View view = getView();

        if (view != null) {
            view.findViewById(R.id.btnBeginConversation)
                    .setEnabled(true);
        }
    }

    private void openConversation(@NonNull View view) {
        if (!isAdded()
                || !isResumed()
                || openingConversation
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        RadioGroup levelGroup =
                view.findViewById(R.id.rgLevel);

        RadioGroup topicGroup =
                view.findViewById(R.id.rgTopic);

        String level = getLevelCode(
                levelGroup.getCheckedRadioButtonId()
        );

        String topic = getTopicCode(
                topicGroup.getCheckedRadioButtonId()
        );

        if (level == null || topic == null) {
            Toast.makeText(
                    requireContext(),
                    "Vui lòng chọn trình độ và chủ đề.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        openingConversation = true;

        view.findViewById(R.id.btnBeginConversation)
                .setEnabled(false);

        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.fragmentContainer,
                        ChatFragment.newInstance(level, topic)
                )
                .addToBackStack("chat")
                .commit();
    }

    private int getLevelId(@Nullable String level) {
        if (level == null) {
            return R.id.rbA2;
        }

        switch (level) {
            case "A1":
                return R.id.rbA1;
            case "A2":
                return R.id.rbA2;
            case "B1":
                return R.id.rbB1;
            case "B2":
                return R.id.rbB2;
            case "C1":
                return R.id.rbC1;
            case "C2":
                return R.id.rbC2;
            default:
                return R.id.rbA2;
        }
    }

    @Nullable
    private String getLevelCode(int checkedId) {
        if (checkedId == R.id.rbA1) {
            return "A1";
        } else if (checkedId == R.id.rbA2) {
            return "A2";
        } else if (checkedId == R.id.rbB1) {
            return "B1";
        } else if (checkedId == R.id.rbB2) {
            return "B2";
        } else if (checkedId == R.id.rbC1) {
            return "C1";
        } else if (checkedId == R.id.rbC2) {
            return "C2";
        }

        return null;
    }

    @Nullable
    private String getTopicCode(int checkedId) {
        if (checkedId == R.id.rbIntroduction) {
            return "Introducing Yourself";
        } else if (checkedId == R.id.rbFood) {
            return "Ordering Food";
        } else if (checkedId == R.id.rbTravel) {
            return "Travel";
        } else if (checkedId == R.id.rbUniversity) {
            return "University Life";
        }

        return null;
    }

    private void updateLevelDescription(
            @NonNull View view,
            int checkedId
    ) {
        TextView description =
                view.findViewById(R.id.tvLevelDescription);

        String text;

        if (checkedId == R.id.rbA1) {
            text = "A1 · Câu ngắn, từ cơ bản và chủ đề quen thuộc.";
        } else if (checkedId == R.id.rbA2) {
            text = "A2 · Luyện các tình huống giao tiếp hằng ngày.";
        } else if (checkedId == R.id.rbB1) {
            text = "B1 · Kể trải nghiệm và giải thích ý kiến của bạn.";
        } else if (checkedId == R.id.rbB2) {
            text = "B2 · Thảo luận, so sánh và trình bày lập luận.";
        } else if (checkedId == R.id.rbC1) {
            text = "C1 · Diễn đạt linh hoạt và thảo luận chủ đề phức tạp.";
        } else if (checkedId == R.id.rbC2) {
            text = "C2 · Diễn đạt chính xác và phân tích sắc thái ý nghĩa.";
        } else {
            text = "Chọn trình độ bạn muốn luyện.";
        }

        description.setText(text);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_LEVEL, selectedLevelId);
        outState.putInt(STATE_TOPIC, selectedTopicId);

        super.onSaveInstanceState(outState);
    }
}