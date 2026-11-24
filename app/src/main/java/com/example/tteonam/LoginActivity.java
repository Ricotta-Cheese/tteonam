package com.example.tteonam;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.tteonam.auth.LoginViewModel;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends AppCompatActivity {
    private static final String PERMISSION_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK";

    private LoginViewModel viewModel;

    private final ActivityResultLauncher<Intent> signUpLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    this::onSignUpFinished);

    private final ActivityResultLauncher<String> localNetworkPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            });

    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private TextView formErrorText;
    private TextView infoText;
    private Button loginButton;
    private TextView signUpButton;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        if (viewModel.isAlreadySignedIn()) {
            goToHome();
            return;
        }

        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_login);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        bindViews();
        setUpListeners();
        requestLocalNetworkPermissionForEmulator();

        viewModel.getState().observe(this, this::render);
    }

    private void bindViews() {
        emailLayout = findViewById(R.id.email_layout);
        passwordLayout = findViewById(R.id.password_layout);
        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);
        formErrorText = findViewById(R.id.form_error_text);
        infoText = findViewById(R.id.info_text);
        loginButton = findViewById(R.id.login_button);
        signUpButton = findViewById(R.id.sign_up_button);
        progress = findViewById(R.id.login_progress);
    }

    private void setUpListeners() {
        loginButton.setOnClickListener(v -> submit());

        passwordInput.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });

        signUpButton.setOnClickListener(v ->
                signUpLauncher.launch(new Intent(this, SignUpActivity.class)));

        // 비밀번호 찾기·Google 로그인은 다음 단계에서 연결한다. 지금은 준비 중 안내만 한다.
        View.OnClickListener comingSoon = v ->
                Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
        findViewById(R.id.forgot_password_button).setOnClickListener(comingSoon);
        findViewById(R.id.google_button).setOnClickListener(comingSoon);
    }

    private void requestLocalNetworkPermissionForEmulator() {
        if (!BuildConfig.USE_FIREBASE_EMULATOR || Build.VERSION.SDK_INT < 37) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, PERMISSION_LOCAL_NETWORK)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        localNetworkPermissionLauncher.launch(PERMISSION_LOCAL_NETWORK);
    }

    private void onSignUpFinished(ActivityResult result) {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
            return;
        }
        String email = result.getData().getStringExtra(SignUpActivity.EXTRA_SIGNED_UP_EMAIL);
        if (email != null) {
            emailInput.setText(email);
        }
        passwordInput.setText("");
        passwordInput.requestFocus();
        infoText.setVisibility(View.VISIBLE);
    }

    private void submit() {
        infoText.setVisibility(View.GONE);
        viewModel.signIn(textOf(emailInput), textOf(passwordInput));
    }

    private void render(LoginViewModel.UiState state) {
        if (state.status == LoginViewModel.Status.SUCCESS) {
            goToHome();
            return;
        }

        boolean loading = state.status == LoginViewModel.Status.LOADING;

        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!loading);
        signUpButton.setEnabled(!loading);
        emailInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);

        emailLayout.setError(state.emailError);
        passwordLayout.setError(state.passwordError);

        if (state.formError != null) {
            formErrorText.setText(state.formError);
            formErrorText.setVisibility(View.VISIBLE);
        } else {
            formErrorText.setVisibility(View.GONE);
        }
    }

    private void goToHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
