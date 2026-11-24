package com.example.tteonam.data;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.tteonam.Photo;
import com.example.tteonam.PhotoDB;
import com.example.tteonam.PhotoDao;
import com.example.tteonam.PhotoDocument;
import com.example.tteonam.util.TnLog;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 사진 한 장 업로드. 순서는 FirebaseControl/docs/backend.md "앱의 첫 연결 순서"를 따른다.
//   1. 고정 photoId 발급(Room에 저장, 재시도에도 유지)
//   2. JPEG 재인코딩(EXIF 제거) + 썸네일 생성
//   3. Firestore 사진 문서를 status=uploading으로 생성
//   4. image.jpg, thumb.jpg 업로드 (이미 있으면 건너뜀)
//   5. finalizePhoto 호출 → 서버가 ready로 바꾸고 AI 분석 작업을 등록
//   6. 성공하면 기기의 임시 사진·Room 행 삭제
// 어느 단계에서 실패해도 다시 호출하면 남은 단계부터 이어서 진행된다.
public final class PhotoUploader {
    // 사진 인코딩은 메모리를 많이 쓰므로 한 번에 한 장씩
    private static final ExecutorService UPLOAD = Executors.newSingleThreadExecutor();

    private final Context appContext;
    private final FirebaseProvider provider;
    private final FirebaseFirestore db;
    private final PhotoDao dao;

    public PhotoUploader(@NonNull Context context) {
        appContext = context.getApplicationContext();
        provider = FirebaseProvider.get(context);
        db = provider.getFirestore();
        dao = PhotoDB.get(context).photoDao();
    }

    // tripId는 처음 업로드할 때만 쓰인다. 이미 다른 여행으로 시작한 사진은 그 여행으로 이어서 올린다.
    // visibility·comment·shareLocation은 문서를 처음 만들 때 확정되며 이후 바꾸지 않는다(서버 계약).
    @NonNull
    public Task<String> upload(long localPhotoId, @NonNull String tripId, @NonNull String visibility,
                               @Nullable String comment, boolean shareLocation) {
        return Tasks.call(UPLOAD, () -> {
            FirebaseUser user = provider.getAuth().getCurrentUser();
            if (user == null) {
                throw new BackendErrors.UserFacingException("로그인이 필요해요. 다시 로그인해 주세요.");
            }
            String uid = user.getUid();
            ServerClock.ensureFreshToken(provider.getAuth());

            Photo photo = dao.findById(localPhotoId);
            TnLog.i(TnLog.UPLOAD, "업로드 시작: local=" + localPhotoId + ", visibility=" + visibility
                    + ", shareLocation=" + shareLocation);
            if (photo == null || !new File(photo.path).exists()) {
                TnLog.w(TnLog.UPLOAD, "기기에 사진 없음: local=" + localPhotoId);
                throw new BackendErrors.UserFacingException("기기에서 사진을 찾을 수 없어요.");
            }

            // 1. 여행·사진 ID 고정
            if (photo.serverPhotoId == null || photo.tripId == null) {
                String photoId = db.collection("trips").document(tripId)
                        .collection("photos").document().getId();
                dao.assignServerIds(photo.id, tripId, photoId);
                photo.tripId = tripId;
                photo.serverPhotoId = photoId;
                TnLog.d(TnLog.UPLOAD, "ID 발급: trip=" + tripId + ", photo=" + photoId);
            } else {
                TnLog.d(TnLog.UPLOAD, "이전 시도 이어서: trip=" + photo.tripId + ", photo="
                        + photo.serverPhotoId + ", docCreated=" + photo.docCreated);
            }
            String targetTrip = photo.tripId;
            String photoId = photo.serverPhotoId;

            PhotoDocument doc = PhotoDocument.builder(targetTrip, uid, photoId)
                    .visibility(visibility)
                    .capturedAt(photo.capturedAt)
                    .comment(comment)
                    .location(photo.lat, photo.lng, shareLocation)
                    // 지명은 문서를 처음 만들 때만 필요하다.
                    .placeName(shareLocation && !photo.docCreated ? placeName(photo.lat, photo.lng) : "")
                    .build();

            // 2. 업로드할 JPEG 준비
            JpegEncoder.PhotoBytes bytes = JpegEncoder.encodePhoto(photo.path, photo.rotation);

            // 3. 사진 문서 생성
            DocumentReference docRef = db.collection("trips").document(targetTrip)
                    .collection("photos").document(photoId);
            if (!photo.docCreated) {
                createDocument(docRef, doc, uid);
                dao.markDocCreated(photo.id);
                TnLog.d(TnLog.UPLOAD, "사진 문서 생성(uploading): " + photoId);
            }

            // 4. 파일 업로드
            StorageReference root = provider.getStorage().getReference();
            StorageUploads.putIfAbsent(root.child(doc.getImagePath()), bytes.image,
                    TripRepository.jpegMetadata());
            StorageUploads.putIfAbsent(root.child(doc.getThumbnailPath()), bytes.thumbnail,
                    TripRepository.jpegMetadata());

            // 5. 완료 처리
            Map<String, Object> input = new HashMap<>();
            input.put("tripId", targetTrip);
            input.put("photoId", photoId);
            Tasks.await(TnLog.track(TnLog.UPLOAD, "finalizePhoto",
                    provider.getFunctions().getHttpsCallable("finalizePhoto").call(input)));

            // 6. 서버에 원본이 있으므로 기기의 임시 사본 정리
            dao.deleteById(photo.id);
            if (!new File(photo.path).delete()) {
                TnLog.w(TnLog.UPLOAD, "임시 사진 삭제 실패: " + photo.path);
            }
            TnLog.i(TnLog.UPLOAD, "업로드 완료: photo=" + photoId);
            return photoId;
        });
    }

    // 문서는 한 번만 만들 수 있다(이후에는 comment·updatedAt만 수정 가능).
    // 생성 응답을 받기 전에 앱이 끊겼다면 문서가 이미 있을 수 있으므로 확인 후 이어간다.
    private void createDocument(@NonNull DocumentReference ref, @NonNull PhotoDocument doc,
                                @NonNull String uid) throws Exception {
        try {
            Tasks.await(TnLog.track(TnLog.UPLOAD, "createPhotoDoc", ref.set(doc.toCreateMap())));
        } catch (Exception createError) {
            TnLog.d(TnLog.UPLOAD, "문서 생성 실패 → 이미 있는지 확인: " + ref.getId());
            DocumentSnapshot existing;
            try {
                existing = Tasks.await(ref.get());
            } catch (Exception ignored) {
                throw createError;
            }
            if (!existing.exists() || !uid.equals(existing.getString("authorId"))) {
                throw createError;
            }
            TnLog.i(TnLog.UPLOAD, "이전 시도에서 만든 문서 확인, 이어서 진행: " + ref.getId());
        }
    }

    // 장소별 앨범에 쓰는 지명. 실패하면 빈 값으로 보낸다.
    @NonNull
    private String placeName(@Nullable Double lat, @Nullable Double lng) {
        if (lat == null || lng == null || !Geocoder.isPresent()) return "";
        try {
            @SuppressWarnings("deprecation")
            List<Address> found = new Geocoder(appContext, Locale.KOREA).getFromLocation(lat, lng, 1);
            if (found == null || found.isEmpty()) return "";
            Address a = found.get(0);
            String place = firstNonEmpty(a.getLocality(), a.getSubAdminArea(), a.getAdminArea());
            TnLog.d(TnLog.UPLOAD, "지명 변환: " + (place == null ? "(없음)" : place));
            return place == null ? "" : place;
        } catch (Exception e) {
            TnLog.d(TnLog.UPLOAD, "지명 변환 실패", e);
            return "";
        }
    }

    @Nullable
    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return null;
    }
}
