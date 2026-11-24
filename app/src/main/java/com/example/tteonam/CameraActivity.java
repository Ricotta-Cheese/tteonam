package com.example.tteonam;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.tteonam.auth.AuthenticatedActivity;
import com.example.tteonam.data.ServerClock;
import com.example.tteonam.databinding.ActivityCameraBinding;
import com.example.tteonam.util.TnLog;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AuthenticatedActivity {

    // 앱 전용 폴더(filesDir) 안의 사진 폴더 이름(휴대폰 갤러리에는 안 보임)
    public static final String PHOTO_DIR = "photos";

    private Geocoder geocoder;
    private FusedLocationProviderClient fusedClient;

    private ActivityCameraBinding binding;
    private ProcessCameraProvider cameraProvider;
    private CameraControl cameraControl;
    private CameraInfo cameraInfo;

    private Preview preview;
    private ImageCapture imageCapture;

    private boolean isFlashOn = false;
    private boolean isFrontCamera = false;

    // 가장 최근 위치(위치 정보 빠르게 저장하기 위해서)
    private LocationCallback locationCallback;

    private volatile Location latestLocation;

    // 위치 갱신을 받는 전용 스레드(메인 스레드 방해 X)
    private HandlerThread locationThread;

    // 최근 위치 인정 시간
    private static final long LOCATION_FRESH_MS = 10_000;

    // 회전 감지(CameraActivity만 회전)
    private OrientationEventListener orientationListener;

    // 촬영결과 + 파일저장 스레드
    private ExecutorService cameraExecutor;

    private PhotoDB photoDB;

    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                TnLog.i(TnLog.CAMERA, "권한 결과: camera=" + hasCameraPermission()
                        + ", location=" + hasLocationPermission());
                if (hasCameraPermission()) {
                    startCamera();
                } else {
                    Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                // 권한 창이 닫힌 뒤 위치 업데이트 시작
                startLocationUpdates();
            });

    // ───────────────────────── Lifecycle ─────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isRedirectingToLogin()) {   // 로그인 안 돼 있으면 LoginActivity로 이동 중 -> 화면 만들지 않음
            return;
        }

        WindowCompat.enableEdgeToEdge(getWindow());
        binding = ActivityCameraBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        geocoder = new Geocoder(this);
        locationThread = new HandlerThread("tn-location");
        locationThread.start();
        initLocationCallback();
        initOrientationListener();

        setListeners();

        photoDB = PhotoDB.get(this);
        cameraExecutor = Executors.newSingleThreadExecutor();

        // 권한이 이미 있으면 바로 시작하고, 없으면 권한 창 결과에서 시작한다.
        if (hasCameraPermission()) {
            startCamera();
        }
        checkAndRequestPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding == null) return;   // 로그인 화면으로 이동 중
        startLocationUpdates();
        orientationListener.enable();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (binding == null) return;
        orientationListener.disable();
        if (locationCallback != null) {
            fusedClient.removeLocationUpdates(locationCallback);   // 배터리 절약, 꼭 해제
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (locationThread != null) {
            locationThread.quitSafely();
        }
    }

    // ───────────────────────── Permission ─────────────────────────

    private void checkAndRequestPermissions() {
        List<String> needed = new ArrayList<>();
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }
        if (!needed.isEmpty()) {
            TnLog.d(TnLog.CAMERA, "권한 요청: " + needed);
            permissionLauncher.launch(needed.toArray(new String[0]));
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    // ───────────────────────── Location ─────────────────────────

    private void initLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                latestLocation = result.getLastLocation();
            }
        };
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        if (!hasLocationPermission() || locationCallback == null) return;

        // 앱을 열자마자 바로 찍는 경우 대비: 마지막으로 알려진 위치로 먼저 채워두기
        fusedClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null && latestLocation == null) latestLocation = location;
        });

        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .build();
        // 콜백을 위치 스레드에서
        fusedClient.requestLocationUpdates(request, locationCallback, locationThread.getLooper());
    }

    // 받아둔 위치가 최근 값인지 확인
    private boolean isFresh(Location location) {
        if (location == null) return false;
        long ageMs = (SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos()) / 1_000_000;
        return ageMs <= LOCATION_FRESH_MS;
    }

    // 사진 한 장의 위치. 촬영 직후에는 위치가 없을 수 있고, 저장(Room) 뒤에 도착할 수도 있다.
    private static final class PendingShot {
        private Location location;
        private long photoId = -1;   // Room에 저장되기 전에는 -1

        PendingShot(Location initial) {
            location = initial;
        }
    }

    // 새 위치를 따로 요청. 사진이 이미 저장됐으면 Room 행에 위치를 채워 넣는다.
    @SuppressLint("MissingPermission")
    private void requestFreshLocation(PendingShot shot) {
        if (!hasLocationPermission()) return;

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location == null) return;
                    latestLocation = location;
                    long savedId;
                    synchronized (shot) {
                        shot.location = location;   // 이 사진의 위치를 새 값으로 교체
                        savedId = shot.photoId;
                    }
                    if (savedId > 0) {
                        PhotoDB.dbExecutor.execute(() -> photoDB.photoDao()
                                .updateLocation(savedId, location.getLatitude(), location.getLongitude()));
                    }
                    TnLog.d(TnLog.CAMERA, "새 위치 도착" + (savedId > 0 ? " (저장된 사진 " + savedId + "에 반영)" : ""));
                })
                .addOnFailureListener(e -> TnLog.w(TnLog.CAMERA, "새 위치 요청 실패", e));
    }

    // 테스트용(위치 버튼)
    @SuppressLint("MissingPermission")
    private void captureLocation() {
        if (!hasLocationPermission()) {
            Toast.makeText(this, "위치 권한이 없어요", Toast.LENGTH_SHORT).show();
            return;
//            finish();
        }

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        Toast.makeText(this, "위치를 가져오지 못했어요", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // getFromLocation -> IOException
                    try{
                        double lat = location.getLatitude();
                        double lng = location.getLongitude();

                        String str = "lat: " + lat + " / lng: " + lng;
                        Toast.makeText(this, str, Toast.LENGTH_SHORT).show();

                        int maxIndex = 1;
                        List<Address> addresses = geocoder.getFromLocation(lat,lng,maxIndex);
                        if(addresses != null && !addresses.isEmpty()){
                            int index = 0;
                            for(Address address : addresses){
                                TnLog.d(TnLog.CAMERA, index + ": " + address.getAddressLine(0));
                                index++;
                            }
                        }
                    }
                    catch(IOException e){
                        TnLog.w(TnLog.CAMERA, "geocoder 오류", e);
                    }

                })
                .addOnFailureListener(e -> TnLog.w(TnLog.CAMERA, "위치 요청 실패", e));
    }

    // ───────────────────────── Orientation ─────────────────────────

    // CameraActivity만 가로세로 구분
    private void initOrientationListener() {
        orientationListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == ORIENTATION_UNKNOWN || imageCapture == null) return;

                int rotation;
                if (orientation >= 45 && orientation < 135) {
                    rotation = Surface.ROTATION_270;
                } else if (orientation >= 135 && orientation < 225) {
                    rotation = Surface.ROTATION_180;
                } else if (orientation >= 225 && orientation < 315) {
                    rotation = Surface.ROTATION_90;
                } else {
                    rotation = Surface.ROTATION_0;
                }
                imageCapture.setTargetRotation(rotation);
            }
        };
    }

    // ───────────────────────── Camera ─────────────────────────

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (Exception e) {
                TnLog.e(TnLog.CAMERA, "카메라 시작 실패", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        preview = new Preview.Builder().build();
        preview.setSurfaceProvider(binding.cameraPreview.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setTargetRotation(getDisplayRotation())
                .build();

        CameraSelector cameraSelector = isFrontCamera ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;

        try {
            cameraProvider.unbindAll();
            Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            cameraControl = camera.getCameraControl();
            TnLog.d(TnLog.CAMERA, "카메라 연결: " + (isFrontCamera ? "전면" : "후면"));
            cameraInfo = camera.getCameraInfo();
            isFlashOn = false;
        } catch (Exception e) {
            TnLog.e(TnLog.CAMERA, "카메라 바인딩 실패", e);
        }
    }

    private int getDisplayRotation() {
        Display display = binding.cameraPreview.getDisplay();
        if (display == null) {
            display = getWindowManager().getDefaultDisplay();
        }
        return display.getRotation();
    }

    private void setListeners() {
        binding.captureButton.setOnClickListener(v -> takeCapture());
        binding.flashButton.setOnClickListener(v -> toggleFlashlight());
        binding.transitionButton.setOnClickListener(v -> toggleCamera());
        binding.locationButton.setOnClickListener(v -> captureLocation());
    }

    private void takeCapture() {
        if (imageCapture == null) {
            TnLog.w(TnLog.CAMERA, "촬영 무시: 카메라 준비 전");
            return;
        }
        TnLog.i(TnLog.CAMERA, "촬영");

        // getCurrentLocation().getResult()로 기다리지 않고, 미리 받아둔 위치를 바로 사용
        // 받아둔 위치가 오래돼면 위치 요청 저장이 끝날 때 도착해 있으면 그걸 사용
        PendingShot shot = new PendingShot(latestLocation);
        if (!isFresh(latestLocation)) {
            TnLog.d(TnLog.CAMERA, "받아둔 위치가 없거나 오래됨 -> 새 위치 요청");
            requestFreshLocation(shot);
        }

        // 휴대폰 갤러리에 넣지 않고 앱 전용 폴더에만 저장
        long capturedAt = ServerClock.now();   // 기기 시계가 틀려도 서버 시각 기준으로 기록
        String name = "IMG_" + capturedAt + ".jpeg";
        File photoFile = new File(new File(getFilesDir(), PHOTO_DIR), name);

//        OnImageSavedCallback / OnImageCapturedCallback 택 1 -> 전용폴더 -> OnImageCapturedCallback
        imageCapture.takePicture(cameraExecutor,
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onError(@NonNull ImageCaptureException exc) {
                        TnLog.e(TnLog.CAMERA, "사진 촬영 실패", exc);
                        runOnUiThread(() -> Toast.makeText(CameraActivity.this,
                                "사진을 찍지 못했어요", Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        int rotation = image.getImageInfo().getRotationDegrees();

                        try {
                            saveJpeg(image, photoFile);
                        } catch (IOException e) {
                            TnLog.e(TnLog.CAMERA, "사진 저장 실패", e);
                            runOnUiThread(() -> Toast.makeText(CameraActivity.this,
                                    "사진을 저장하지 못했어요", Toast.LENGTH_SHORT).show());
                            return;
                        } finally {
                            image.close();   // 꼭 닫기, 안 닫으면 다음 촬영이 막힘
                        }

                        // cameraExecutor 스레드 -> 화면 관련 작업은 메인 스레드로
                        runOnUiThread(() -> onPhotoSaved(photoFile, capturedAt, rotation, shot));
                    }
                });
    }

    // ImageProxy(JPEG)를 그대로 파일로 저장
    private void saveJpeg(ImageProxy image, File photoFile) throws IOException {
        File dir = photoFile.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new IOException("사진 폴더 생성 실패: " + dir);
        }

        // 기본 설정의 ImageCapture는 JPEG 한 장 -> planes[0]에 파일 내용이 통째로 들어 있음
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        try (FileOutputStream out = new FileOutputStream(photoFile)) {
            out.write(bytes);
        }
    }

    private void onPhotoSaved(File photoFile, long capturedAt, int rotation, PendingShot shot) {
        Photo photo = new Photo();
        photo.path = photoFile.getPath();
        photo.capturedAt = capturedAt;
        photo.rotation = rotation;

        PhotoDB.dbExecutor.execute(() -> {
            long id;
            // 위치 도착과 저장이 겹쳐도 위치를 잃지 않도록 같은 잠금 안에서 처리
            synchronized (shot) {
                Location location = shot.location;
                photo.lat = (location != null) ? location.getLatitude() : null;
                photo.lng = (location != null) ? location.getLongitude() : null;
                id = photoDB.photoDao().insert(photo);
                shot.photoId = id;
            }
            // 정확한 좌표는 남기지 않고 위치 유무만 기록한다.
            TnLog.i(TnLog.CAMERA, "사진 저장: local=" + id + ", 회전 " + rotation + "°, 위치 "
                    + (photo.lat != null ? "있음" : "없음"));
            runOnUiThread(() -> openShare(id));
        });
    }

    // 사진 아이디를 ShareActivity로
    private void openShare(long photoId) {
        TnLog.i(TnLog.SCREEN, "CameraActivity → ShareActivity (local=" + photoId + ")");

        Intent intent = new Intent(CameraActivity.this, ShareActivity.class);
        intent.putExtra(ShareActivity.EXTRA_PHOTO_ID, photoId);
        startActivity(intent);
    }

    private void toggleFlashlight() {
        if (cameraControl == null || cameraInfo == null) return;

        if (!cameraInfo.hasFlashUnit()) {
            Toast.makeText(this, "이 카메라는 플래시가 없습니다", Toast.LENGTH_SHORT).show();
            return;
        }

        isFlashOn = !isFlashOn;
        TnLog.d(TnLog.CAMERA, "플래시 " + (isFlashOn ? "켬" : "끔"));
        cameraControl.enableTorch(isFlashOn);
    }

    private void toggleCamera() {
        if (cameraProvider == null) return;
        isFrontCamera = !isFrontCamera;
        TnLog.d(TnLog.CAMERA, "카메라 전환: " + (isFrontCamera ? "전면" : "후면"));
        bindCameraUseCases();
    }
}
