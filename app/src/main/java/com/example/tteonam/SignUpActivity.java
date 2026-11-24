package com.example.tteonam;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.tteonam.auth.AuthValidator;
import com.example.tteonam.auth.SignUpViewModel;
import com.example.tteonam.util.TnLog;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class SignUpActivity extends AppCompatActivity {
    public static final String EXTRA_SIGNED_UP_EMAIL = "signed_up_email";

    private SignUpViewModel viewModel;

    private TextInputLayout nicknameLayout;
    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputLayout confirmLayout;
    private TextInputEditText nicknameInput;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmInput;
    private CheckBox termsAll;
    private CheckBox termsService;
    private CheckBox termsPrivacy;
    private CheckBox termsLocation;
    private TextView formErrorText;
    private Button signUpButton;
    private Button retryProfileButton;
    private ProgressBar progress;

    private boolean updatingChecks = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TnLog.i(TnLog.SCREEN, "SignUpActivity 열림");
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_sign_up);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        bindViews();
        setUpListeners();

        viewModel = new ViewModelProvider(this).get(SignUpViewModel.class);
        viewModel.getState().observe(this, this::render);
    }

    private void bindViews() {
        nicknameLayout = findViewById(R.id.nickname_layout);
        emailLayout = findViewById(R.id.email_layout);
        passwordLayout = findViewById(R.id.password_layout);
        confirmLayout = findViewById(R.id.confirm_layout);
        nicknameInput = findViewById(R.id.nickname_input);
        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);
        confirmInput = findViewById(R.id.confirm_input);
        termsAll = findViewById(R.id.terms_all_check);
        termsService = findViewById(R.id.terms_service_check);
        termsPrivacy = findViewById(R.id.terms_privacy_check);
        termsLocation = findViewById(R.id.terms_location_check);
        formErrorText = findViewById(R.id.form_error_text);
        signUpButton = findViewById(R.id.sign_up_button);
        retryProfileButton = findViewById(R.id.retry_profile_button);
        progress = findViewById(R.id.sign_up_progress);

        // TODO: 닉네임 카운터를 코드포인트 기준으로 통일한다.
        nicknameLayout.setCounterMaxLength(AuthValidator.NICKNAME_MAX_LENGTH);
        updatePasswordRules("");
    }

    private void setUpListeners() {
        TextWatcher watcher = new SimpleTextWatcher(this::refreshForm);
        nicknameInput.addTextChangedListener(watcher);
        emailInput.addTextChangedListener(watcher);
        passwordInput.addTextChangedListener(watcher);
        confirmInput.addTextChangedListener(watcher);

        termsAll.setOnCheckedChangeListener((button, checked) -> {
            if (updatingChecks) {
                return;
            }
            updatingChecks = true;
            termsService.setChecked(checked);
            termsPrivacy.setChecked(checked);
            termsLocation.setChecked(checked);
            updatingChecks = false;
            refreshForm();
        });

        CheckBox[] items = {termsService, termsPrivacy, termsLocation};
        for (CheckBox item : items) {
            item.setOnCheckedChangeListener((button, checked) -> {
                if (updatingChecks) {
                    return;
                }
                updatingChecks = true;
                termsAll.setChecked(allTermsAccepted());
                updatingChecks = false;
                refreshForm();
            });
        }

        signUpButton.setOnClickListener(v -> viewModel.signUp(
                textOf(nicknameInput), textOf(emailInput),
                textOf(passwordInput), textOf(confirmInput),
                allTermsAccepted()));
        retryProfileButton.setOnClickListener(v -> viewModel.retryProfile());

        // 위쪽 바의 뒤로 가기 화살표: 기기의 뒤로 가기와 똑같이 동작한다.
        findViewById(R.id.back_button).setOnClickListener(v ->
                getOnBackPressedDispatcher().onBackPressed());
    }

    private void refreshForm() {
        String nickname = AuthValidator.normalizeNickname(textOf(nicknameInput));
        String email = AuthValidator.normalizeEmail(textOf(emailInput));
        String password = textOf(passwordInput);
        String confirm = textOf(confirmInput);

        String nicknameError = AuthValidator.validateNickname(nickname);
        String emailError = AuthValidator.validateEmail(email);
        String passwordError = AuthValidator.validateSignUpPassword(password);
        String confirmError = AuthValidator.validatePasswordConfirm(password, confirm);

        nicknameLayout.setError(textOf(nicknameInput).isEmpty() ? null : nicknameError);
        emailLayout.setError(email.isEmpty() ? null : emailError);
        updatePasswordRules(password);
        if (confirm.isEmpty()) {
            confirmLayout.setError(null);
            confirmLayout.setHelperText(null);
        } else if (confirmError != null) {
            confirmLayout.setError(confirmError);
        } else {
            confirmLayout.setError(null);
            confirmLayout.setHelperText(getString(R.string.sign_up_password_match));
            confirmLayout.setHelperTextColor(colorList(R.color.tn_success));
        }

        boolean allValid = nicknameError == null && emailError == null
                && passwordError == null && confirmError == null && allTermsAccepted();
        SignUpViewModel.UiState state = viewModel == null ? null : viewModel.getState().getValue();
        boolean idle = state == null || state.status == SignUpViewModel.Status.IDLE;
        signUpButton.setEnabled(allValid && idle);
    }

    private void updatePasswordRules(String password) {
        String text = mark(AuthValidator.hasMinLength(password)) + " 8자 이상   "
                + mark(AuthValidator.hasAsciiLetter(password)) + " 영문   "
                + mark(AuthValidator.hasDigit(password)) + " 숫자";
        passwordLayout.setHelperText(text);
        // 세 규칙을 모두 지키면 초록색, 아니면 회색으로 보여 준다.
        boolean allOk = AuthValidator.validateSignUpPassword(password) == null;
        passwordLayout.setHelperTextColor(
                colorList(allOk ? R.color.tn_success : R.color.tn_text_secondary));
    }

    private ColorStateList colorList(int colorRes) {
        return ColorStateList.valueOf(ContextCompat.getColor(this, colorRes));
    }

    private static String mark(boolean ok) {
        return ok ? "✓" : "✗";
    }

    private boolean allTermsAccepted() {
        return termsService.isChecked() && termsPrivacy.isChecked() && termsLocation.isChecked();
    }

    private void render(SignUpViewModel.UiState state) {
        if (state.status == SignUpViewModel.Status.SUCCESS) {
            finishWithSuccess(state.email);
            return;
        }

        boolean busy = state.isBusy();
        boolean profileFailed = state.status == SignUpViewModel.Status.PROFILE_FAILED;

        boolean lockInputs = busy || profileFailed;

        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        View[] inputs = {nicknameInput, emailInput, passwordInput, confirmInput,
                termsAll, termsService, termsPrivacy, termsLocation};
        for (View input : inputs) {
            input.setEnabled(!lockInputs);
        }
        retryProfileButton.setVisibility(profileFailed ? View.VISIBLE : View.GONE);
        signUpButton.setVisibility(profileFailed ? View.GONE : View.VISIBLE);

        if (state.message != null) {
            formErrorText.setText(state.message);
            formErrorText.setVisibility(View.VISIBLE);
        } else {
            formErrorText.setVisibility(View.GONE);
        }

        refreshForm();
    }

    private void finishWithSuccess(String email) {
        Intent result = new Intent();
        result.putExtra(EXTRA_SIGNED_UP_EMAIL, email);
        setResult(RESULT_OK, result);
        finish();
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    private static final class SimpleTextWatcher implements TextWatcher {
        private final Runnable onChanged;

        SimpleTextWatcher(Runnable onChanged) {
            this.onChanged = onChanged;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            onChanged.run();
        }
    }
}
