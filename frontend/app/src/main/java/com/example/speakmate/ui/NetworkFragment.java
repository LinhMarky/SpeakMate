package com.example.speakmate.ui;

import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.speakmate.MainActivity;
import com.example.speakmate.api.LearningApi;
import com.example.speakmate.auth.AuthApi;
import com.example.speakmate.auth.SessionManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Gọi mạng ở nền; bỏ callback khi view cũ đã bị hủy. */
public abstract class NetworkFragment extends Fragment {

    protected LearningApi api;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService worker;
    private int generation;

    protected NetworkFragment() {
    }

    protected NetworkFragment(int layout) {
        super(layout);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        api = new LearningApi(SessionManager.get(requireContext()));
        worker = Executors.newFixedThreadPool(2);
        generation++;
    }

    protected boolean requiresAuthentication() {
        return true;
    }

    protected interface Work<T> {
        T run() throws Exception;
    }

    protected <T> void call(Work<T> work, Consumer<T> success,
            Consumer<AuthApi.ApiException> failure) {
        int expected = generation;
        worker.execute(() -> {
            try {
                T result = work.run();
                main.post(() -> {
                    if (expected == generation && getView() != null) success.accept(result);
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (expected != generation || getView() == null) return;
                    AuthApi.ApiException problem = error instanceof AuthApi.ApiException
                            ? (AuthApi.ApiException) error
                            : new AuthApi.ApiException(0, "INVALID_RESPONSE", "Chưa thể đọc dữ liệu. Hãy tải lại.");
                    if (requiresAuthentication() && (problem.status == 401
                            || "ACCOUNT_DISABLED".equals(problem.code))) {
                        ((MainActivity) requireActivity()).onSessionExpired();
                    } else {
                        failure.accept(problem);
                    }
                });
            }
        });
    }

    @Override
    public void onDestroyView() {
        generation++;
        if (worker != null) worker.shutdownNow();
        super.onDestroyView();
    }
}
