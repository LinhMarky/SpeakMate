package com.example.speakmate;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class AdminDashboardFragment extends Fragment {

    private boolean openingSection;

    public AdminDashboardFragment() {
        super(R.layout.fragment_admin_dashboard);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        openingSection = false;

        view.findViewById(R.id.btnAdminUsers)
                .setOnClickListener(button ->
                        openSection(AdminSectionFragment.USERS)
                );

        view.findViewById(R.id.btnAdminTopics)
                .setOnClickListener(button ->
                        openSection(AdminSectionFragment.TOPICS)
                );

        view.findViewById(R.id.btnAdminDecks)
                .setOnClickListener(button ->
                        openSection(AdminSectionFragment.DECKS)
                );

        view.findViewById(R.id.btnAdminAccount)
                .setOnClickListener(button ->
                        openSection(AdminSectionFragment.ACCOUNT)
                );
    }

    @Override
    public void onResume() {
        super.onResume();
        openingSection = false;
    }

    private void openSection(@NonNull String section) {
        if (!isAdded()
                || !isResumed()
                || openingSection
                || getParentFragmentManager().isStateSaved()) {
            return;
        }

        openingSection = true;

        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.fragmentContainer,
                        USERS_SECTION(section)
                )
                .addToBackStack("admin_section")
                .commit();
    }

    @NonNull
    private Fragment USERS_SECTION(@NonNull String section) {
        if (AdminSectionFragment.USERS.equals(section)) {
            return new AdminUsersFragment();
        }

        if (AdminSectionFragment.TOPICS.equals(section)) {
            return new AdminTopicsFragment();
        }

        if (AdminSectionFragment.DECKS.equals(section)) {
            return new AdminDecksFragment();
        }

        if (AdminSectionFragment.ACCOUNT.equals(section)) {
            return new AdminAccountFragment();
        }

        return AdminSectionFragment.newInstance(section);
    }
}