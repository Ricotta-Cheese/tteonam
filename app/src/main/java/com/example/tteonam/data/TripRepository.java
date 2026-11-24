package com.example.tteonam.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.tteonam.util.TnLog;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 여행(그룹) 관련 서버 호출을 한 곳에 모은다.
// 여행·멤버 문서는 서버만 쓸 수 있으므로 생성·가입·수정은 모두 callable로 한다.
// 각 메서드는 백그라운드 스레드에서 실행되고 Task로 결과를 돌려준다.
public final class TripRepository {

    private static final ExecutorService IO = Executors.newCachedThreadPool();

    public static final class TripInfo {
        @NonNull public final String tripId;
        @NonNull public final String name;
        @NonNull public final String ownerId;
        @NonNull public final String status;
        public final long startAt;
        public final long endAt;
        @NonNull public final String timeZone;
        @Nullable public final String coverImagePath;
        public final int memberCount;

        TripInfo(@NonNull DocumentSnapshot doc) {
            tripId = doc.getId();
            name = orEmpty(doc.getString("name"));
            ownerId = orEmpty(doc.getString("ownerId"));
            status = orEmpty(doc.getString("status"));
            startAt = millis(doc.getTimestamp("startAt"));
            endAt = millis(doc.getTimestamp("endAt"));
            String zone = doc.getString("timeZone");
            timeZone = zone == null ? "UTC" : zone;
            coverImagePath = doc.getString("coverImagePath");
            Long count = doc.getLong("memberCount");
            memberCount = count == null ? 0 : count.intValue();
        }

        public boolean isOwner(@Nullable String uid) {
            return uid != null && uid.equals(ownerId);
        }

        public boolean isActive() {
            return "active".equals(status);
        }
    }

    public static final class MemberInfo {
        @NonNull public final String uid;
        @NonNull public final String displayName;
        public final boolean owner;

        MemberInfo(@NonNull DocumentSnapshot doc) {
            uid = doc.getId();
            displayName = orEmpty(doc.getString("displayName"));
            owner = "owner".equals(doc.getString("role"));
        }
    }

    public static final class Invite {
        @NonNull public final String code;
        public final long expiresAt;

        Invite(@NonNull String code, long expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }
    }

    private final Context appContext;
    private final FirebaseProvider provider;
    private final FirebaseFirestore db;
    private final FirebaseFunctions functions;
    private final FirebaseStorage storage;

    public TripRepository(@NonNull Context context) {
        appContext = context.getApplicationContext();
        provider = FirebaseProvider.get(context);
        db = provider.getFirestore();
        functions = provider.getFunctions();
        storage = provider.getStorage();
    }

    @NonNull
    private String requireUid() throws Exception {
        FirebaseUser user = provider.getAuth().getCurrentUser();
        if (user == null) {
            throw new BackendErrors.UserFacingException("로그인이 필요해요. 다시 로그인해 주세요.");
        }
        // 기기 시계가 틀려 만료된 토큰을 보내지 않도록 서버 시각 기준으로 확인
        ServerClock.ensureFreshToken(provider.getAuth());
        return user.getUid();
    }

    @NonNull
    private CurrentTripStore store(@NonNull String uid) {
        return new CurrentTripStore(appContext, uid);
    }

    // ───────────────────────── 현재 여행 ─────────────────────────

    // 기기에 기억한 여행을 먼저 확인하고, 없거나 더 이상 멤버가 아니면 가장 최근에 참여한 여행을 고른다.
    // 참여한 여행이 하나도 없으면 결과가 null이다.
    @NonNull
    public Task<TripInfo> loadCurrentTrip() {
        return Tasks.call(IO, () -> {
            String uid = requireUid();
            CurrentTripStore store = store(uid);
            String saved = store.getTripId();
            if (saved != null) {
                TripInfo trip = readTripOrNull(saved);
                if (trip != null) {
                    TnLog.d(TnLog.TRIP, "현재 여행(저장값) 사용: " + trip.tripId);
                    return trip;
                }
                TnLog.w(TnLog.TRIP, "저장된 여행에 접근할 수 없어 초기화: " + saved);
                store.setTripId(null);
            }
            QuerySnapshot refs = Tasks.await(db.collection("users").document(uid)
                    .collection("tripRefs")
                    .orderBy("joinedAt", Query.Direction.DESCENDING)
                    .limit(1)
                    .get());
            if (refs.isEmpty()) {
                TnLog.i(TnLog.TRIP, "참여한 여행 없음");
                return null;
            }
            String latest = refs.getDocuments().get(0).getId();
            TripInfo trip = readTripOrNull(latest);
            if (trip != null) {
                store.setTripId(trip.tripId);
                TnLog.i(TnLog.TRIP, "현재 여행(최근 참여) 선택: " + trip.tripId);
            }
            return trip;
        });
    }

    @Nullable
    private TripInfo readTripOrNull(@NonNull String tripId) throws Exception {
        try {
            DocumentSnapshot doc = Tasks.await(db.collection("trips").document(tripId).get());
            return doc.exists() ? new TripInfo(doc) : null;
        } catch (Exception e) {
            Throwable cause = BackendErrors.unwrap(e);
            // 멤버가 아니거나 여행이 없으면 규칙이 거부한다 → 다른 여행으로 넘어간다.
            if (cause instanceof FirebaseFirestoreException
                    && ((FirebaseFirestoreException) cause).getCode()
                    == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                TnLog.w(TnLog.TRIP, "여행 읽기 거부(멤버 아님/없음): " + tripId);
                return null;
            }
            throw e;
        }
    }

    @NonNull
    public Task<List<MemberInfo>> loadMembers(@NonNull String tripId) {
        return Tasks.call(IO, () -> {
            QuerySnapshot snapshot = Tasks.await(db.collection("trips").document(tripId)
                    .collection("members")
                    .orderBy("joinedAt", Query.Direction.ASCENDING)
                    .get());
            TnLog.d(TnLog.TRIP, "멤버 " + snapshot.size() + "명: " + tripId);
            List<MemberInfo> members = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                members.add(new MemberInfo(doc));
            }
            return members;
        });
    }

    // 대표 이미지는 토큰 URL을 쓰지 않고 인증된 SDK로 받는다(backend.md).
    @NonNull
    public Task<byte[]> loadCover(@NonNull String coverImagePath) {
        return TnLog.track(TnLog.TRIP, "loadCover",
                storage.getReference().child(coverImagePath).getBytes(JpegEncoder.MAX_COVER_BYTES));
    }

    // ───────────────────────── 생성·가입 ─────────────────────────

    // tripId는 재시도 때 같은 값을 넘겨야 멱등하게 처리된다.
    @NonNull
    public Task<String> createTrip(@NonNull String tripId, @NonNull String name,
                                   long startAt, long endAt, @NonNull String timeZone) {
        return Tasks.call(IO, () -> {
            String uid = requireUid();
            Map<String, Object> input = new HashMap<>();
            input.put("tripId", tripId);
            input.put("name", name);
            input.put("startAt", startAt);
            input.put("endAt", endAt);
            input.put("timeZone", timeZone);
            HttpsCallableResult result = Tasks.await(TnLog.track(TnLog.TRIP, "createTrip",
                    functions.getHttpsCallable("createTrip").call(input)));
            String created = stringField(result, "tripId", tripId);
            store(uid).setTripId(created);
            TnLog.i(TnLog.TRIP, "여행 생성 완료: " + created);
            return created;
        });
    }

    // 방장만 가능. Storage에 올린 뒤 updateTrip으로 대표 이미지를 지정한다.
    // assetId는 재시도에도 같은 값을 쓴다. 이미 올라간 파일은 덮어쓸 수 없으므로 건너뛴다.
    @NonNull
    public Task<String> setCover(@NonNull String tripId, @NonNull String assetId, @NonNull byte[] jpeg) {
        return Tasks.call(IO, () -> {
            requireUid();
            String path = "trips/" + tripId + "/covers/" + assetId + ".jpg";
            StorageReference ref = storage.getReference().child(path);
            TnLog.d(TnLog.TRIP, "대표 이미지 업로드: " + path + " (" + jpeg.length + "B)");
            StorageUploads.putIfAbsent(ref, jpeg, jpegMetadata());
            Map<String, Object> input = new HashMap<>();
            input.put("tripId", tripId);
            input.put("coverImagePath", path);
            Tasks.await(TnLog.track(TnLog.TRIP, "updateTrip(cover)",
                    functions.getHttpsCallable("updateTrip").call(input)));
            return path;
        });
    }

    @NonNull
    public Task<Invite> createInvite(@NonNull String tripId) {
        return Tasks.call(IO, () -> {
            String uid = requireUid();
            Map<String, Object> input = new HashMap<>();
            input.put("tripId", tripId);
            HttpsCallableResult result = Tasks.await(TnLog.track(TnLog.TRIP, "createInvite",
                    functions.getHttpsCallable("createInvite").call(input)));
            Map<?, ?> data = (Map<?, ?>) result.getData();
            String code = data == null ? null : (String) data.get("code");
            Object expires = data == null ? null : data.get("expiresAt");
            if (code == null || !(expires instanceof Number)) {
                throw new BackendErrors.UserFacingException("초대 코드를 받지 못했어요. 다시 시도해 주세요.");
            }
            Invite invite = new Invite(code, ((Number) expires).longValue());
            store(uid).saveInvite(tripId, invite.code, invite.expiresAt);
            return invite;
        });
    }

    // 기기에 저장해 둔 초대 코드가 아직 유효하면 돌려준다. 다시 발급하면 이전 코드가 무효가 되므로
    // 화면을 열 때마다 자동으로 발급하지 않는다.
    @Nullable
    public Invite cachedInvite(@NonNull String tripId) {
        FirebaseUser user = provider.getAuth().getCurrentUser();
        if (user == null) return null;
        CurrentTripStore store = store(user.getUid());
        String code = store.getInviteCode(tripId);
        long expiresAt = store.getInviteExpiresAt(tripId);
        if (code == null || expiresAt <= ServerClock.now()) return null;
        return new Invite(code, expiresAt);
    }

    @NonNull
    public Task<String> joinTrip(@NonNull String code) {
        return Tasks.call(IO, () -> {
            String uid = requireUid();
            Map<String, Object> input = new HashMap<>();
            input.put("code", code);
            HttpsCallableResult result = Tasks.await(TnLog.track(TnLog.TRIP, "joinTrip",
                    functions.getHttpsCallable("joinTrip").call(input)));
            String tripId = stringField(result, "tripId", null);
            if (tripId == null) {
                throw new BackendErrors.UserFacingException("여행 정보를 받지 못했어요. 다시 시도해 주세요.");
            }
            store(uid).setTripId(tripId);
            TnLog.i(TnLog.TRIP, "여행 참여 완료: " + tripId);
            return tripId;
        });
    }

    // ───────────────────────── helpers ─────────────────────────

    @NonNull
    static StorageMetadata jpegMetadata() {
        return new StorageMetadata.Builder().setContentType("image/jpeg").build();
    }

    @Nullable
    private static String stringField(@NonNull HttpsCallableResult result, @NonNull String key,
                                      @Nullable String fallback) {
        Object data = result.getData();
        if (data instanceof Map) {
            Object value = ((Map<?, ?>) data).get(key);
            if (value instanceof String) return (String) value;
        }
        return fallback;
    }

    @NonNull
    private static String orEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    private static long millis(@Nullable Timestamp timestamp) {
        return timestamp == null ? 0L : timestamp.toDate().getTime();
    }
}
