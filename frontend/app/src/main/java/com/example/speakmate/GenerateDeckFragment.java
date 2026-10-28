package com.example.speakmate;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

public class GenerateDeckFragment extends Fragment {

    private static final String[] LEVELS = {
            "A1", "A2", "B1", "B2", "C1", "C2"
    };

    private static final String[] COUNT_LABELS = {
            "5 từ", "10 từ", "15 từ", "20 từ"
    };

    public GenerateDeckFragment() {
        super(R.layout.fragment_generate_deck);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        EditText topicInput =
                view.findViewById(R.id.etGenerateDeckTopic);

        Spinner levelSpinner =
                view.findViewById(R.id.spGenerateDeckLevel);

        Spinner countSpinner =
                view.findViewById(R.id.spGenerateDeckCount);

        levelSpinner.setAdapter(createAdapter(LEVELS));
        countSpinner.setAdapter(createAdapter(COUNT_LABELS));

        if (savedInstanceState == null) {
            String savedLevel = requireContext()
                    .getSharedPreferences(
                            "speakmate_profile",
                            Context.MODE_PRIVATE
                    )
                    .getString("practice_level", "A2");

            levelSpinner.setSelection(1);
            countSpinner.setSelection(1);

            for (int i = 0; i < LEVELS.length; i++) {
                if (LEVELS[i].equals(savedLevel)) {
                    levelSpinner.setSelection(i);
                    break;
                }
            }
        }

        topicInput.setOnEditorActionListener(
                (input, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        hideKeyboard(input);
                        input.clearFocus();
                        return true;
                    }

                    return false;
                }
        );

        view.findViewById(R.id.btnBackGenerateDeck)
                .setOnClickListener(button -> goBack());

        view.findViewById(R.id.btnGenerateDeck)
                .setOnClickListener(button -> generate(view));

        View previewButton =
                view.findViewById(R.id.btnPreviewGeneratedDeck);

        boolean debugBuild =
                (requireContext().getApplicationInfo().flags
                        & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;

        previewButton.setVisibility(
                debugBuild ? View.VISIBLE : View.GONE
        );

        previewButton.setOnClickListener(button -> {
            if (!isResumed()
                    || getParentFragmentManager().isStateSaved()
                    || !button.isEnabled()) {
                return;
            }

            java.util.ArrayList<Flashcard> sampleCards =
                    new java.util.ArrayList<>();

            sampleCards.add(new Flashcard(
                    "Destination",
                    "Điểm đến",
                    "What is your destination?"
            ));

            sampleCards.add(new Flashcard(
                    "Journey",
                    "Chuyến đi",
                    "Have a safe journey."
            ));

            sampleCards.add(new Flashcard(
                    "Ticket",
                    "Vé",
                    "I would like to buy a ticket."
            ));

            Spinner selectedLevel =
                    view.findViewById(R.id.spGenerateDeckLevel);

            String reviewLevel =
                    String.valueOf(selectedLevel.getSelectedItem());

            hideKeyboard(view);
            button.setEnabled(false);

            getParentFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(
                            R.id.fragmentContainer,
                            GeneratedDeckReviewFragment.newInstance(
                                    "Bộ thẻ du lịch mẫu",
                                    reviewLevel,
                                    sampleCards,
                                    true
                            )
                    )
                    .addToBackStack("generated_deck_review")
                    .commit();
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        View view = getView();

        if (view != null) {
            view.findViewById(R.id.btnPreviewGeneratedDeck)
                    .setEnabled(true);
        }
    }

    private void generate(@NonNull View view) {
        if (!isAdded() || !isResumed()) {
            return;
        }

        EditText topicInput =
                view.findViewById(R.id.etGenerateDeckTopic);

        TextView status =
                view.findViewById(R.id.tvGenerateDeckStatus);

        status.setVisibility(View.GONE);

        String topic = topicInput.getText()
                .toString()
                .trim();

        if (topic.isEmpty()) {
            topicInput.setError("Bạn hãy nhập chủ đề muốn học.");
            topicInput.requestFocus();
            return;
        }

        topicInput.setError(null);

        hideKeyboard(view);
        topicInput.clearFocus();

        status.setText(
                "Chức năng tạo từ vựng bằng AI chưa kết nối "
                        + "máy chủ. Chưa có bộ thẻ nào được tạo."
        );

        status.setVisibility(View.VISIBLE);
    }

    private ArrayAdapter<String> createAdapter(String[] values) {
        return new ArrayAdapter<String>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                values
        ) {
            @NonNull
            @Override
            public View getView(
                    int position,
                    @Nullable View convertView,
                    @NonNull ViewGroup parent
            ) {
                View item = super.getView(
                        position,
                        convertView,
                        parent
                );

                styleText(item);
                return item;
            }

            @Override
            public View getDropDownView(
                    int position,
                    @Nullable View convertView,
                    @NonNull ViewGroup parent
            ) {
                View item = super.getDropDownView(
                        position,
                        convertView,
                        parent
                );

                styleText(item);

                item.setBackgroundColor(
                        ContextCompat.getColor(
                                requireContext(),
                                R.color.sm_surface
                        )
                );

                return item;
            }

            private void styleText(@NonNull View item) {
                if (!(item instanceof TextView)) {
                    return;
                }

                TextView text = (TextView) item;

                text.setTextColor(
                        ContextCompat.getColor(
                                requireContext(),
                                R.color.sm_text_primary
                        )
                );

                text.setTextSize(15);
                text.setGravity(Gravity.CENTER_VERTICAL);
                text.setMinHeight(dp(48));
                text.setPadding(dp(12), dp(8), dp(12), dp(8));
            }
        };
    }

    private void hideKeyboard(@NonNull View view) {
        WindowCompat.getInsetsController(
                requireActivity().getWindow(),
                view
        ).hide(WindowInsetsCompat.Type.ime());
    }

    private void goBack() {
        if (!isAdded()
                || !isResumed()
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        View view = getView();

        if (view != null) {
            hideKeyboard(view);
        }

        getParentFragmentManager().popBackStack();
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }
}