package com.example.tteonam;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.GeoPoint;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

// Firestore: trips/{tripId}/photos/{photoId}
public final class PhotoDocument {

    public static final String VISIBILITY_PRIVATE = "private";
    public static final String VISIBILITY_GROUP = "group";

    @NonNull
    private final String authorId;
    @NonNull
    private final String visibility;
    @NonNull
    private final Timestamp capturedAt;
    @NonNull
    private final String imagePath;
    @NonNull
    private final String thumbnailPath;
    @Nullable
    private final String comment;
    private final boolean shareLocation;
    @Nullable
    private final GeoPoint location;
    @Nullable
    private final String placeName;

    // 생성은 Builder로만
    private PhotoDocument(Builder b) {
        this.authorId = b.authorId;
        this.visibility = b.visibility;
        this.capturedAt = b.capturedAt;
        this.imagePath = b.imagePath;
        this.thumbnailPath = b.thumbnailPath;
        this.comment = b.comment;
        this.shareLocation = b.shareLocation;
        this.location = b.location;
        this.placeName = b.placeName;
    }

    // ───────── Storage 경로 규칙 (한 곳에서만 만들기) ─────────
    public static String imagePath(String tripId, String uid, String photoId) {
        return "trips/" + tripId + "/photos/" + uid + "/" + photoId + "/image.jpeg";
    }

    public static String thumbnailPath(String tripId, String uid, String photoId) {
        return "trips/" + tripId + "/photos/" + uid + "/" + photoId + "/thumb.jpeg";
    }

    // ───────── Firestore로 보낼 때 ─────────
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("authorId", authorId);
        map.put("visibility", visibility);
        map.put("capturedAt", capturedAt);
        map.put("imagePath", imagePath);
        map.put("thumbnailPath", thumbnailPath);
        map.put("shareLocation", shareLocation);
        if (comment != null) map.put("comment", comment);
        if (shareLocation && location != null) map.put("location", location);   // 위치 공유 안 하면 아예 안 보냄
        if (shareLocation && placeName != null) map.put("placeName", placeName);
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
            b.capturedAt = doc.getTimestamp("capturedAt");
            b.shareLocation = Boolean.TRUE.equals(doc.getBoolean("shareLocation"));
            b.location = doc.getGeoPoint("location");
            return b.build();
        } catch (IllegalStateException e) {
            // 서버 문서에 필수 필드가 빠져 있으면 앱을 죽이지 말고 이 문서만 건너뛰기
            Log.w("ttn", "사진 문서 형식 이상: " + doc.getId(), e);
            return null;
        }
    }

    // ───────── getter ─────────
    @NonNull
    public String getAuthorId() { return authorId; }
    @NonNull
    public String getVisibility() { return visibility; }
    @NonNull
    public Timestamp getCapturedAt() { return capturedAt; }
    @NonNull
    public String getImagePath() { return imagePath; }
    @NonNull
    public String getThumbnailPath() { return thumbnailPath; }
    @Nullable
    public String getComment() { return comment; }
    public boolean isShareLocation() { return shareLocation; }
    @Nullable
    public GeoPoint getLocation() { return location; }
    @Nullable
    public String getPlaceName() { return placeName; }

    // ───────── Builder ─────────
    public static Builder builder(String tripId, String uid, String photoId) {
        return new Builder(uid).paths(imagePath(tripId, uid, photoId), thumbnailPath(tripId, uid, photoId));
    }

    public static final class Builder {
        private final String authorId;
        private String visibility = VISIBILITY_PRIVATE;   // 기본값은 나만 보기
        private Timestamp capturedAt;
        private String imagePath;
        private String thumbnailPath;
        private String comment;
        private boolean shareLocation = false;
        private GeoPoint location;
        private String placeName;

        private Builder(String authorId) { this.authorId = authorId; }

        public Builder visibility(String visibility) { this.visibility = visibility; return this; }

        public Builder capturedAt(long millis) { this.capturedAt = new Timestamp(new Date(millis)); return this; }

        private Builder paths(String image, String thumb) { this.imagePath = image; this.thumbnailPath = thumb; return this; }

        public Builder comment(@Nullable String comment) {
            this.comment = (comment == null || comment.trim().isEmpty()) ? null : comment.trim();
            return this;
        }

        // Room의 Double(null 가능)을 그대로 받기
        public Builder location(@Nullable Double lat, @Nullable Double lng, boolean share) {
            this.shareLocation = share;
            this.location = (lat != null && lng != null) ? new GeoPoint(lat, lng) : null;
            return this;
        }

        public Builder placeName(@Nullable String placeName) { this.placeName = placeName; return this; }

        public PhotoDocument build() {
            // 필수 값이 빠지면 업로드 전에 바로 알 수 있게
            if (authorId == null) throw new IllegalStateException("authorId 없음");
            if (capturedAt == null) throw new IllegalStateException("capturedAt 없음");
            if (imagePath == null || thumbnailPath == null) throw new IllegalStateException("경로 없음");
            if (!VISIBILITY_PRIVATE.equals(visibility) && !VISIBILITY_GROUP.equals(visibility)) {
                throw new IllegalStateException("visibility 값 이상: " + visibility);
            }
            return new PhotoDocument(this);
        }
    }
}