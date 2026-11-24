package com.example.tteonam.auth;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.tteonam.BuildConfig;
import com.example.tteonam.data.AuthRepository;
import com.google.firebase.auth.FirebaseUser;

public class SignUpViewModel extends AndroidViewModel {
    private static final String TAG = "SignUpViewModel";

    public enum Status {
        IDLE,
        CREATING_ACCOUNT,
        SAVING_PROFILE,
        PROFILE_FAILED,
        SUCCESS
    }

    public static final class UiState {
        @NonNull public final Status status;
        @Nullable public final String message;
        @Nullable public final String email;

        UiState(@NonNull Status status, @Nullable String message, @Nullable String email) {
            this.status = status;
            this.message = message;
            this.email = email;
        }

        public boolean isBusy() {
            return status == Status.CREATING_ACCOUNT || status == Status.SAVING_PROFILE;
        }
    }

    private final AuthRepository repository;
    private final MutableLiveData<UiState> state =
            new MutableLiveData<>(new UiState(Status.IDLE, null, null));

    @Nullable private String pendingUid;
    @Nullable private String pendingNickname;
    @Nullable private String pendingEmail;

    public SignUpViewModel(@NonNull Application application) {
        super(application);
        repository = new AuthRepository(application);
    }

    @NonNull
    public LiveData<UiState> getState() {
        return state;
    }

    public void signUp(@Nullable String rawNickname, @Nullable String rawEmail,
                       @Nullable String password, @Nullable String confirm,
                       boolean termsAccepted) {
        if (currentStatus() != Status.IDLE) {
            return;
        }
        String nickname = AuthValidator.normalizeNickname(rawNickname);
        String email = AuthValidator.normalizeEmail(rawEmail);
        String error = firstError(
                AuthValidator.validateNickname(nickname),
                AuthValidator.validateEmail(email),
                AuthValidator.validateSignUpPassword(password),
                AuthValidator.validatePasswordConfirm(password, confirm),
                termsAccepted ? null : "필수 약관에 모두 동의해 주세요.");
        if (error != null) {
            state.setValue(new UiState(Status.IDLE, error, null));
            return;
        }

        state.setValue(new UiState(Status.CREATING_ACCOUNT, null, null));
        // TODO: 종료된 요청의 콜백을 무시한다.
        repository.signUp(email, password).addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult().getUser() == null) {
                logDebug("계정 생성 실패", task.getException());
                state.setValue(new UiState(Status.IDLE,
                        AuthErrorMessages.forSignUp(task.getException()), null));
                return;
            }

            pendingUid = task.getResult().getUser().getUid();
            pendingNickname = nickname;
            pendingEmail = email;
            saveProfile();
        });
    }

    public void retryProfile() {
        if (currentStatus() == Status.PROFILE_FAILED) {
            saveProfile();
        }
    }

    private void saveProfile() {
        final String uid = pendingUid;
        final String nickname = pendingNickname;
        FirebaseUser user = repository.getCurrentUser();
        if (uid == null || nickname == null || user == null || !uid.equals(user.getUid())) {
            state.setValue(new UiState(Status.IDLE,
                    "가입 진행 상태를 확인할 수 없어요. 로그인 화면에서 로그인해 주세요.", null));
            return;
        }

        state.setValue(new UiState(Status.SAVING_PROFILE, null, null));
        repository.createProfileIfMissing(uid, nickname).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // TODO: 로그아웃 전 현재 UID를 재확인한다.
                // 가입 시 생성된 세션을 종료하고 로그인 화면으로 돌아간다.
                repository.signOut();
                String email = pendingEmail;
                clearPending();
                state.setValue(new UiState(Status.SUCCESS, null, email));
            } else {
                logDebug("프로필 저장 실패", task.getException());

                state.setValue(new UiState(Status.PROFILE_FAILED,
                        "계정은 만들어졌지만 닉네임 저장에 실패했어요. "
                                + AuthErrorMessages.forProfile(task.getException()),
                        null));
            }
        });
    }

    @Override
    protected void onCleared() {
        FirebaseUser user = repository.getCurrentUser();
        if (pendingUid != null && user != null && pendingUid.equals(user.getUid())) {
            repository.signOut();
        }
    }

    private void clearPending() {
        pendingUid = null;
        pendingNickname = null;
        pendingEmail = null;
    }

    @Nullable
    private static String firstError(String... errors) {
        for (String error : errors) {
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    private static void logDebug(String message, @Nullable Exception error) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, message, error);
        }
    }

    @NonNull
    private Status currentStatus() {
        UiState current = state.getValue();
        return current == null ? Status.IDLE : current.status;
    }
}
