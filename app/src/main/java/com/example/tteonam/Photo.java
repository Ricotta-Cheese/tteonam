package com.example.tteonam;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

// 사진 한 장 = 표의 한 줄
@Entity(tableName = "photos")
public class Photo {

    @PrimaryKey(autoGenerate = true)
    public long id;                 // 자동으로 1, 2, 3 ... 붙음

    @NonNull
    public String path = "";        // filesDir/photos/IMG_xxx.jpg

    public long capturedAt;         // 촬영 시각 (UTC 밀리초)
    public int rotation;            // 돌려야 하는 각도

    @Nullable
    public Double lat;              // 위치가 없을 수 있어서 Double(null 가능)
    @Nullable
    public Double lng;

    // 업로드를 처음 시작할 때 정해지고 재시도에서도 그대로 쓴다.
    // Storage 경로와 Firestore 문서가 이 두 값으로 고정되기 때문이다.
    @Nullable
    public String tripId;           // trips/{tripId}
    @Nullable
    public String serverPhotoId;    // trips/{tripId}/photos/{serverPhotoId}

    public boolean docCreated;      // Firestore 사진 문서(uploading)를 만들었는지
}
