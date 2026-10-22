package com.example.speakmate;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

public class PlaceholderFragment extends Fragment {

    public PlaceholderFragment() {
        // Constructor rỗng để Android khôi phục Fragment.
    }

    public static PlaceholderFragment newInstance(String title) {
        PlaceholderFragment fragment = new PlaceholderFragment();

        Bundle arguments = new Bundle();
        arguments.putString("title", title);
        fragment.setArguments(arguments);

        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        int padding = Math.round(
                24 * getResources().getDisplayMetrics().density
        );

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, padding, padding, padding);
        layout.setBackgroundColor(ContextCompat.getColor(
                requireContext(), R.color.sm_background
        ));

        TextView title = new TextView(requireContext());
        title.setText(requireArguments().getString("title"));
        title.setTextSize(26);
        title.setTypeface(
                title.getTypeface(),
                android.graphics.Typeface.BOLD
        );
        title.setTextColor(ContextCompat.getColor(
                requireContext(), R.color.sm_text_primary
        ));

        TextView description = new TextView(requireContext());
        description.setText("Giao diện sẽ được hoàn thiện ở bước tiếp theo.");
        description.setTextSize(15);
        description.setTextColor(ContextCompat.getColor(
                requireContext(), R.color.sm_text_secondary
        ));

        LinearLayout.LayoutParams descriptionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        descriptionParams.topMargin = padding;

        layout.addView(title);
        layout.addView(description, descriptionParams);

        return layout;
    }
}