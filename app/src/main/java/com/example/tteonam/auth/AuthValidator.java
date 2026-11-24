package com.example.tteonam.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

public final class AuthValidator {
    public static final int NICKNAME_MAX_LENGTH = 30;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private AuthValidator() {
    }

    @NonNull
    public static String normalizeEmail(@Nullable String raw) {
        return raw == null ? "" : raw.trim();
    }

    @Nullable
    public static String validateEmail(@NonNull String email) {
        if (email.isEmpty()) {
            return "이메일을 입력해 주세요.";
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return "이메일 형식이 올바르지 않아요.";
        }
        return null;
    }

    @Nullable
    public static String validateLoginPassword(@Nullable String password) {
        if (password == null || password.isEmpty()) {
            return "비밀번호를 입력해 주세요.";
        }
        return null;
    }

    public static final int SIGN_UP_PASSWORD_MIN_LENGTH = 8;

    public static boolean hasMinLength(@Nullable String password) {
        return password != null && password.length() >= SIGN_UP_PASSWORD_MIN_LENGTH;
    }

    public static boolean hasAsciiLetter(@Nullable String password) {
        if (password == null) {
            return false;
        }
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasDigit(@Nullable String password) {
        if (password == null) {
            return false;
        }
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (c >= '0' && c <= '9') {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static String validateSignUpPassword(@Nullable String password) {
        if (password == null || password.isEmpty()) {
            return "비밀번호를 입력해 주세요.";
        }
        if (!hasMinLength(password) || !hasAsciiLetter(password) || !hasDigit(password)) {
            return "비밀번호는 8자 이상이며 영문과 숫자를 모두 포함해야 해요.";
        }
        return null;
    }

    @Nullable
    public static String validatePasswordConfirm(@Nullable String password,
                                                 @Nullable String confirm) {
        if (confirm == null || confirm.isEmpty()) {
            return "비밀번호를 한 번 더 입력해 주세요.";
        }
        if (!confirm.equals(password)) {
            return "비밀번호가 일치하지 않아요.";
        }
        return null;
    }

    @NonNull
    public static String normalizeNickname(@Nullable String raw) {
        return raw == null ? "" : raw.trim();
    }

    @Nullable
    public static String validateNickname(@NonNull String nickname) {
        if (nickname.isEmpty()) {
            return "닉네임을 입력해 주세요.";
        }
        if (nickname.codePointCount(0, nickname.length()) > NICKNAME_MAX_LENGTH) {
            return "닉네임은 " + NICKNAME_MAX_LENGTH + "자 이하로 입력해 주세요.";
        }
        return null;
    }
}
