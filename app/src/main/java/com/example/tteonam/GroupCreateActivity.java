package com.example.tteonam;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.util.Pair;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.tteonam.auth.AuthenticatedActivity;
import com.example.tteonam.data.BackendErrors;
import com.example.tteonam.data.JpegEncoder;
import com.example.tteonam.data.TripRepository;
import com.example.tteonam.databinding.ActivityGroupCreateBinding;
import com.example.tteonam.util.TnLog;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 여행(그룹) 만들기: createTrip → (사진을 골랐으면) 대표 이미지 업로드 + updateTrip
public class GroupCreateActivity extends AuthenticatedActivity {
    public static final String EXTRA_TRIP_ID = "tripId";

    private static final int MAX_NAME_LENGTH = 60;
    private static final ExecutorService IMAGE_IO = Executors.newSingleThreadExecutor();

    private ActivityGroupCreateBinding binding;
    private TripRepository repository;

    // MaterialDatePicker가 돌려주는 값: 선택한 날짜의 UTC 자정(ms)
    private long startDateUtc;
    private long endDateUtc;
    private Uri photoUri;
    private boolean saving = false;

    // 재시도 때 같은 입력이면 같은 tripId를 써야 서버가 중복 생성하지 않는다.
    private String pendingTripId;
    private String pendingSignature;
    private String coverAssetId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isRedirectingToLogin()) {
            return;
        }
        WindowCompat.enableEdgeToEdge(getWindow());

        binding = ActivityGroupCreateBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        repository = new TripRepository(this);

        //사진 고르기 런처 등록
        ActivityResultLauncher<PickVisualMediaRequest> photoLauncher =
                registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                    //잠금 이미지선택 layout을 품
                    binding.imageArea.setEnabled(true);
                    //사용자가 안 고르고 뒤로 나가면 null
                    if (uri == null) {
                        TnLog.d(TnLog.GROUP, "대표 사진 선택 취소");
                        return;
                    }
                    TnLog.d(TnLog.GROUP, "대표 사진 선택됨");

                    photoUri = uri;                        //완료 버튼에서 업로드하려고 저장
                    coverAssetId = null;                   //사진이 바뀌면 새 파일로 올린다
                    binding.ivGroupImage.setImageURI(uri);      //미리보기
                    binding.tvAttach.setVisibility(View.GONE);
                });

        //back버튼
        binding.btnBack.setOnClickListener(view -> finish());

        //사진 첨부 영역 누르면 사진 선택기 띄우기
        binding.imageArea.setOnClickListener(view -> {
            //한번만 실행하도록 layout 잠금
            binding.imageArea.setEnabled(false);

            photoLauncher.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        //텍스트 변환 읽기
        binding.etTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                updateDoneButton();
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }
        });

        //기간선택
        binding.layoutDateRange.setOnClickListener(view -> {
            //두번 뜨는걸 방지
            binding.layoutDateRange.setEnabled(false);

            //오늘 이전 날짜는 선택 못 하게 막는 제약 조건 검사기 생성
            CalendarConstraints constraints = new CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointForward.now())
                    .build();

            MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText("여행기간 선택")
                    .setCalendarConstraints(constraints)
                    .build();

            picker.addOnPositiveButtonClickListener(selection -> {
                if (selection.first == null || selection.second == null) return;
                startDateUtc = selection.first;
                endDateUtc = selection.second;

                SimpleDateFormat fmt = new SimpleDateFormat("yyyy.MM.dd.", Locale.KOREA);
                fmt.setTimeZone(TimeZone.getTimeZone("UTC"));

                binding.tvStartDate.setText(fmt.format(new Date(startDateUtc)));
                binding.tvEndDate.setText(fmt.format(new Date(endDateUtc)));
                TnLog.d(TnLog.GROUP, "여행 기간 선택: " + fmt.format(new Date(startDateUtc))
                        + " ~ " + fmt.format(new Date(endDateUtc)));

                updateDoneButton();
            });

            //달력이 닫힐 때 잠금 풀어줌
            picker.addOnDismissListener(dialogInterface -> binding.layoutDateRange.setEnabled(true));

            picker.show(getSupportFragmentManager(), "DATE_PICKER");
        });

        //done버튼
        binding.btnDone.setOnClickListener(view -> createTrip());
        updateDoneButton();
    }

    private boolean isDone() {
        String title = binding.etTitle.getText() == null ? "" : binding.etTitle.getText().toString().trim();
        return startDateUtc != 0 && endDateUtc != 0 && !title.isEmpty() && title.length() <= MAX_NAME_LENGTH;
    }

    private void updateDoneButton() {
        binding.btnDone.setEnabled(isDone() && !saving);
    }

    private void createTrip() {
        if (!isDone() || saving) return;
        String name = binding.etTitle.getText().toString().trim();
        TimeZone zone = TimeZone.getDefault();
        // 달력에서 고른 날짜를 기기 시간대의 시작(00:00)과 마지막 날 끝(23:59:59.999)으로 바꾼다.
        long startAt = localStartOfDay(startDateUtc, zone);
        long endAt = localStartOfDay(endDateUtc, zone) + 24L * 60 * 60 * 1000 - 1;

        String signature = name + "|" + startAt + "|" + endAt + "|" + zone.getID();
        if (pendingTripId == null || !signature.equals(pendingSignature)) {
            // createTrip은 20~64자 무작위 ID를 요구한다. 하이픈 없는 UUID(32자)를 쓴다.
            pendingTripId = UUID.randomUUID().toString().replace("-", "");
            pendingSignature = signature;
        }
        String tripId = pendingTripId;

        TnLog.i(TnLog.GROUP, "여행 생성 요청: " + tripId + ", 시간대 " + zone.getID()
                + ", 대표사진 " + (photoUri != null ? "있음" : "없음"));
        setSaving(true);
        repository.createTrip(tripId, name, startAt, endAt, zone.getID())
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        TnLog.e(TnLog.GROUP, "여행 생성 실패", task.getException());
                        setSaving(false);
                        Toast.makeText(this, BackendErrors.forCreateTrip(task.getException()),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (photoUri == null) {
                        finishWithTrip(tripId);
                    } else {
                        uploadCover(tripId);
                    }
                });
    }

    // 여행은 이미 만들어졌으므로 대표 이미지가 실패해도 화면은 넘어간다.
    private void uploadCover(@NonNull String tripId) {
        if (coverAssetId == null) {
            coverAssetId = UUID.randomUUID().toString();
        }
        String assetId = coverAssetId;
        Uri uri = photoUri;
        Tasks.call(IMAGE_IO,
                        () -> JpegEncoder.encodeCover(getContentResolver(), uri))
                .continueWithTask(t -> repository.setCover(tripId, assetId, t.getResult()))
                .addOnCompleteListener(this, t -> {
                    if (!t.isSuccessful()) {
                        TnLog.w(TnLog.GROUP, "대표 사진 설정 실패(여행은 생성됨)", t.getException());
                        Toast.makeText(this, "여행은 만들었지만 대표 사진은 올리지 못했어요.",
                                Toast.LENGTH_LONG).show();
                    }
                    finishWithTrip(tripId);
                });
    }

    private void finishWithTrip(@NonNull String tripId) {
        TnLog.i(TnLog.GROUP, "여행 생성 화면 종료: " + tripId);
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_TRIP_ID, tripId));
        finish();
    }

    private void setSaving(boolean value) {
        saving = value;
        binding.btnDone.setText(value ? "만드는 중…" : "완료");
        binding.etTitle.setEnabled(!value);
        binding.layoutDateRange.setEnabled(!value);
        binding.imageArea.setEnabled(!value);
        updateDoneButton();
    }

    // UTC 자정 값의 연·월·일을 기기 시간대의 같은 날짜 00:00으로 옮긴다.
    static long localStartOfDay(long utcMidnight, @NonNull TimeZone zone) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(utcMidnight);
        Calendar local = Calendar.getInstance(zone);
        local.clear();
        local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        return local.getTimeInMillis();
    }
}
