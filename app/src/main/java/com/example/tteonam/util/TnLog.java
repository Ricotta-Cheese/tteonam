package com.example.tteonam.util;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.tteonam.BuildConfig;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.functions.FirebaseFunctionsException;
import com.google.firebase.storage.StorageException;

// 팀 공통 로그. 모든 태그는 "tn"으로 시작한다.
//   tn-<영역>  예) tn-Upload, tn-Camera
// Logcat 필터: 전체는 tag:tn-  /  한 영역만 보려면 tag:tn-Upload
//
// 남기지 않는 것: 이메일·비밀번호·토큰·다운로드 URL·정확한 위도/경도.
// uid는 shortId()로 앞부분만 남긴다.
public final class TnLog {
    public static final String PREFIX = "tn";

    // 영역 이름 (태그 뒷부분)
    public static final String APP = "App";
    public static final String SCREEN = "Screen";
    public static final String AUTH = "Auth";
    public static final String HOME = "Home";
    public static final String CAMERA = "Camera";
    public static final String SHARE = "Share";
    public static final String UPLOAD = "Upload";
    public static final String TRIP = "Trip";
    public static final String GROUP = "Group";
    public static final String JOIN = "Join";
    public static final String IMAGE = "Image";

    // Android 7 이하는 태그 23자 제한이 있다.
    private static final int MAX_TAG_LENGTH = 23;

    private TnLog() {
    }

    @NonNull
    public static String tag(@Nullable String area) {
        String tag = (area == null || area.isEmpty()) ? PREFIX : PREFIX + "-" + area;
        return tag.length() > MAX_TAG_LENGTH ? tag.substring(0, MAX_TAG_LENGTH) : tag;
    }

    // 개발 중에만 보는 상세 로그 (릴리스 빌드에서는 남기지 않는다)
    public static void d(@NonNull String area, @NonNull String message) {
        if (BuildConfig.DEBUG) Log.d(tag(area), message);
    }

    public static void d(@NonNull String area, @NonNull String message, @Nullable Throwable error) {
        if (BuildConfig.DEBUG) Log.d(tag(area), message, error);
    }

    // 화면 이동·사용자 행동·서버 처리 결과 같은 주요 흐름
    public static void i(@NonNull String area, @NonNull String message) {
        Log.i(tag(area), message);
    }

    // 다시 시도하면 되는 실패, 예상 가능한 오류
    public static void w(@NonNull String area, @NonNull String message) {
        Log.w(tag(area), message);
    }

    public static void w(@NonNull String area, @NonNull String message, @Nullable Throwable error) {
        Log.w(tag(area), message + describe(error), error);
    }

    // 예상하지 못한 오류
    public static void e(@NonNull String area, @NonNull String message, @Nullable Throwable error) {
        Log.e(tag(area), message + describe(error), error);
    }

    // 서버 호출 등 Task의 시작·성공·실패와 걸린 시간을 남긴다. 받은 Task를 그대로 돌려준다.
    //   Tasks.await(TnLog.track(TnLog.TRIP, "createTrip", call))
    @NonNull
    public static <T> Task<T> track(@NonNull String area, @NonNull String name, @NonNull Task<T> task) {
        long started = SystemClock.elapsedRealtime();
        d(area, name + " 시작");
        task.addOnCompleteListener(t -> {
            long ms = SystemClock.elapsedRealtime() - started;
            if (t.isCanceled()) {
                w(area, name + " 취소 (" + ms + "ms)");
            } else if (t.isSuccessful()) {
                i(area, name + " 성공 (" + ms + "ms)");
            } else {
                w(area, name + " 실패 (" + ms + "ms)", t.getException());
            }
        });
        return task;
    }

    // uid 같은 식별자는 앞 6자만 남긴다.
    @NonNull
    public static String shortId(@Nullable String id) {
        if (id == null) return "null";
        return id.length() <= 6 ? id : id.substring(0, 6) + "…";
    }

    // Firebase 오류 코드를 메시지 뒤에 붙인다. 예) " [functions:FAILED_PRECONDITION]"
    @NonNull
    static String describe(@Nullable Throwable error) {
        Throwable e = error;
        while (e instanceof java.util.concurrent.ExecutionException && e.getCause() != null) {
            e = e.getCause();
        }
        if (e == null) return "";
        if (e instanceof FirebaseFunctionsException) {
            return " [functions:" + ((FirebaseFunctionsException) e).getCode() + "]";
        }
        if (e instanceof FirebaseFirestoreException) {
            return " [firestore:" + ((FirebaseFirestoreException) e).getCode() + "]";
        }
        if (e instanceof StorageException) {
            return " [storage:" + ((StorageException) e).getErrorCode() + "]";
        }
        return " [" + e.getClass().getSimpleName() + "]";
    }
}
