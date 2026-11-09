package com.example.tteonam;

import android.os.Bundle;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class SignUpActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_sign_up);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        EditText nicknameInput = findViewById(R.id.nickname_input);
        EditText emailInput = findViewById(R.id.email_input);
        EditText passwordInput = findViewById(R.id.password_input);
        EditText confirmInput = findViewById(R.id.confirm_input);

        findViewById(R.id.sign_up_button).setOnClickListener(v -> {
            // 입력칸을 계속 다시 읽고 있음. 검사 부분은 나중에 따로 빼기.
            if (nicknameInput.getText().toString().trim().isEmpty()) {
                nicknameInput.setError(getString(R.string.nickname_required));
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(emailInput.getText().toString().trim()).matches()) {
                emailInput.setError(getString(R.string.email_invalid));
                return;
            }
            if (passwordInput.getText().toString().length() < 8) {
                passwordInput.setError(getString(R.string.password_length_error));
                return;
            }
            if (!passwordInput.getText().toString().equals(confirmInput.getText().toString())) {
                confirmInput.setError(getString(R.string.password_mismatch));
                return;
            }

            // TODO: 계정 생성과 닉네임 저장 연결
            // Auth 계정이 만들어지면 그 UID로 users 문서도 만들어야 함.
            Toast.makeText(this, R.string.sign_up_not_connected, Toast.LENGTH_LONG).show();
        });

        findViewById(R.id.back_to_login_button).setOnClickListener(v -> finish());
    }
}
