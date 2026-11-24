package com.example.tteonam.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// 기기에 기억해 두는 "지금 진행 중인 여행"과 마지막으로 발급한 초대 코드.
// 서버의 멤버십이 기준이며, 여기 값은 화면을 빨리 그리기 위한 캐시다.
public final class CurrentTripStore {
    private static final String PREFS = "current_trip";

    private final SharedPreferences prefs;
    private final String uid;

    public CurrentTripStore(@NonNull Context context, @NonNull String uid) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.uid = uid;
    }

    @Nullable
    public String getTripId() {
        return prefs.getString(uid + ".tripId", null);
    }

    public void setTripId(@Nullable String tripId) {
        prefs.edit().putString(uid + ".tripId", tripId).apply();
    }

    public void saveInvite(@NonNull String tripId, @NonNull String code, long expiresAt) {
        prefs.edit()
                .putString(uid + ".invite." + tripId + ".code", code)
                .putLong(uid + ".invite." + tripId + ".expiresAt", expiresAt)
                .apply();
    }

    @Nullable
    public String getInviteCode(@NonNull String tripId) {
        return prefs.getString(uid + ".invite." + tripId + ".code", null);
    }

    public long getInviteExpiresAt(@NonNull String tripId) {
        return prefs.getLong(uid + ".invite." + tripId + ".expiresAt", 0L);
    }
}
