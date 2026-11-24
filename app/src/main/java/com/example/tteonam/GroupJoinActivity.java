package com.example.tteonam;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.tteonam.auth.AuthenticatedActivity;
import com.example.tteonam.data.BackendErrors;
import com.example.tteonam.data.InviteCodes;
import com.example.tteonam.data.TripRepository;
import com.example.tteonam.databinding.ActivityGroupJoinBinding;
import com.example.tteonam.util.TnLog;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import java.util.Collections;

// 초대 QR을 스캔하거나 6자리 영숫자 코드를 입력해 joinTrip을 호출한다.
public class GroupJoinActivity extends AuthenticatedActivity {
    private ActivityGroupJoinBinding binding;
    private EditText[] boxes;
    private boolean joining = false;
    private boolean cameraGranted = false;

    private final ActivityResultLauncher<String> cameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                cameraGranted = granted;
                TnLog.i(TnLog.JOIN, "카메라 권한 결과: " + granted);
                if (granted) {
                    binding.barcodeView.resume();
                } else {
                    Toast.makeText(this, "카메라 권한이 없으면 코드를 직접 입력해 주세요", Toast.LENGTH_SHORT).show();
                }
            });

    // 초대 QR에는 6자리 코드만 들어 있다(GroupManageActivity.makeQr).
    private final BarcodeCallback scanCallback = result -> {
        String code = InviteCodes.normalize(result.getText());
        if (code == null) {
            TnLog.d(TnLog.JOIN, "초대 코드가 아닌 QR 무시");
            return;
        }
        if (joining) return;
        TnLog.i(TnLog.JOIN, "QR에서 초대 코드 인식");
        fillCode(code);
        join(code);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isRedirectingToLogin()) {
            return;
        }

        WindowCompat.enableEdgeToEdge(getWindow());

        binding = ActivityGroupJoinBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //코드 넣는 editText 모두 인플레이션
        boxes = new EditText[]{binding.etCode1, binding.etCode2, binding.etCode3,
                binding.etCode4, binding.etCode5, binding.etCode6};

        for (int i = 0; i < boxes.length; i++) {
            final int index = i;
            boxes[i].setFilters(new InputFilter[]{
                    new InputFilter.AllCaps(), new InputFilter.LengthFilter(1)});
            boxes[i].addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(Editable editable) {
                    //코드 하나를 작성했을 때 다음 칸으로 이동
                    if (boxes[index].getText().length() == 1 && index < boxes.length - 1) {
                        boxes[index + 1].requestFocus();
                    } else if (boxes[index].getText().length() == 1 && index == boxes.length - 1) {
                        boxes[index].clearFocus();
                        hideKeyboard(boxes[index]);
                    }
                    binding.btnJoin.setEnabled(!joining && InviteCodes.isValid(currentCode()));
                }

                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }
            });

            boxes[i].setOnKeyListener((view, keyCode, event) -> { //입력받은 뷰, 어떤 키?, 어떻게 눌렸는지
                if (event.getAction() == KeyEvent.ACTION_DOWN      // ① 누를 때만
                        && keyCode == KeyEvent.KEYCODE_DEL         // ② 백스페이스이고
                        && boxes[index].getText().length() == 0    // ③ 지금 칸이 비어 있고
                        && index > 0) {                            // ④ 첫 칸이 아니면
                    boxes[index - 1].setText("");                  // 이전 칸 글자 지우고
                    boxes[index - 1].requestFocus();               // 이전 칸으로 커서 이동
                    return true;                                   // 내가 처리했음
                }
                return false;                                      // 나머지는 원래대로
            });
        }

        binding.btnJoin.setOnClickListener(v -> {
            String code = currentCode();
            if (InviteCodes.isValid(code)) {
                TnLog.i(TnLog.JOIN, "코드 직접 입력으로 참여");
                join(code);
            }
        });

        //back버튼
        binding.btnBack.setOnClickListener(view -> finish());

        // QR 스캔 준비
        binding.barcodeView.setDecoderFactory(
                new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE)));
        binding.barcodeView.decodeContinuous(scanCallback);
        cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        if (!cameraGranted) {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null && cameraGranted && !joining) binding.barcodeView.resume();
    }

    @Override
    protected void onPause() {
        if (binding != null) binding.barcodeView.pause();
        super.onPause();
    }

    @NonNull
    private String currentCode() {
        StringBuilder sb = new StringBuilder();
        for (EditText box : boxes) sb.append(box.getText());
        return sb.toString();
    }

    private void fillCode(@NonNull String code) {
        for (int i = 0; i < boxes.length && i < code.length(); i++) {
            boxes[i].setText(String.valueOf(code.charAt(i)));
        }
    }

    private void join(@NonNull String code) {
        if (joining) return;
        setJoining(true);
        // 초대 코드 원문은 남기지 않는다.
        new TripRepository(this).joinTrip(code).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                TnLog.i(TnLog.JOIN, "참여 성공: " + task.getResult());
                Toast.makeText(this, "여행에 참여했어요", Toast.LENGTH_SHORT).show();
                if (getCallingActivity() == null) {
                    // 홈에서 바로 들어왔으면 그룹 관리 화면으로 이동
                    startActivity(new Intent(this, GroupManageActivity.class));
                } else {
                    setResult(RESULT_OK);
                }
                finish();
                return;
            }
            TnLog.w(TnLog.JOIN, "참여 실패", task.getException());
            Toast.makeText(this, BackendErrors.forJoin(task.getException()), Toast.LENGTH_LONG).show();
            setJoining(false);
        });
    }

    private void setJoining(boolean value) {
        joining = value;
        binding.btnJoin.setEnabled(!value && InviteCodes.isValid(currentCode()));
        binding.btnJoin.setText(value ? "참여하는 중…" : "그룹 참여하기");
        for (EditText box : boxes) box.setEnabled(!value);
        if (value) {
            binding.barcodeView.pause();
        } else if (cameraGranted) {
            binding.barcodeView.resume();
        }
    }

    private void hideKeyboard(@NonNull View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
}
