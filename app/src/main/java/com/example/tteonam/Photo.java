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

//    @Nullable
//    public String comment;          // 05 공유에서 쓰는 코멘트
//    @Nullable
//    public String labels;           // ML Kit 라벨 ("Food,Sky" 처럼 쉼표로)

    @Nullable
    public String serverPhotoId;    // Firestore trips/{tripId}/photos/{photoId}, 업로드를 처음 시작할 때 정해짐

    public boolean uploaded;        // 서버에 올렸는지 (나중에 사용)
}
