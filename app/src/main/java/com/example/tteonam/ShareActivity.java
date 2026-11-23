package com.example.tteonam;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.tteonam.databinding.ActivityShareBinding;
import com.example.tteonam.databinding.ActivityShareBinding;
import com.google.android.gms.tasks.Task;
import com.google.android.material.chip.Chip;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ShareActivity extends AppCompatActivity {

    private static final String TAG = "tn";

    // Intent 키
    public static final String EXTRA_PHOTO_ID = "photo_id";

    private ActivityShareBinding binding;

    private long photoId;

    // room
    private PhotoDB photoRoomDB;
    private Photo photo;

    // firestore
    private FirebaseFirestore photoInfoDB;


    // 태그 수정
    private final LinkedHashSet<String> currentTagIds = new LinkedHashSet<>();
    private final Set<String> aiTagIds = new HashSet<>();
    private final LinkedHashMap<String, String> tagLabels = new LinkedHashMap<>();

    // trip 별로 사진 저장
    private String tripId;

    private String uid;

    // 디버깅용
    private int checker = 0;

    // ───────────────────────── Lifecycle ─────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.enableEdgeToEdge(getWindow());
        binding = ActivityShareBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Log.d(TAG, "share act " + checker++);
        setIds();

        Log.d(TAG, "share act " + checker++);
        setListeners();

        Log.d(TAG, "share act " + checker++);
        photoRoomDB = PhotoDB.get(this);

        Log.d(TAG, "share act " + checker++);
        initializeCloudFirestore();

        Log.d(TAG, "share act " + checker++);
        photoId = getIntent().getLongExtra(EXTRA_PHOTO_ID, -1);

        Log.d(TAG, "share act " + checker++);
        PhotoDB.dbExecutor.execute(() -> {
            Photo loaded = photoRoomDB.photoDao().findById(photoId);
            runOnUiThread(() -> {
                if (loaded == null) {
                    Toast.makeText(this, "share act 사진 찾지 못함", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                photo = loaded;
                showPhoto();
                binding.loading.setVisibility(View.GONE);
                binding.content.setVisibility(View.VISIBLE);
            });
        });
    }

    // ───────────────────────── init ─────────────────────────

    // 임시
    private void setIds(){
        tripId = "12341234";
        uid = "01234567";
    }

    private void initializeCloudFirestore() {
        photoInfoDB = FirebaseFirestore.getInstance();
    }

    private void setListeners() {
        binding.backButton.setOnClickListener(v -> finish());
        binding.deletePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deletePhoto(photo);
            }
        });
        binding.noShareButton.setOnClickListener(v -> uploadPhoto(PhotoDocument.VISIBILITY_PRIVATE));
        binding.groupShareButton.setOnClickListener(v -> uploadPhoto(PhotoDocument.VISIBILITY_GROUP));
    }

    // ───────────────────────── Photo ─────────────────────────

    private void showPhoto() {
        int rotation = photo.rotation;
        String photoPath = photo.path;

        if (photoPath == null || !new File(photoPath).exists()) {
            Toast.makeText(this, "사진을 찾을 수 없어요", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "사진 없음: " + photoPath);
            finish();
            return;
        }

        // 가로 세로 1/4 압축
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;
        Bitmap bitmap = BitmapFactory.decodeFile(photoPath, options);
        if (bitmap == null) {
            Toast.makeText(this, "사진을 열 수 없어요", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "사진 디코딩 실패: " + photoPath);
            finish();
            return;
        }

        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }
        binding.photoImage.setImageBitmap(bitmap);
    }

    // ───────────────────────── Upload ─────────────────────────
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;   // 원본 10MiB
    private static final int MAX_THUMB_BYTES = 512 * 1024;            // 썸네일 512KiB
    private static final int THUMB_LONG_SIDE = 640;                   // 썸네일 긴 변(px)

    private void uploadPhoto(String visibility) {
        if (photo == null) return;

        File original = new File(photo.path);
        if (original.length() > MAX_IMAGE_BYTES) {
            Toast.makeText(this, "사진 10MB 초과", Toast.LENGTH_SHORT).show();
            return;
        }
        byte[] thumbBytes = makeThumbnail();
        if (thumbBytes.length > MAX_THUMB_BYTES) {
            Toast.makeText(this, "썸네일 크기 초과", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "썸네일 크기 초과: " + thumbBytes.length);
            return;
        }

        setUploading(true); // 버튼 끄고 로딩

        CollectionReference photosRef = photoInfoDB
                .collection("trips")
                .document(tripId)
                .collection("photos");

        // 재시도 -> id 재사용
        DocumentReference docRef = (photo.serverPhotoId != null)
                ? photosRef.document(photo.serverPhotoId)
                : photosRef.document();
        String serverPhotoId = docRef.getId();
        long localId = photo.id;

        if (photo.serverPhotoId == null) {
            photo.serverPhotoId = serverPhotoId;
            PhotoDB.dbExecutor.execute(() -> photoRoomDB.photoDao().updateServerPhotoId(localId, serverPhotoId));
        }

        PhotoDocument doc = PhotoDocument.builder(tripId, uid, serverPhotoId)
                .visibility(visibility)
                .capturedAt(photo.capturedAt)
                .comment(binding.commentInput.getText().toString())
                .location(photo.lat, photo.lng, getShareLocationChecked())
                .build();

        StorageReference root = FirebaseStorage.getInstance().getReference();

        docRef.set(doc.toMap())
                .continueWithTask(t -> { //  문서가 생긴 뒤에 원본 업로드
                    if (!t.isSuccessful()) throw failure(t);
                    Log.d(TAG, "share act 문서 생성 완료: " + serverPhotoId);
                    return root.child(doc.getImagePath()).putFile(Uri.fromFile(original));
                })
                .continueWithTask(t -> { // 원본이 올라간 뒤에 썸네일 업로드
                    if (!t.isSuccessful()) throw failure(t);
                    Log.d(TAG, "share act 원본 업로드 완료");
                    return root.child(doc.getThumbnailPath()).putBytes(thumbBytes);
                })
                .addOnSuccessListener(r -> {
                    Log.d(TAG, "share act 업로드 완료: " + serverPhotoId);
                    PhotoDB.dbExecutor.execute(() -> {
//                        photoRoomDB.photoDao().markUploaded(localId);
                        deletePhoto(photo);
                        runOnUiThread(this::finish);
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "share act 업로드 실패", e);
                    Toast.makeText(this, "업로드에 실패했어요. 다시 시도해 주세요", Toast.LENGTH_SHORT).show();
                    setUploading(false); // 다시 누르기
                });
    }

    // 실패한 Task의 예외
    private static Exception failure(Task<?> t) {
        Exception e = t.getException();
        return (e != null) ? e : new Exception("작업 취소");
    }

    private void setUploading(boolean uploading) {
        binding.noShareButton.setEnabled(!uploading);
        binding.groupShareButton.setEnabled(!uploading);
        binding.deletePhoto.setEnabled(!uploading);
        binding.loading.setVisibility(uploading ? View.VISIBLE : View.GONE);
    }

    // 화면에 띄운 비트맵으로 썸네일
    private byte[] makeThumbnail() {
        Bitmap src = ((BitmapDrawable) binding.photoImage.getDrawable()).getBitmap();
        float scale = (float) THUMB_LONG_SIDE / Math.max(src.getWidth(), src.getHeight());
        Bitmap small = (scale < 1f)
                ? Bitmap.createScaledBitmap(src, Math.round(src.getWidth() * scale), Math.round(src.getHeight() * scale), true)
                : src;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        small.compress(Bitmap.CompressFormat.JPEG, 50, baos);
        return baos.toByteArray();
    }

    // ───────────────────────── delete photo ─────────────────────────
    private void deletePhoto(Photo photo) {
        if (photo == null || !deleteCheckAndReturnYes()) return;
        PhotoDB.dbExecutor.execute(() -> {
            photoRoomDB.photoDao().delete(photo);
            new File(photo.path).delete();      // 사진 파일도 같이 삭제
            runOnUiThread(this::finish);         // 삭제가 끝나면 화면 닫기
        });
    }

    private boolean deleteCheckAndReturnYes(){
        //
        return true;
    }

    // ───────────────────────── image self labeling ─────────────────────────
    private void showTags() {
        binding.tagChips.removeAllViews();
        for (String id : currentTagIds) {
            Chip chip = new Chip(this);
            chip.setText(tagLabels.getOrDefault(id, id));
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> {
                currentTagIds.remove(id);
                showTags();
            });
            binding.tagChips.addView(chip);
        }
    }

    // + Tag 버튼: 120개 중 체크해서 고르기
    private void openTagPicker() {
        String[] ids = tagLabels.keySet().toArray(new String[0]);
        String[] labels = tagLabels.values().toArray(new String[0]);
        boolean[] checked = new boolean[ids.length];
        for (int i = 0; i < ids.length; i++) checked[i] = currentTagIds.contains(ids[i]);

        new AlertDialog.Builder(this)
                .setTitle("태그 선택")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("확인", (dialog, w) -> {
                    currentTagIds.clear();
                    for (int i = 0; i < ids.length; i++) {
                        if (checked[i]) currentTagIds.add(ids[i]);
                    }
                    showTags();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // 서버로 보낼 값: "원하는 최종 태그"와 "AI 추천"의 차이
    // added = 최종 - AI, removed = AI - 최종   (최종 = (AI - removed) ∪ added 를 거꾸로 계산)
    private List<String> addedTagIds() {
        List<String> added = new ArrayList<>(currentTagIds);
        added.removeAll(aiTagIds);
        return added;
    }

    private List<String> removedTagIds() {
        List<String> removed = new ArrayList<>(aiTagIds);
        removed.removeAll(currentTagIds);
        return removed;
    }

    private boolean isNetworkConnected(){
        return true;
    }

    private boolean getShareLocationChecked(){
        return true;
    }
}

