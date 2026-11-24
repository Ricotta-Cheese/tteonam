package com.example.tteonam.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// 초대 코드는 서버(createInvite)가 만드는 6자리 숫자다. 앞자리 0도 그대로 보존한다.
public final class InviteCodes {
    public static final int LENGTH = 6;

    private InviteCodes() {
    }

    // QR·붙여넣기 입력에서 공백·하이픈을 지우고, 6자리 숫자가 아니면 null
    @Nullable
    public static String normalize(@Nullable String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[\\s-]", "");
        return isValid(digits) ? digits : null;
    }

    public static boolean isValid(@Nullable String code) {
        return code != null && code.matches("^\\d{" + LENGTH + "}$");
    }

    // 남은 시간을 "mm분 후 만료" 형식으로. 만료됐으면 "코드 만료됨"
    @NonNull
    public static String remainingText(long expiresAtMillis, long nowMillis) {
        long remain = expiresAtMillis - nowMillis;
        if (remain <= 0) return "코드 만료됨";
        long minutes = (remain + 59_999) / 60_000;   // 남은 시간을 분 단위로 올림
        return minutes + "분 후 만료";
    }
}
