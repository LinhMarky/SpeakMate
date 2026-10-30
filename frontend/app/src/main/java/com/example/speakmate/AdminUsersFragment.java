package com.example.speakmate;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.speakmate.api.AdminApi;
import com.example.speakmate.auth.SessionManager;
import com.example.speakmate.ui.AdminFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import org.json.*;

public class AdminUsersFragment extends AdminFragment {
    private final List<JSONObject> users = new ArrayList<>();
    private AdminApi admin;
    private boolean busy;
    private int nextPage = 1, pages;
    private long total;
    private String loadedSearch = "", loadedActive = "";

    public AdminUsersFragment() { super(R.layout.fragment_admin_users); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        if (!hasAdminSession()) return;
        busy = false;
        users.clear();
        admin = new AdminApi(SessionManager.get(requireContext()));
        view.findViewById(R.id.btnBackAdminUsers).setOnClickListener(v -> getParentFragmentManager().popBackStack());
        Spinner filter = view.findViewById(R.id.spAdminUserStatus);
        filter.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Tất cả trạng thái", "Đang hoạt động", "Đã khóa"}));
        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int position, long id) { load(1); }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        ((EditText) view.findViewById(R.id.etAdminUserSearch)).setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH && action != EditorInfo.IME_ACTION_DONE) return false;
            androidx.core.view.WindowCompat.getInsetsController(requireActivity().getWindow(), view)
                    .hide(androidx.core.view.WindowInsetsCompat.Type.ime());
            load(1);
            return true;
        });
        view.findViewById(R.id.tvAdminUserListCount).setOnClickListener(v -> load(1));
    }

    private void load(int page) {
        if (busy || getView() == null) return;
        busy = true;
        if (page == 1) {
            loadedSearch = ((EditText) requireView().findViewById(R.id.etAdminUserSearch)).getText().toString().trim();
            int selected = ((Spinner) requireView().findViewById(R.id.spAdminUserStatus)).getSelectedItemPosition();
            loadedActive = selected == 0 ? "" : String.valueOf(selected == 1);
        }
        String query = loadedSearch, active = loadedActive;
        status("Đang tải tài khoản…", null);
        render();
        call(() -> admin.users(page, query, active), data -> {
            busy = false;
            if (page == 1) users.clear();
            JSONArray rows = data.optJSONArray("result");
            if (rows != null) for (int i = 0; i < rows.length(); i++) users.add(rows.optJSONObject(i));
            JSONObject meta = data.optJSONObject("meta");
            pages = meta == null ? page : meta.optInt("pages");
            total = meta == null ? users.size() : meta.optLong("total");
            nextPage = page + 1;
            render();
            status(nextPage <= pages ? "Chạm để tải thêm" : "Nhập tên/email rồi bấm Tìm. Chạm số lượng để làm mới.",
                    nextPage <= pages ? v -> load(nextPage) : null);
        }, error -> {
            busy = false; render();
            status(error.getMessage() + " Chạm để thử lại.", v -> load(page));
            showAdminError(error);
        });
    }

    private void render() {
        LinearLayout list = requireView().findViewById(R.id.adminUserListContainer);
        list.removeAllViews();
        ((TextView) requireView().findViewById(R.id.tvAdminUserListCount)).setText("Đã tải " + users.size() + "/" + total + " tài khoản");
        for (JSONObject user : users) {
            if (user == null) continue;
            View item = getLayoutInflater().inflate(R.layout.item_admin_user, list, false);
            ((TextView) item.findViewById(R.id.tvAdminUserName)).setText(user.optString("name"));
            ((TextView) item.findViewById(R.id.tvAdminUserEmail)).setText(user.optString("email"));
            boolean active = user.optBoolean("active");
            ((TextView) item.findViewById(R.id.tvAdminUserStatus)).setText(user.optString("role") + " · " + (active ? "Đang hoạt động" : "Đã khóa"));
            Button toggle = item.findViewById(R.id.btnAdminUserToggleStatus);
            toggle.setText(active ? "Khóa tài khoản" : "Mở khóa tài khoản");
            toggle.setEnabled(!busy && !"ADMIN".equals(user.optString("role")));
            toggle.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(active ? "Khóa tài khoản?" : "Mở khóa tài khoản?")
                    .setMessage(user.optString("email") + "\nPhiên của người học sẽ bị thu hồi. Lịch sử được giữ nguyên.")
                    .setNegativeButton("Hủy", null).setPositiveButton("Xác nhận", (d, which) -> toggle(user, !active)).show());
            item.findViewById(R.id.btnAdminUserDetail).setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(user.optString("name")).setMessage("Email: " + user.optString("email")
                            + "\nVai trò: " + user.optString("role") + "\nTrình độ: " + user.optString("level")
                            + "\nGoogle: " + (user.optBoolean("googleLinked") ? "Đã liên kết" : "Chưa liên kết"))
                    .setPositiveButton("Đóng", null).show());
            list.addView(item);
        }
    }

    private void toggle(JSONObject user, boolean active) {
        if (busy) return;
        busy = true;
        render();
        long id = user.optLong("id");
        call(() -> {
            JSONObject updated = admin.userActive(id, active);
            if (updated.getLong("id") != id || updated.getBoolean("active") != active) throw new JSONException("Invalid user acknowledgement");
            return updated;
        }, updated -> { busy = false; load(1); }, error -> {
            busy = false; render();
            status(error.getMessage() + " Chạm để tải lại.", v -> load(1));
            showAdminError(error);
        });
    }

    private void status(String text, View.OnClickListener listener) {
        TextView status = requireView().findViewById(R.id.tvAdminUsersStatus);
        status.setText(text);
        status.setOnClickListener(listener);
    }
}
