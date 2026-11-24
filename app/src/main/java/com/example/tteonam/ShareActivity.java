package com.example.tteonam;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.tteonam.auth.AuthenticatedActivity;
import com.example.tteonam.data.BackendErrors;
import com.example.tteonam.data.PhotoUploader;
import com.example.tteonam.data.TripRepository;
import com.example.tteonam.databinding.ActivityShareBinding;
import com.example.tteonam.util.TnLog;
import com.google.android.material.chip.Chip;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ShareActivity extends AuthenticatedActivity {

    // Intent 키
    public static final String EXTRA_PHOTO_ID = "photo_id";

    private ActivityShareBinding binding;

    // room
    private PhotoDB photoRoomDB;
    private Photo photo;

    // 사진을 올릴 여행 (서버의 내 여행 중 현재 여행)
    private TripRepository.TripInfo trip;
    private boolean tripLoaded = false;
    private boolean uploading = false;

    private PhotoUploader uploader;

    // 태그 수정 (서버 AI 분석 결과와 연결 예정, 지금은 화면에서 숨김)
    private final LinkedHashSet<String> currentTagIds = new LinkedHashSet<>();
    private final Set<String> aiTagIds = new HashSet<>();
    private final LinkedHashMap<String, String> tagLabels = new LinkedHashMap<>();

    // ───────────────────────── Lifecycle ─────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isRedirectingToLogin()) {   // 로그인 안 돼 있으면 LoginActivity로 이동 중 -> 화면 만들지 않음
            return;
        }

        WindowCompat.enableEdgeToEdge(getWindow());
        binding = ActivityShareBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        photoRoomDB = PhotoDB.get(this);
        uploader = new PhotoUploader(this);
        setListeners();
        updateButtons();

        long photoId = getIntent().getLongExtra(EXTRA_PHOTO_ID, -1);
        PhotoDB.dbExecutor.execute(() -> {
            Photo loaded = photoRoomDB.photoDao().findById(photoId);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (loaded == null) {
                    TnLog.w(TnLog.SHARE, "Room에 사진 없음: local=" + photoId);
                    Toast.makeText(this, "사진을 찾지 못했어요", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                photo = loaded;
                TnLog.d(TnLog.SHARE, "사진 불러옴: local=" + photo.id + ", 위치 "
                        + (photo.lat != null ? "있음" : "없음") + ", docCreated=" + photo.docCreated);
                if (!showPhoto()) return;
                bindPhotoOptions();
                binding.loading.setVisibility(View.GONE);
                binding.content.setVisibility(View.VISIBLE);
                updateButtons();
            });
        });

        loadTrip();
    }

    // ───────────────────────── init ─────────────────────────

    private void loadTrip() {
        binding.tripText.setText("여행 정보를 불러오는 중…");
        new TripRepository(this).loadCurrentTrip().addOnCompleteListener(this, task -> {
            tripLoaded = true;
            if (!task.isSuccessful()) {
                TnLog.w(TnLog.SHARE, "현재 여행 불러오기 실패", task.getException());
                trip = null;
                binding.tripText.setText(BackendErrors.message(task.getException()));
            } else {
                trip = task.getResult();
                TnLog.d(TnLog.SHARE, "업로드 대상 여행: " + (trip == null ? "없음" : trip.tripId));
                binding.tripText.setText(trip == null
                        ? "참여 중인 여행이 없어요. 그룹을 만들거나 참여하면 올릴 수 있어요."
                        : "‘" + trip.name + "’ 여행에 올려요");
            }
            updateButtons();
        });
    }

    private void setListeners() {
        binding.backButton.setOnClickListener(v -> {
            TnLog.i(TnLog.SHARE, "나중에: 사진을 기기에 남김 (local=" + (photo == null ? -1 : photo.id) + ")");
            finish();   // 사진은 기기에 남겨 둔다
        });
        binding.deletePhoto.setOnClickListener(v -> confirmDelete());
        binding.noShareButton.setOnClickListener(v -> uploadPhoto(PhotoDocument.VISIBILITY_PRIVATE));
        binding.groupShareButton.setOnClickListener(v -> uploadPhoto(PhotoDocument.VISIBILITY_GROUP));
        binding.addTagButton.setOnClickListener(v -> openTagPicker());
    }

    // 위치가 없는 사진은 위치 공유를 켤 수 없다.
    private void bindPhotoOptions() {
        boolean hasLocation = photo.lat != null && photo.lng != null;
        binding.shareLocationSwitch.setEnabled(hasLocation);
        binding.shareLocationSwitch.setChecked(hasLocation);
        if (!hasLocation) {
            binding.shareLocationSwitch.setText("촬영 위치 정보가 없어요");
        }
        if (photo.docCreated) {
            // 이전 시도에서 서버 문서가 만들어졌으면 코멘트·위치 선택은 그때 값으로 고정된다.
            binding.commentInput.setEnabled(false);
            binding.shareLocationSwitch.setEnabled(false);
        }
    }

    // ───────────────────────── Photo ─────────────────────────

    private boolean showPhoto() {
        String photoPath = photo.path;
        if (!new File(photoPath).exists()) {
            Toast.makeText(this, "사진을 찾을 수 없어요", Toast.LENGTH_SHORT).show();
            TnLog.w(TnLog.SHARE, "사진 파일 없음: " + photoPath);
            finish();
            return false;
        }

        // 화면 표시용으로 가로 세로 1/4 압축 (업로드용 인코딩은 PhotoUploader가 따로 한다)
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;
        Bitmap bitmap = BitmapFactory.decodeFile(photoPath, options);
        if (bitmap == null) {
            Toast.makeText(this, "사진을 열 수 없어요", Toast.LENGTH_SHORT).show();
            TnLog.w(TnLog.SHARE, "사진 디코딩 실패: " + photoPath);
            finish();
            return false;
        }

        if (photo.rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(photo.rotation);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }
        binding.photoImage.setImageBitmap(bitmap);
        return true;
    }

    // ───────────────────────── Upload ─────────────────────────

    private void uploadPhoto(String visibility) {
        if (photo == null || trip == null || uploading) return;

        CharSequence comment = binding.commentInput.getText();
        boolean shareLocation = binding.shareLocationSwitch.isEnabled()
                && binding.shareLocationSwitch.isChecked();

        TnLog.i(TnLog.SHARE, "업로드 요청: " + visibility + ", 위치공유=" + shareLocation
                + ", 코멘트 " + (comment == null ? 0 : comment.length()) + "자");
        setUploading(true);
        uploader.upload(photo.id, trip.tripId, visibility,
                        comment == null ? "" : comment.toString(), shareLocation)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        TnLog.i(TnLog.SHARE, "업로드 성공: photo=" + task.getResult());
                        Toast.makeText(this, PhotoDocument.VISIBILITY_GROUP.equals(visibility)
                                ? "그룹에 공유했어요" : "내 앨범에 올렸어요", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    TnLog.e(TnLog.SHARE, "업로드 실패", task.getException());
                    Toast.makeText(this, BackendErrors.message(task.getException()),
                            Toast.LENGTH_LONG).show();
                    setUploading(false);
                    // 공개 범위·코멘트는 첫 시도에서 서버 문서에 고정될 수 있으므로 최신 상태를 다시 읽는다.
                    reloadPhotoState();
                });
    }

    private void reloadPhotoState() {
        long id = photo.id;
        PhotoDB.dbExecutor.execute(() -> {
            Photo latest = photoRoomDB.photoDao().findById(id);
            runOnUiThread(() -> {
                if (latest == null || isFinishing()) return;
                photo = latest;
                if (photo.docCreated) {
                    // 문서가 이미 만들어졌으면 코멘트·위치 선택은 처음 값으로 유지된다.
                    binding.commentInput.setEnabled(false);
                    binding.shareLocationSwitch.setEnabled(false);
                }
            });
        });
    }

    private void setUploading(boolean value) {
        uploading = value;
        binding.loading.setVisibility(value ? View.VISIBLE : View.GONE);
        updateButtons();
    }

    private void updateButtons() {
        boolean canUpload = photo != null && trip != null && tripLoaded && !uploading;
        binding.noShareButton.setEnabled(canUpload);
        binding.groupShareButton.setEnabled(canUpload);
        binding.deletePhoto.setEnabled(photo != null && !uploading);
        binding.backButton.setEnabled(!uploading);
    }

    // ───────────────────────── delete photo ─────────────────────────

    private void confirmDelete() {
        if (photo == null || uploading) return;
        new AlertDialog.Builder(this)
                .setTitle("사진 삭제")
                .setMessage("기기에 저장된 이 사진을 삭제할까요? 되돌릴 수 없어요.")
                .setPositiveButton("삭제", (dialog, which) -> {
                    TnLog.i(TnLog.SHARE, "사진 삭제: local=" + photo.id);
                    deletePhoto(photo);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void deletePhoto(Photo target) {
        PhotoDB.dbExecutor.execute(() -> {
            photoRoomDB.photoDao().delete(target);
            if (!new File(target.path).delete()) {
                TnLog.w(TnLog.SHARE, "사진 파일 삭제 실패: " + target.path);
            }
            runOnUiThread(this::finish);         // 삭제가 끝나면 화면 닫기
        });
    }

    // ───────────────────────── image self labeling ─────────────────────────
    // TODO(박이든): 업로드 후 getPhotoAnalysis/updatePhotoTags와 연결하면 태그 UI를 다시 보이게 한다.
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
        if (tagLabels.isEmpty()) return;
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
    List<String> addedTagIds() {
        List<String> added = new ArrayList<>(currentTagIds);
        added.removeAll(aiTagIds);
        return added;
    }

    List<String> removedTagIds() {
        List<String> removed = new ArrayList<>(aiTagIds);
        removed.removeAll(currentTagIds);
        return removed;
    }
}
