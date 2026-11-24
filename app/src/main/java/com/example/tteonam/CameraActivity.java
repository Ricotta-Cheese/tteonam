package com.example.tteonam;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.tteonam.auth.AuthenticatedActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.internal.utils.ImageUtil;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.room.Room;

import com.example.tteonam.ShareActivity;
import com.example.tteonam.databinding.ActivityCameraBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class CameraActivity extends AuthenticatedActivity {

    private static final String TAG = "tn";

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

    private ImageLabeler labeler;

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
                if (Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA))) {
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
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        binding = ActivityCameraBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        geocoder = new Geocoder(this);
        locationThread = new HandlerThread("tn-location");
        locationThread.start();
        initLocationCallback();
        initOrientationListener();

        setListeners();

        checkAndRequestPermissions();

        photoDB = PhotoDB.get(this);

//        initImageLabeler();

        cameraExecutor = Executors.newSingleThreadExecutor();

        startCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startLocationUpdates();
        orientationListener.enable();
    }

    @Override
    protected void onPause() {
        super.onPause();
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
            permissionLauncher.launch(needed.toArray(new String[0]));
        }
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

    // 새 위치를 따로 요청
    @SuppressLint("MissingPermission")
    private void requestFreshLocation(AtomicReference<Location> target) {
        if (!hasLocationPermission()) return;

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location == null) return;
                    latestLocation = location;
                    target.set(location);   // 이 사진의 위치를 새 값으로 교체
                    Log.d(TAG, "새 위치 도착");
                })
                .addOnFailureListener(e -> Log.d(TAG, "새 위치 요청 실패", e));
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
                                Log.d(TAG,index + ": " + address.getAddressLine(0));
                                index++;
                            }
                        }
                    }
                    catch(IOException e){
                        e.printStackTrace();
                        Log.e(TAG,"geocoder 오류");
                    }

                })
                .addOnFailureListener(e -> Log.d(TAG, "위치 요청 실패", e));
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
                Log.d(TAG, "카메라 시작 실패", e);
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
            cameraInfo = camera.getCameraInfo();
            isFlashOn = false;
        } catch (Exception e) {
            Log.d(TAG, "카메라 바인딩 실패", e);
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
        if (imageCapture == null) return;

        // getCurrentLocation().getResult()로 기다리지 않고, 미리 받아둔 위치를 바로 사용
        // 받아둔 위치가 오래돼면 위치 요청 저장이 끝날 때 도착해 있으면 그걸 사용
        AtomicReference<Location> shotLocation = new AtomicReference<>(latestLocation);
        if (!isFresh(shotLocation.get())) {
            Log.d(TAG, "받아둔 위치가 없거나 오래됨 -> 새 위치 요청");
            requestFreshLocation(shotLocation);
        }

        // 휴대폰 갤러리에 넣지 않고 앱 전용 폴더에만 저장
        Long capturedAt = System.currentTimeMillis();
        String name = "IMG_" + capturedAt + ".jpeg";
        File photoFile = new File(new File(getFilesDir(), PHOTO_DIR), name);

//        OnImageSavedCallback / OnImageCapturedCallback 택 1 -> 전용폴더 -> OnImageCapturedCallback
        imageCapture.takePicture(cameraExecutor,
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onError(@NonNull ImageCaptureException exc) {
                        Log.d(TAG, "사진 촬영 실패: " + exc.getMessage(), exc);
                    }

                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        int rotation = image.getImageInfo().getRotationDegrees();

                        try {
                            saveJpeg(image, photoFile);
                        } catch (IOException e) {
                            Log.e(TAG, "사진 저장 실패", e);
                            return;
                        } finally {
                            image.close();   // 꼭 닫기, 안 닫으면 다음 촬영이 막힘
                        }

                        // cameraExecutor 스레드 -> 화면 관련 작업은 메인 스레드로
                        runOnUiThread(() -> onPhotoSaved(photoFile, capturedAt, rotation , shotLocation.get()));
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

    private void onPhotoSaved(File photoFile, long capturedAt, int rotation, Location location) {
//        Uri savedUri = Uri.fromFile(photoFile);

        Photo photo = new Photo();
        photo.path = photoFile.getPath();
        photo.capturedAt = capturedAt;
        photo.rotation = rotation;
        photo.lat = (location != null) ? location.getLatitude() : null;
        photo.lng = (location != null) ? location.getLongitude() : null;
        photo.uploaded = false;

        PhotoDB.dbExecutor.execute(() -> {
            long id = photoDB.photoDao().insert(photo);
            runOnUiThread(() -> openShare(id));
        });
    }

    // 사진 아이디를 ShareActivity로
    private void openShare(long photoId) {
        Log.d(TAG, "share act 실행");

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
        cameraControl.enableTorch(isFlashOn);
    }

    private void toggleCamera() {
        if (cameraProvider == null) return;
        isFrontCamera = !isFrontCamera;
        bindCameraUseCases();
    }

//    // ───────────────────────── ML kit ─────────────────────────
//    private void initImageLabeler(){
//        // 신뢰도 0.7 이상인 경우만
//        ImageLabelerOptions options = new ImageLabelerOptions.Builder()
//                .setConfidenceThreshold(0.7f)
//                .build();
//
//        labeler = ImageLabeling.getClient(options);
////        labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);
//    }
}