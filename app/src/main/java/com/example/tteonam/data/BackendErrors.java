package com.example.tteonam.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.functions.FirebaseFunctionsException;
import com.google.firebase.storage.StorageException;

import java.util.concurrent.ExecutionException;

// 서버 오류를 사용자에게 보여 줄 한국어 문장으로 바꾼다.
// 오류 코드는 FirebaseControl/docs/backend.md의 callable 오류 목록을 따른다.
public final class BackendErrors {
    private BackendErrors() {
    }

    // 화면에서 직접 쓰는 오류(사진이 없음 등)를 표시할 때 사용한다.
    public static final class UserFacingException extends Exception {
        public UserFacingException(@NonNull String message) {
            super(message);
        }
    }

    @NonNull
    public static Throwable unwrap(@Nullable Throwable error) {
        Throwable current = error;
        while (current instanceof ExecutionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null ? new Exception("unknown") : current;
    }

    // 다시 시도해도 결과가 같은 오류인지 (권한·입력 오류)
    public static boolean isPermanent(@Nullable Throwable error) {
        Throwable e = unwrap(error);
        if (e instanceof FirebaseFunctionsException) {
            switch (((FirebaseFunctionsException) e).getCode()) {
                case PERMISSION_DENIED:
                case INVALID_ARGUMENT:
                case UNAUTHENTICATED:
                    return true;
                default:
                    return false;
            }
        }
        if (e instanceof FirebaseFirestoreException) {
            return ((FirebaseFirestoreException) e).getCode()
                    == FirebaseFirestoreException.Code.PERMISSION_DENIED;
        }
        return e instanceof UserFacingException;
    }

    @NonNull
    public static String message(@Nullable Throwable error) {
        Throwable e = unwrap(error);
        if (e instanceof UserFacingException) {
            return e.getMessage();
        }
        if (e instanceof FirebaseNetworkException) {
            return "인터넷 연결을 확인한 뒤 다시 시도해 주세요.";
        }
        if (e instanceof FirebaseFunctionsException) {
            switch (((FirebaseFunctionsException) e).getCode()) {
                case UNAUTHENTICATED:
                    return "로그인이 필요해요. 다시 로그인해 주세요.";
                case PERMISSION_DENIED:
                    return "이 여행에 대한 권한이 없어요.";
                case INVALID_ARGUMENT:
                    return "입력한 내용을 다시 확인해 주세요.";
                case FAILED_PRECONDITION:
                    return "아직 처리할 수 없는 상태예요. 잠시 후 다시 시도해 주세요.";
                case NOT_FOUND:
                    return "초대 코드가 없거나 만료됐어요.";
                case ALREADY_EXISTS:
                    return "이미 같은 ID의 여행이 있어요. 다시 시도해 주세요.";
                case RESOURCE_EXHAUSTED:
                    return "요청이 너무 잦아요. 잠시 후 다시 시도해 주세요.";
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    return "서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.";
                default:
                    return "서버 오류가 발생했어요. 잠시 후 다시 시도해 주세요.";
            }
        }
        if (e instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) e).getCode()) {
                case PERMISSION_DENIED:
                    return "이 여행에 대한 권한이 없어요.";
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    return "인터넷 연결을 확인한 뒤 다시 시도해 주세요.";
                default:
                    return "데이터를 처리하지 못했어요. 잠시 후 다시 시도해 주세요.";
            }
        }
        if (e instanceof StorageException) {
            int code = ((StorageException) e).getErrorCode();
            if (code == StorageException.ERROR_NOT_AUTHORIZED) {
                return "사진을 올릴 권한이 없어요.";
            }
            if (code == StorageException.ERROR_RETRY_LIMIT_EXCEEDED) {
                return "인터넷 연결을 확인한 뒤 다시 시도해 주세요.";
            }
            return "사진 파일을 처리하지 못했어요. 다시 시도해 주세요.";
        }
        return "문제가 발생했어요. 다시 시도해 주세요.";
    }

    @NonNull
    public static String forJoin(@Nullable Throwable error) {
        Throwable e = unwrap(error);
        if (e instanceof FirebaseFunctionsException) {
            switch (((FirebaseFunctionsException) e).getCode()) {
                case RESOURCE_EXHAUSTED:
                    return "참여 시도가 너무 많아요. 10분 뒤에 다시 시도해 주세요.";
                case FAILED_PRECONDITION:
                    return "프로필(닉네임)을 먼저 만들어 주세요.";
                default:
                    break;
            }
        }
        return message(e);
    }

    @NonNull
    public static String forCreateTrip(@Nullable Throwable error) {
        Throwable e = unwrap(error);
        if (e instanceof FirebaseFunctionsException
                && ((FirebaseFunctionsException) e).getCode()
                == FirebaseFunctionsException.Code.FAILED_PRECONDITION) {
            return "프로필(닉네임)을 먼저 만들어 주세요.";
        }
        return message(e);
    }
}
