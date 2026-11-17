package com.example.tteonam;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.tteonam.databinding.ActivityShareBinding;

import java.io.File;

public class ShareActivity extends AppCompatActivity {

    private static final String TAG = "tn";

    // Intent 키

    // 사진 파일 경로
    public static final String EXTRA_PHOTO_PATH = "photo_path";

    // 돌려야 하는 각도
    public static final String EXTRA_ROTATION = "rotation";

    // 위도
    public static final String EXTRA_LAT = "lat";

    // 경도
    public static final String EXTRA_LNG = "lng";

    private ActivityShareBinding binding;

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

        setListeners();

        showPhoto();
    }

    private void setListeners() {
        binding.backButton.setOnClickListener(v -> finish());
    }

    // ───────────────────────── Photo ─────────────────────────

    private void showPhoto() {
        String photoPath = getIntent().getStringExtra(EXTRA_PHOTO_PATH);
        int rotation = getIntent().getIntExtra(EXTRA_ROTATION, 0);

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

        // 테스트용(파일 이름 + 위치 표시)
        String info = new File(photoPath).getName();
        if (getIntent().hasExtra(EXTRA_LAT) && getIntent().hasExtra(EXTRA_LNG)) {
            double lat = getIntent().getDoubleExtra(EXTRA_LAT, 0);
            double lng = getIntent().getDoubleExtra(EXTRA_LNG, 0);
            info += "\nlat: " + lat + " / lng: " + lng;
        } else {
            info += "\n위치 정보 없음";
        }
        binding.infoText.setText(info);
        Log.d(TAG, "사진 공유: " + photoPath + ", rotation: " + rotation);
    }

}
