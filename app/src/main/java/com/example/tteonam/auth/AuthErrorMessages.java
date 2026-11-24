package com.example.tteonam.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.firestore.FirebaseFirestoreException;

public final class AuthErrorMessages {
    private AuthErrorMessages() {
    }

    @NonNull
    public static String forSignIn(@Nullable Exception error) {
        if (error instanceof FirebaseNetworkException) {
            return "인터넷 연결을 확인한 뒤 다시 시도해 주세요.";
        }

        if (error instanceof FirebaseTooManyRequestsException) {
            return "시도가 너무 많아요. 잠시 후 다시 시도해 주세요.";
        }

        if (error instanceof FirebaseAuthInvalidUserException) {
            String code = ((FirebaseAuthInvalidUserException) error).getErrorCode();
            if ("ERROR_USER_DISABLED".equals(code)) {
                return "사용이 중지된 계정이에요. 관리자에게 문의해 주세요.";
            }

            return wrongCredentials();
        }

        if (error instanceof FirebaseAuthInvalidCredentialsException) {
            return wrongCredentials();
        }

        if (error instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) error).getErrorCode();
            if ("ERROR_OPERATION_NOT_ALLOWED".equals(code)) {
                return "지금은 이메일 로그인을 사용할 수 없어요. 관리자에게 문의해 주세요.";
            }
        }
        return unknown();
    }

    @NonNull
    public static String forSignUp(@Nullable Exception error) {
        if (error instanceof FirebaseNetworkException) {
            return "인터넷 연결을 확인한 뒤 다시 시도해 주세요.";
        }
        if (error instanceof FirebaseTooManyRequestsException) {
            return "시도가 너무 많아요. 잠시 후 다시 시도해 주세요.";
        }

        if (error instanceof FirebaseAuthUserCollisionException) {
            return "이미 가입된 이메일이에요. 로그인 화면에서 로그인해 주세요.";
        }

        if (error instanceof FirebaseAuthWeakPasswordException) {
            return "더 안전한 비밀번호를 사용해 주세요.";
        }
        if (error instanceof FirebaseAuthInvalidCredentialsException) {
            return "이메일 형식이 올바르지 않아요.";
        }
        if (error instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) error).getErrorCode();
            if ("ERROR_OPERATION_NOT_ALLOWED".equals(code)) {
                return "지금은 이메일 가입을 사용할 수 없어요. 관리자에게 문의해 주세요.";
            }
        }
        return unknown();
    }

    @NonNull
    public static String forProfile(@Nullable Exception error) {
        if (error instanceof FirebaseNetworkException) {
            return "인터넷 연결을 확인한 뒤 다시 시도해 주세요.";
        }
        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) error).getCode();
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                return "서버에 연결할 수 없어요. 인터넷 연결을 확인한 뒤 다시 시도해 주세요.";
            }
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    || code == FirebaseFirestoreException.Code.UNAUTHENTICATED) {
                return "프로필에 접근할 권한이 없어요. 로그아웃한 뒤 다시 로그인해 주세요.";
            }
        }
        return unknown();
    }

    @NonNull
    private static String wrongCredentials() {
        return "이메일과 비밀번호를 확인해 주세요.";
    }

    @NonNull
    private static String unknown() {
        return "문제가 발생했어요. 잠시 후 다시 시도해 주세요.";
    }
}
