package com.example.tteonam.auth;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.tteonam.data.AuthRepository;
import com.example.tteonam.util.TnLog;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

public class HomeViewModel extends AndroidViewModel {
    public enum Status {
        LOADING,
        READY,
        NEEDS_PROFILE,
        SAVING_PROFILE,
        ERROR,
        SIGNED_OUT
    }

    public static final class UiState {
        @NonNull public final Status status;
        @Nullable public final String nickname;
        @Nullable public final String message;
        @Nullable public final String nicknameError;

        UiState(@NonNull Status status, @Nullable String nickname,
                @Nullable String message, @Nullable String nicknameError) {
            this.status = status;
            this.nickname = nickname;
            this.message = message;
            this.nicknameError = nicknameError;
        }

        static UiState of(@NonNull Status status) {
            return new UiState(status, null, null, null);
        }
    }

    private final AuthRepository repository;
    private final MutableLiveData<UiState> state = new MutableLiveData<>(UiState.of(Status.LOADING));

    // 로그아웃 이전에 시작된 요청의 응답을 무시한다.
    private int generation = 0;

    private boolean started = false;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        repository = new AuthRepository(application);
    }

    @NonNull
    public LiveData<UiState> getState() {
        return state;
    }

    public void start() {
        if (started) {
            return;
        }

        started = true;
        loadProfile();
    }

    public void retry() {
        if (currentStatus() == Status.ERROR) {
            loadProfile();
        }
    }

    private void loadProfile() {
        FirebaseUser user = repository.getCurrentUser();
        if (user == null) {
            state.setValue(UiState.of(Status.SIGNED_OUT));
            return;
        }
        final String uid = user.getUid();
        final int requestGeneration = generation;
        state.setValue(UiState.of(Status.LOADING));

        repository.loadProfile(uid).addOnCompleteListener(task -> {
            if (isStale(requestGeneration, uid)) {
                return;
            }
            if (!task.isSuccessful()) {
                TnLog.w(TnLog.HOME, "프로필 불러오기 실패", task.getException());
                String message = AuthErrorMessages.forProfile(task.getException());
                state.setValue(new UiState(Status.ERROR, null, message, null));
                return;
            }

            DocumentSnapshot snapshot = task.getResult();
            if (snapshot.exists()) {
                String nickname = snapshot.getString(AuthRepository.FIELD_DISPLAY_NAME);
                TnLog.i(TnLog.HOME, "프로필 확인 완료");
                state.setValue(new UiState(Status.READY, nickname, null, null));
            } else if (snapshot.getMetadata().isFromCache()) {
                TnLog.w(TnLog.HOME, "오프라인: 첫 로그인 프로필 확인 불가");
                state.setValue(new UiState(Status.ERROR, null,
                        "처음 로그인할 때는 인터넷 연결이 필요해요. 연결 후 다시 시도해 주세요.",
                        null));
            } else {
                TnLog.i(TnLog.HOME, "프로필 없음 → 닉네임 입력");
                state.setValue(UiState.of(Status.NEEDS_PROFILE));
            }
        });
    }

    public void saveProfile(@Nullable String rawNickname) {
        if (currentStatus() != Status.NEEDS_PROFILE) {
            return;
        }
        String nickname = AuthValidator.normalizeNickname(rawNickname);
        String error = AuthValidator.validateNickname(nickname);
        if (error != null) {
            TnLog.d(TnLog.HOME, "닉네임 입력 오류: " + error);
            state.setValue(new UiState(Status.NEEDS_PROFILE, null, null, error));
            return;
        }

        FirebaseUser user = repository.getCurrentUser();
        if (user == null) {
            state.setValue(UiState.of(Status.SIGNED_OUT));
            return;
        }
        final String uid = user.getUid();
        final int requestGeneration = generation;
        state.setValue(UiState.of(Status.SAVING_PROFILE));

        repository.createProfileIfMissing(uid, nickname).addOnCompleteListener(task -> {
            if (isStale(requestGeneration, uid)) {
                return;
            }
            if (task.isSuccessful()) {
                loadProfile();
            } else {
                TnLog.w(TnLog.HOME, "프로필 저장 실패", task.getException());
                String message = AuthErrorMessages.forProfile(task.getException());
                state.setValue(new UiState(Status.NEEDS_PROFILE, null, message, null));
            }
        });
    }

    public void logout() {
        generation++;
        repository.signOut();
        state.setValue(UiState.of(Status.SIGNED_OUT));
    }

    private boolean isStale(int requestGeneration, @NonNull String requestUid) {
        FirebaseUser now = repository.getCurrentUser();
        return requestGeneration != generation
                || now == null
                || !now.getUid().equals(requestUid);
    }

    @NonNull
    private Status currentStatus() {
        UiState current = state.getValue();
        return current == null ? Status.LOADING : current.status;
    }
}
