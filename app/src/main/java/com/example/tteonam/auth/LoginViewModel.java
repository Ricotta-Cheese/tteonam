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

public class LoginViewModel extends AndroidViewModel {
    private static final String TAG = "LoginViewModel";

    public enum Status {
        IDLE,

        LOADING,

        SUCCESS
    }

    public static final class UiState {
        @NonNull public final Status status;
        @Nullable public final String emailError;
        @Nullable public final String passwordError;
        @Nullable public final String formError;

        UiState(@NonNull Status status, @Nullable String emailError,
                @Nullable String passwordError, @Nullable String formError) {
            this.status = status;
            this.emailError = emailError;
            this.passwordError = passwordError;
            this.formError = formError;
        }

        static UiState idle() {
            return new UiState(Status.IDLE, null, null, null);
        }
    }

    private final AuthRepository repository;
    private final MutableLiveData<UiState> state = new MutableLiveData<>(UiState.idle());

    public LoginViewModel(@NonNull Application application) {
        super(application);
        repository = new AuthRepository(application);
    }

    @NonNull
    public LiveData<UiState> getState() {
        return state;
    }

    public boolean isAlreadySignedIn() {
        return repository.getCurrentUser() != null;
    }

    public void signIn(@Nullable String rawEmail, @Nullable String rawPassword) {
        UiState current = state.getValue();
        if (current != null && current.status != Status.IDLE) {
            return;
        }

        String email = AuthValidator.normalizeEmail(rawEmail);
        String password = rawPassword == null ? "" : rawPassword;
        String emailError = AuthValidator.validateEmail(email);
        String passwordError = AuthValidator.validateLoginPassword(password);
        if (emailError != null || passwordError != null) {
            state.setValue(new UiState(Status.IDLE, emailError, passwordError, null));
            return;
        }

        state.setValue(new UiState(Status.LOADING, null, null, null));
        repository.signIn(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                state.setValue(new UiState(Status.SUCCESS, null, null, null));
            } else {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "로그인 실패", task.getException());
                }
                String message = AuthErrorMessages.forSignIn(task.getException());
                state.setValue(new UiState(Status.IDLE, null, null, message));
            }
        });
    }
}
