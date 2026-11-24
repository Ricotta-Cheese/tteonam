package com.example.tteonam.data;

import androidx.annotation.NonNull;

import com.example.tteonam.util.TnLog;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

// Storage 규칙은 덮어쓰기를 막는다(resource == null 일 때만 create 허용).
// 재시도 때는 이미 올라간 파일을 건너뛰고 빠진 파일만 올린다.
final class StorageUploads {
    private StorageUploads() {
    }

    // 백그라운드 스레드에서만 호출한다.
    static void putIfAbsent(@NonNull StorageReference ref, @NonNull byte[] bytes,
                            @NonNull StorageMetadata metadata) throws Exception {
        if (exists(ref)) {
            TnLog.d(TnLog.UPLOAD, "이미 있는 파일 건너뜀: " + ref.getPath());
            return;
        }
        Tasks.await(TnLog.track(TnLog.UPLOAD, "putBytes " + ref.getName() + " (" + bytes.length + "B)",
                ref.putBytes(bytes, metadata)));
    }

    private static boolean exists(@NonNull StorageReference ref) {
        try {
            Tasks.await(ref.getMetadata());
            return true;
        } catch (Exception e) {
            // 파일이 없거나(ERROR_OBJECT_NOT_FOUND) 확인이 실패하면 업로드를 시도한다.
            // 업로드도 실패하면 그 오류가 사용자에게 표시된다.
            return false;
        }
    }
}
