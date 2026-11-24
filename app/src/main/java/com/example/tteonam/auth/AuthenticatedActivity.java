package com.example.tteonam.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.tteonam.LoginActivity;
import com.example.tteonam.data.FirebaseProvider;
import com.google.firebase.auth.FirebaseAuth;

public abstract class AuthenticatedActivity extends AppCompatActivity {
    private FirebaseAuth auth;

    private boolean redirectingToLogin = false;

    // 로컬 세션 감시. 서버 계정 상태의 즉시 검증은 포함하지 않는다.
    private final FirebaseAuth.AuthStateListener authStateListener = firebaseAuth -> {
        if (firebaseAuth.getCurrentUser() == null) {
            goToLogin();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseProvider.get(this).getAuth();
        if (auth.getCurrentUser() == null) {
            goToLogin();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        auth.addAuthStateListener(authStateListener);
    }

    @Override
    protected void onStop() {
        auth.removeAuthStateListener(authStateListener);
        super.onStop();
    }

    protected boolean isRedirectingToLogin() {
        return redirectingToLogin;
    }

    protected final void goToLogin() {
        if (redirectingToLogin) {
            return;
        }
        redirectingToLogin = true;
        Intent intent = new Intent(this, LoginActivity.class);

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
