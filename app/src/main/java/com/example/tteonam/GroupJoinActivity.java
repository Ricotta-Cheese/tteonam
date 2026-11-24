package com.example.tteonam;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import com.example.tteonam.auth.AuthenticatedActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.tteonam.databinding.ActivityGroupJoinBinding;

public class GroupJoinActivity extends AuthenticatedActivity {
    ActivityGroupJoinBinding binding;
    private EditText[] boxes;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.enableEdgeToEdge(getWindow());

        binding = ActivityGroupJoinBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //코드 넣는 editText 모두 인플레이션
        boxes = new EditText[]{binding.etCode1, binding.etCode2, binding.etCode3,
                binding.etCode4, binding.etCode5, binding.etCode6};

        for(int i = 0; i < boxes.length; i++) {
            final int index = i;
            boxes[i].addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(Editable editable) {
                    //코드 하나를 작성했을 때 다음 칸으로 이동
                    if(boxes[index].getText().length() == 1 && index < boxes.length-1){
                        boxes[index+1].requestFocus();
                    }
                    else if (boxes[index].getText().length() == 1 && index == boxes.length-1){
                        boxes[index].clearFocus();
                        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(boxes[index].getWindowToken(), 0);
                    }
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

        //back버튼
        binding.btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }
}
