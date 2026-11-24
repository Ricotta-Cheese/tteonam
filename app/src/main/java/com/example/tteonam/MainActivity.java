package com.example.tteonam;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.tteonam.auth.AuthValidator;
import com.example.tteonam.auth.AuthenticatedActivity;
import com.example.tteonam.auth.HomeViewModel;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class MainActivity extends AuthenticatedActivity {
    private HomeViewModel viewModel;

    private View loadingSection;
    private View homeSection;
    private View profileSection;
    private View errorSection;

    private TextView welcomeText;

    private TextInputLayout nicknameLayout;
    private TextInputEditText nicknameInput;
    private TextView profileErrorText;
    private Button saveProfileButton;

    private TextView errorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (isRedirectingToLogin()) {
            return;
        }

        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        bindViews();

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        viewModel.getState().observe(this, this::render);
        viewModel.start();
    }

    private void bindViews() {
        loadingSection = findViewById(R.id.loading_section);
        homeSection = findViewById(R.id.home_section);
        profileSection = findViewById(R.id.profile_section);
        errorSection = findViewById(R.id.error_section);

        welcomeText = findViewById(R.id.welcome_text);

        nicknameLayout = findViewById(R.id.nickname_layout);
        nicknameInput = findViewById(R.id.nickname_input);
        profileErrorText = findViewById(R.id.profile_error_text);
        saveProfileButton = findViewById(R.id.save_profile_button);

        // TODO: 닉네임 카운터를 코드포인트 기준으로 통일한다.
        nicknameLayout.setCounterMaxLength(AuthValidator.NICKNAME_MAX_LENGTH);

        errorText = findViewById(R.id.error_text);

        saveProfileButton.setOnClickListener(v -> {
            CharSequence text = nicknameInput.getText();
            viewModel.saveProfile(text == null ? "" : text.toString());
        });
        findViewById(R.id.retry_button).setOnClickListener(v -> viewModel.retry());

        View.OnClickListener logout = v -> viewModel.logout();
        findViewById(R.id.profile_logout_button).setOnClickListener(logout);
        findViewById(R.id.error_logout_button).setOnClickListener(logout);
        // 홈 오른쪽 위 계정 아이콘: 누르면 "로그아웃" 메뉴를 띄운다.
        findViewById(R.id.logout_button).setOnClickListener(this::showAccountMenu);

        // 지금 화면은 홈이므로 홈 탭을 "선택됨"으로 표시한다 (아이콘·글자가 주황색이 된다).
        findViewById(R.id.nav_home).setSelected(true);

        // 하단 탭·버튼: 아직 화면 내용은 비어 있지만 기획서의 화면 이동 순서대로 연결해 둔다.
        openOnClick(R.id.camera_button, CameraActivity.class);       // 04 촬영
        openOnClick(R.id.nav_map, PhotoMapActivity.class);           // 06 지도
        openOnClick(R.id.nav_album, AlbumActivity.class);            // 07 앨범
        openOnClick(R.id.nav_retro, RetroListActivity.class);        // 12 회고 모음
        openOnClick(R.id.group_button, GroupManageActivity.class);   // 13 그룹 관리
        openOnClick(R.id.create_trip_button, GroupManageActivity.class);
        openOnClick(R.id.join_trip_button, GroupJoinActivity.class); // 14 그룹 참여
    }

    private void openOnClick(int viewId, Class<?> screen) {
        findViewById(viewId).setOnClickListener(v -> startActivity(new Intent(this, screen)));
    }

    private void showAccountMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.inflate(R.menu.menu_account);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                viewModel.logout();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void render(HomeViewModel.UiState state) {
        if (state.status == HomeViewModel.Status.SIGNED_OUT) {
            goToLogin();
            return;
        }

        boolean saving = state.status == HomeViewModel.Status.SAVING_PROFILE;

        showOnly(state.status == HomeViewModel.Status.LOADING ? loadingSection
                : state.status == HomeViewModel.Status.READY ? homeSection
                : state.status == HomeViewModel.Status.ERROR ? errorSection
                : profileSection);

        switch (state.status) {
            case READY:
                welcomeText.setText(getString(R.string.home_welcome, state.nickname));
                break;
            case ERROR:
                errorText.setText(state.message);
                break;
            case NEEDS_PROFILE:
            case SAVING_PROFILE:
                nicknameLayout.setError(state.nicknameError);
                profileErrorText.setText(state.message);
                profileErrorText.setVisibility(state.message == null ? View.GONE : View.VISIBLE);
                saveProfileButton.setEnabled(!saving);
                nicknameInput.setEnabled(!saving);
                saveProfileButton.setText(saving
                        ? R.string.action_saving : R.string.action_save_profile);
                break;
            default:
                break;
        }
    }

    private void showOnly(View target) {
        View[] sections = {loadingSection, homeSection, profileSection, errorSection};
        for (View section : sections) {
            section.setVisibility(section == target ? View.VISIBLE : View.GONE);
        }
    }
}
