package com.example.speakmate;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class AdminSectionFragment extends Fragment {

    public static final String USERS = "users";
    public static final String TOPICS = "topics";
    public static final String DECKS = "decks";
    public static final String ACCOUNT = "account";

    private static final String ARG_SECTION = "section";

    public AdminSectionFragment() {
        super(R.layout.fragment_admin_section);
    }

    public static AdminSectionFragment newInstance(
            @NonNull String section
    ) {
        AdminSectionFragment fragment =
                new AdminSectionFragment();

        Bundle arguments = new Bundle();
        arguments.putString(ARG_SECTION, section);
        fragment.setArguments(arguments);

        return fragment;
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        TextView title =
                view.findViewById(R.id.tvAdminSectionTitle);

        TextView description =
                view.findViewById(
                        R.id.tvAdminSectionDescription
                );

        TextView statusTitle =
                view.findViewById(
                        R.id.tvAdminSectionStatusTitle
                );

        TextView statusDetail =
                view.findViewById(
                        R.id.tvAdminSectionStatusDetail
                );

        Bundle arguments = getArguments();

        String section = arguments == null
                ? ""
                : arguments.getString(ARG_SECTION, "");

        switch (section) {
            case USERS:
                title.setText("Quản lý người dùng");
                description.setText(
                        "Tìm kiếm và quản lý tài khoản người học."
                );
                statusTitle.setText("Chưa có danh sách");
                statusDetail.setText(
                        "Danh sách tài khoản sẽ hiển thị khi "
                                + "kết nối máy chủ."
                );
                break;

            case TOPICS:
                title.setText("Chủ đề luyện nói");
                description.setText(
                        "Quản lý các tình huống luyện hội thoại."
                );
                statusTitle.setText("Chưa có danh sách");
                statusDetail.setText(
                        "Danh sách chủ đề và thao tác quản lý "
                                + "sẽ được bổ sung ở bước tiếp theo."
                );
                break;

            case DECKS:
                title.setText("Bộ thẻ hệ thống");
                description.setText(
                        "Quản lý các bộ từ vựng dành cho người học."
                );
                statusTitle.setText("Chưa có danh sách");
                statusDetail.setText(
                        "Danh sách bộ thẻ hệ thống sẽ hiển thị "
                                + "khi kết nối máy chủ."
                );
                break;

            case ACCOUNT:
                title.setText("Tài khoản quản trị");
                description.setText(
                        "Thông tin và thiết lập tài khoản admin."
                );
                statusTitle.setText("Chưa có thông tin");
                statusDetail.setText(
                        "Thông tin tài khoản sẽ hiển thị sau "
                                + "khi đăng nhập thành công."
                );
                break;

            default:
                title.setText("Quản trị");
                description.setText("");
                statusTitle.setText("Không tìm thấy mục");
                statusDetail.setText(
                        "Vui lòng quay lại trang tổng quan."
                );
                break;
        }

        view.findViewById(R.id.btnBackAdminDashboard)
                .setOnClickListener(button -> {
                    if (!isAdded()
                            || !isResumed()
                            || getParentFragmentManager()
                            .isStateSaved()) {
                        return;
                    }

                    getParentFragmentManager().popBackStack();
                });
    }
}