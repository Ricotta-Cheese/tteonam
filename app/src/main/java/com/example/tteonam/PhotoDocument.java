package com.example.tteonam;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.tteonam.data.ServerClock;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.GeoPoint;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

// Firestore: trips/{tripId}/photos/{photoId}
// 필드 계약은 FirebaseControl/firestore.rules의 validPhoto와 functions/src/models.ts의 Photo를 따른다.
// 규칙이 13개 필드를 모두 요구하므로 비어 있는 값도 빠뜨리지 않고 보낸다.
public final class PhotoDocument {

    public static final String VISIBILITY_PRIVATE = "private";
    public static final String VISIBILITY_GROUP = "group";

    public static final String STATUS_UPLOADING = "uploading";
    public static final String STATUS_READY = "ready";

    // 장면 분류는 서버 분석이 따로 저장한다. 클라이언트는 항상 unknown으로 만든다.
    public static final String SCENE_UNKNOWN = "unknown";

    public static final int MAX_COMMENT_LENGTH = 200;
    public static final int MAX_PLACE_NAME_LENGTH = 100;

    @NonNull
    private final String authorId;
    @NonNull
    private final String visibility;
    @NonNull
    private final String status;
    @NonNull
    private final Timestamp capturedAt;
    @NonNull
    private final String imagePath;
    @NonNull
    private final String thumbnailPath;
    @NonNull
    private final String comment;
    private final boolean shareLocation;
    @Nullable
    private final GeoPoint location;
    @NonNull
    private final String placeName;
    @NonNull
    private final String scene;

    // 생성은 Builder로만
    private PhotoDocument(Builder b) {
        this.authorId = b.authorId;
        this.visibility = b.visibility;
        this.status = b.status;
        this.capturedAt = b.capturedAt;
        this.imagePath = b.imagePath;
        this.thumbnailPath = b.thumbnailPath;
        this.comment = b.comment;
        this.shareLocation = b.shareLocation;
        // 위치 공유를 끄면 location=null, placeName=''로 저장하는 계약
        this.location = b.shareLocation ? b.location : null;
        this.placeName = b.shareLocation ? b.placeName : "";
        this.scene = b.scene;
    }

    // ───────── Storage 경로 규칙 (한 곳에서만 만들기) ─────────
    // storage.rules·finalizePhoto는 확장자를 .jpg로만 허용한다.
    public static String imagePath(String tripId, String uid, String photoId) {
        return "trips/" + tripId + "/photos/" + uid + "/" + photoId + "/image.jpg";
    }

    public static String thumbnailPath(String tripId, String uid, String photoId) {
        return "trips/" + tripId + "/photos/" + uid + "/" + photoId + "/thumb.jpg";
    }

    // ───────── Firestore로 보낼 때 (새 문서 생성용) ─────────
    // createdAt·updatedAt은 규칙상 request.time과 같아야 하므로 서버 타임스탬프를 쓴다.
    public Map<String, Object> toCreateMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("authorId", authorId);
        map.put("visibility", visibility);
        map.put("status", status);
        map.put("capturedAt", capturedAt);
        map.put("createdAt", FieldValue.serverTimestamp());
        map.put("updatedAt", FieldValue.serverTimestamp());
        map.put("imagePath", imagePath);
        map.put("thumbnailPath", thumbnailPath);
        map.put("comment", comment);
        map.put("shareLocation", shareLocation);
        map.put("location", location);
        map.put("placeName", placeName);
        map.put("scene", scene);
        return map;
    }

    // ───────── Firestore에서 받을 때 ─────────
    @Nullable
    public static PhotoDocument fromSnapshot(DocumentSnapshot doc) {
        if (!doc.exists()) return null;
        try {
            Builder b = new Builder(doc.getString("authorId"))
                    .visibility(doc.getString("visibility"))
                    .paths(doc.getString("imagePath"), doc.getString("thumbnailPath"))
                    .comment(doc.getString("comment"))
                    .placeName(doc.getString("placeName"));
            b.status = doc.getString("status");
            b.scene = doc.getString("scene");
            b.capturedAt = doc.getTimestamp("capturedAt");
            b.shareLocation = Boolean.TRUE.equals(doc.getBoolean("shareLocation"));
            b.location = doc.getGeoPoint("location");
            return b.build();
        } catch (IllegalStateException e) {
            // 서버 문서에 필수 필드가 빠져 있으면 앱을 죽이지 말고 이 문서만 건너뛰기
            return null;
        }
    }

    // ───────── getter ─────────
    @NonNull
    public String getAuthorId() { return authorId; }
    @NonNull
    public String getVisibility() { return visibility; }
    @NonNull
    public String getStatus() { return status; }
    @NonNull
    public Timestamp getCapturedAt() { return capturedAt; }
    @NonNull
    public String getImagePath() { return imagePath; }
    @NonNull
    public String getThumbnailPath() { return thumbnailPath; }
    @NonNull
    public String getComment() { return comment; }
    public boolean isShareLocation() { return shareLocation; }
    @Nullable
    public GeoPoint getLocation() { return location; }
    @NonNull
    public String getPlaceName() { return placeName; }
    @NonNull
    public String getScene() { return scene; }

    // ───────── Builder ─────────
    public static Builder builder(String tripId, String uid, String photoId) {
        return new Builder(uid).paths(imagePath(tripId, uid, photoId), thumbnailPath(tripId, uid, photoId));
    }

    public static final class Builder {
        private final String authorId;
        private String visibility = VISIBILITY_PRIVATE;   // 기본값은 나만 보기
        private String status = STATUS_UPLOADING;         // 클라이언트는 uploading으로만 만든다
        private String scene = SCENE_UNKNOWN;
        private Timestamp capturedAt;
        private String imagePath;
        private String thumbnailPath;
        private String comment = "";
        private boolean shareLocation = false;
        private GeoPoint location;
        private String placeName = "";

        private Builder(String authorId) { this.authorId = authorId; }

        public Builder visibility(String visibility) { this.visibility = visibility; return this; }

        // 규칙(capturedAt <= request.time)에 걸리지 않도록 서버 기준 현재 시각을 넘지 않게 한다.
        public Builder capturedAt(long millis) {
            long safe = Math.min(millis, ServerClock.now());
            this.capturedAt = new Timestamp(new Date(safe));
            return this;
        }

        private Builder paths(String image, String thumb) { this.imagePath = image; this.thumbnailPath = thumb; return this; }

        public Builder comment(@Nullable String comment) {
            this.comment = clip(comment, MAX_COMMENT_LENGTH);
            return this;
        }

        // Room의 Double(null 가능)을 그대로 받기
        public Builder location(@Nullable Double lat, @Nullable Double lng, boolean share) {
            this.shareLocation = share;
            this.location = (lat != null && lng != null) ? new GeoPoint(lat, lng) : null;
            return this;
        }

        public Builder placeName(@Nullable String placeName) {
            this.placeName = clip(placeName, MAX_PLACE_NAME_LENGTH);
            return this;
        }

        public PhotoDocument build() {
            // 필수 값이 빠지면 업로드 전에 바로 알 수 있게
            if (authorId == null) throw new IllegalStateException("authorId 없음");
            if (capturedAt == null) throw new IllegalStateException("capturedAt 없음");
            if (imagePath == null || thumbnailPath == null) throw new IllegalStateException("경로 없음");
            if (!VISIBILITY_PRIVATE.equals(visibility) && !VISIBILITY_GROUP.equals(visibility)) {
                throw new IllegalStateException("visibility 값 이상: " + visibility);
            }
            if (!STATUS_UPLOADING.equals(status) && !STATUS_READY.equals(status)) {
                throw new IllegalStateException("status 값 이상: " + status);
            }
            if (scene == null) scene = SCENE_UNKNOWN;
            return new PhotoDocument(this);
        }

        @NonNull
        private static String clip(@Nullable String value, int max) {
            if (value == null) return "";
            String trimmed = value.trim();
            if (trimmed.length() <= max) return trimmed;
            // 서로게이트 쌍(이모지) 중간에서 자르지 않는다.
            int end = max;
            if (Character.isHighSurrogate(trimmed.charAt(end - 1))) end--;
            return trimmed.substring(0, end);
        }
    }
}
