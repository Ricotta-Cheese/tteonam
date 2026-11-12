package com.example.tteonam;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

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
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "camera";

    private Geocoder geocoder;
    private FusedLocationProviderClient fusedClient;

    private ActivityCameraBinding binding;
    private ProcessCameraProvider cameraProvider;
    private CameraControl cameraControl;
    private CameraInfo cameraInfo;

    // UseCase
    private Preview preview;
    private ImageCapture imageCapture;

    // Camera State
    private boolean isFlashOn = false;
    private boolean isFrontCamera = false;

    // Location
    private Location latestLocation;          // 가장 최근 위치(위치 정보 빠르게 저장하기 위해서)
    private LocationCallback locationCallback;

    private ImageLabeler labeler;

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
        initLocationCallback();

        setListeners();

        checkAndRequestPermissions();

        initImageLabeler();

        startCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startLocationUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (locationCallback != null) {
            fusedClient.removeLocationUpdates(locationCallback);   // 배터리 절약, 꼭 해제
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
        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
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
        Location location = latestLocation;

        String name = "IMG_" + System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/tteonam");

        // 위치를 사진 EXIF에 기록
        ImageCapture.Metadata metadata = new ImageCapture.Metadata();
        if (location != null) metadata.setLocation(location);

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        .setMetadata(metadata)
                        .build();

//        OnImageSavedCallback / OnImageCapturedCallback 택 1
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onError(@NonNull ImageCaptureException exc) {
                        Log.d(TAG, "사진 촬영 실패: " + exc.getMessage(), exc);
                    }

                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        Uri savedUri = output.getSavedUri();
                        String msg = (location != null)
                                ? "저장됨 (lat: " + location.getLatitude() + ", lng: " + location.getLongitude() + ")"
                                : "저장됨 (위치 정보 없음)";
                        Toast.makeText(CameraActivity.this, msg, Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "저장 Uri: " + savedUri);

                        // fromFilePath
                        try{
                            InputImage inputImage = InputImage.fromFilePath(getApplicationContext(), savedUri);


                            labeler.process(inputImage)
                                    .addOnSuccessListener(labels->{
                                        for(ImageLabel label : labels){
                                            String text = label.getText();
                                            float confidence = label.getConfidence();
                                            int index = label.getIndex();

                                            Log.d("Label",index+": "+confidence+", "+text);
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.d("Label","labelling failed");
                                    });
                        }
                        catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
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

    // ───────────────────────── ML kit ─────────────────────────
    private void initImageLabeler(){
        // 신뢰도 0.7 이상인 경우만
        ImageLabelerOptions options = new ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.7f)
                .build();

        labeler = ImageLabeling.getClient(options);
//        labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);
    }
}
