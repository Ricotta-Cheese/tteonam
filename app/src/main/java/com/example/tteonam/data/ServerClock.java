package com.example.tteonam.data;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.example.tteonam.util.TnLog;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;

// 기기 시계가 틀려도(예: 스냅샷으로 켠 AVD가 며칠 늦음) 앱이 동작하도록 서버 시각을 추정한다.
//
// 기준: 방금 새로 발급받은 로그인 토큰의 발급 시각(iat, 서버 시계).
//   - 초대 코드 남은 시간, 촬영 시각 등은 now()를 쓴다.
//   - 기기 시계가 늦으면 Auth SDK가 만료된 토큰을 계속 쓰므로, 서버 호출 전에
//     ensureFreshToken()으로 서버 시각 기준 만료가 가까운 토큰을 새로 받는다.
public final class ServerClock {
    // 만료 5분 전이면 새로 받는다.
    private static final long REFRESH_MARGIN_MS = 5 * 60_000L;
    // 1분 넘게 차이 나면 로그로 알린다.
    private static final long WARN_SKEW_MS = 60_000L;

    private static volatile long offsetMs = 0L;   // 서버 시각 - 기기 시각
    private static volatile boolean known = false;

    private ServerClock() {
    }

    // 서버 기준 현재 시각(ms). 아직 맞춰 보지 못했으면 기기 시각 그대로.
    public static long now() {
        return System.currentTimeMillis() + offsetMs;
    }

    public static long offsetMs() {
        return offsetMs;
    }

    public static boolean isKnown() {
        return known;
    }

    private static final java.util.concurrent.ExecutorService SYNC =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    // 화면을 열 때 백그라운드에서 한 번 맞춰 둔다. 실패해도 다음 서버 호출 때 다시 시도한다.
    public static void syncInBackground(@NonNull FirebaseAuth auth) {
        if (known || auth.getCurrentUser() == null) return;
        SYNC.execute(() -> {
            try {
                ensureFreshToken(auth);
            } catch (Exception e) {
                TnLog.d(TnLog.APP, "서버 시각 확인 실패", e);
            }
        });
    }

    // 로그인 상태에서 서버 호출 전에 부른다(백그라운드 스레드).
    // 처음 한 번은 토큰을 새로 받아 시계 차이를 잰다.
    @WorkerThread
    public static void ensureFreshToken(@NonNull FirebaseAuth auth) throws Exception {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;
        if (known) {
            GetTokenResult cached = Tasks.await(user.getIdToken(false));
            long expiresAt = cached.getExpirationTimestamp() * 1000L;
            if (expiresAt - now() > REFRESH_MARGIN_MS) return;
            TnLog.d(TnLog.APP, "로그인 토큰 만료 임박(서버 시각 기준) → 새로 받기");
        }
        long before = System.currentTimeMillis();
        GetTokenResult fresh = Tasks.await(user.getIdToken(true));
        long after = System.currentTimeMillis();
        observe(fresh.getIssuedAtTimestamp() * 1000L, (before + after) / 2);
    }

    // 서버가 발급한 시각과 그때의 기기 시각으로 차이를 잰다. iat는 초 단위라 ±1초 오차가 있다.
    static void observe(long serverIssuedAtMs, long deviceMs) {
        long offset = serverIssuedAtMs - deviceMs;
        // 1초 이내 차이는 iat 반올림 오차로 보고 0으로 둔다.
        offsetMs = Math.abs(offset) <= 1_000L ? 0L : offset;
        boolean first = !known;
        known = true;
        if (Math.abs(offsetMs) > WARN_SKEW_MS) {
            TnLog.w(TnLog.APP, "기기 시계가 서버와 " + (offsetMs / 60_000L)
                    + "분 차이나요. 서버 시각 기준으로 보정해 동작합니다.");
        } else if (first) {
            TnLog.d(TnLog.APP, "기기 시계 정상 (차이 " + offsetMs + "ms)");
        }
    }

    // 테스트용
    static void reset() {
        offsetMs = 0L;
        known = false;
    }
}
