package com.example.tteonam;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tteonam.auth.AuthenticatedActivity;
import com.example.tteonam.data.BackendErrors;
import com.example.tteonam.data.FirebaseProvider;
import com.example.tteonam.data.InviteCodes;
import com.example.tteonam.data.JpegEncoder;
import com.example.tteonam.data.TripRepository;
import com.example.tteonam.databinding.ActivityGroupManageBinding;
import com.example.tteonam.databinding.ItemMemberBinding;
import com.example.tteonam.util.TnLog;
import com.google.firebase.auth.FirebaseUser;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

// 현재 여행의 정보·멤버·초대 코드. 여행 데이터는 서버(trips/{tripId})에서 읽는다.
public class GroupManageActivity extends AuthenticatedActivity {
    private ActivityGroupManageBinding binding;
    private TripRepository repository;

    @Nullable private TripRepository.TripInfo trip;
    @Nullable private TripRepository.Invite invite;
    private final ArrayList<Member> members = new ArrayList<>();
    private MemberAdapter adapter;
    private boolean issuingInvite = false;

    // 만료 시간 표시를 1분마다 갱신
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable expiryTicker = new Runnable() {
        @Override
        public void run() {
            renderInvite();
            handler.postDelayed(this, 30_000);
        }
    };

    private final ActivityResultLauncher<Intent> tripLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                // 생성·참여가 끝나면 서버에서 다시 읽는다(현재 여행은 저장소에 기록돼 있음).
                TnLog.d(TnLog.GROUP, "생성/참여 화면 결과: " + result.getResultCode());
                if (result.getResultCode() == RESULT_OK) {
                    loadTrip();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isRedirectingToLogin()) {
            return;
        }

        WindowCompat.enableEdgeToEdge(getWindow());
        binding = ActivityGroupManageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        repository = new TripRepository(this);

        adapter = new MemberAdapter(members);
        binding.rvMembers.setLayoutManager(new LinearLayoutManager(this));
        binding.rvMembers.setAdapter(adapter);

        //코드복사터치
        binding.btnCopy.setOnClickListener(view -> {
            if (invite == null) return;
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("inviteCode", invite.code));
            TnLog.d(TnLog.GROUP, "초대 코드 복사");
            Toast.makeText(this, "복사완료", Toast.LENGTH_SHORT).show();
        });

        // 코드가 없거나 만료됐으면 눌러서 새로 발급
        View.OnClickListener issue = view -> {
            if (invite == null || invite.expiresAt <= System.currentTimeMillis()) issueInvite();
        };
        binding.tvInviteCode.setOnClickListener(issue);
        binding.ivQr.setOnClickListener(issue);

        binding.btnBack.setOnClickListener(view -> finish());

        //+새 그룹 버튼
        binding.btnNewGroup.setOnClickListener(view -> {
            TnLog.i(TnLog.SCREEN, "GroupManageActivity → GroupCreateActivity");
            tripLauncher.launch(new Intent(this, GroupCreateActivity.class));
        });

        //그룹참여 버튼
        binding.btnJoinGroup.setOnClickListener(view -> {
            TnLog.i(TnLog.SCREEN, "GroupManageActivity → GroupJoinActivity");
            tripLauncher.launch(new Intent(this, GroupJoinActivity.class));
        });

        // 여행 끝내기: 서버 함수가 아직 없어 버튼을 숨겨 둔다(backend.md "여행 종료는 별도 구현 단계").

        loadTrip();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null) handler.post(expiryTicker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(expiryTicker);
        super.onPause();
    }

    // ───────────────────────── 서버에서 읽기 ─────────────────────────

    private void loadTrip() {
        showMessage("불러오는 중…");
        repository.loadCurrentTrip().addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) {
                TnLog.e(TnLog.GROUP, "여행 불러오기 실패", task.getException());
                showMessage(BackendErrors.message(task.getException()));
                return;
            }
            trip = task.getResult();
            if (trip == null) {
                TnLog.i(TnLog.GROUP, "참여한 그룹 없음");
                showMessage("아직 참여한 그룹이 없어요");
                return;
            }
            invite = repository.cachedInvite(trip.tripId);
            TnLog.i(TnLog.GROUP, "그룹 표시: " + trip.tripId + ", 멤버 " + trip.memberCount
                    + ", 상태 " + trip.status + ", 저장된 초대코드 " + (invite != null ? "있음" : "없음"));
            renderTrip();
            loadMembers(trip.tripId);
            loadCover(trip);
        });
    }

    private void loadMembers(@NonNull String tripId) {
        repository.loadMembers(tripId).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) {
                TnLog.e(TnLog.GROUP, "멤버 불러오기 실패", task.getException());
                return;
            }
            members.clear();
            for (TripRepository.MemberInfo m : task.getResult()) {
                members.add(new Member(m.displayName, m.owner));
            }
            adapter.notifyDataSetChanged();
            binding.tvMemberTitle.setText("멤버 " + members.size());
        });
    }

    private void loadCover(@NonNull TripRepository.TripInfo info) {
        if (info.coverImagePath == null) return;
        repository.loadCover(info.coverImagePath).addOnSuccessListener(this, bytes -> {
            Bitmap bitmap = JpegEncoder.decode(bytes);
            if (bitmap != null) {
                binding.ivGroupImage.setImageBitmap(bitmap);
            } else {
                TnLog.w(TnLog.GROUP, "대표 이미지 디코딩 실패: " + info.coverImagePath);
            }
        });
    }

    private void issueInvite() {
        if (trip == null || issuingInvite) return;
        issuingInvite = true;
        TnLog.i(TnLog.GROUP, "초대 코드 발급 요청: " + trip.tripId);
        binding.tvInviteCode.setText("발급 중…");
        repository.createInvite(trip.tripId).addOnCompleteListener(this, task -> {
            issuingInvite = false;
            if (task.isSuccessful()) {
                invite = task.getResult();
                // 초대 코드 원문은 남기지 않는다(로그가 공유되면 누구나 참여할 수 있음).
                TnLog.i(TnLog.GROUP, "초대 코드 발급됨, 만료까지 "
                        + InviteCodes.remainingText(invite.expiresAt, System.currentTimeMillis()));
            } else {
                Toast.makeText(this, BackendErrors.message(task.getException()), Toast.LENGTH_LONG).show();
            }
            renderInvite();
        });
    }

    // ───────────────────────── 화면 그리기 ─────────────────────────

    private void showMessage(@NonNull String message) {
        binding.emptyGroup.setText(message);
        binding.emptyGroup.setVisibility(View.VISIBLE);
        binding.inProcess.setVisibility(View.GONE);
        // 여행이 없을 때만 새로 만들기·참여 버튼을 보여 준다.
        boolean noTrip = trip == null;
        binding.btnNewGroup.setVisibility(noTrip ? View.VISIBLE : View.GONE);
        binding.btnJoinGroup.setVisibility(noTrip ? View.VISIBLE : View.GONE);
    }

    private void renderTrip() {
        if (trip == null) return;
        binding.emptyGroup.setVisibility(View.GONE);
        binding.inProcess.setVisibility(View.VISIBLE);
        binding.btnNewGroup.setVisibility(View.GONE);
        binding.btnJoinGroup.setVisibility(View.GONE);

        binding.tvGroupName.setText(trip.name);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy.MM.dd.", Locale.KOREA);
        fmt.setTimeZone(TimeZone.getTimeZone(trip.timeZone));
        binding.gmStartDate.setText(fmt.format(new Date(trip.startAt)));
        binding.gmEndDate.setText(fmt.format(new Date(trip.endAt)));
        binding.tvStatus.setText(trip.isActive() ? "진행 중" : "종료됨");
        binding.tvMemberTitle.setText("멤버 " + trip.memberCount);

        // 초대 코드는 진행 중인 여행의 방장만 만들 수 있다(createInvite).
        FirebaseUser user = FirebaseProvider.get(this).getAuth().getCurrentUser();
        boolean canInvite = trip.isOwner(user == null ? null : user.getUid()) && trip.isActive();
        binding.inviteCard.setVisibility(canInvite ? View.VISIBLE : View.GONE);
        renderInvite();
    }

    private void renderInvite() {
        if (binding == null || trip == null) return;
        long now = System.currentTimeMillis();
        if (invite == null || invite.expiresAt <= now) {
            if (!issuingInvite) binding.tvInviteCode.setText("코드 발급");
            binding.expirationDate.setText(invite == null
                    ? "눌러서 30분짜리 코드를 만들어요" : "코드 만료됨 · 눌러서 다시 발급");
            binding.btnCopy.setVisibility(View.GONE);
            binding.ivQr.setImageDrawable(null);
            return;
        }
        binding.tvInviteCode.setText(invite.code);
        binding.btnCopy.setVisibility(View.VISIBLE);
        binding.expirationDate.setText(InviteCodes.remainingText(invite.expiresAt, now));
        makeQr(invite.code);
    }

    //qr코드 만들기 (QR 내용은 6자리 코드 그대로. 참여 화면에서 스캔하면 바로 입력된다)
    private void makeQr(@NonNull String code) {
        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap qr = barcodeEncoder.encodeBitmap(code, BarcodeFormat.QR_CODE, 400, 400);
            binding.ivQr.setImageBitmap(qr);
        } catch (WriterException e) {
            TnLog.e(TnLog.GROUP, "QR 생성 실패", e);
        }
    }

    //Adapter
    private static class MemberAdapter extends RecyclerView.Adapter<MemberViewHolder> {

        private final List<Member> data;

        private MemberAdapter(List<Member> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemMemberBinding itemMemberBinding = ItemMemberBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new MemberViewHolder(itemMemberBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
            holder.bind(data.get(position));
            holder.itemMemberBinding.divider.setVisibility(
                    position == getItemCount() - 1 ? View.GONE : View.VISIBLE);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    //viewholder
    private static class MemberViewHolder extends RecyclerView.ViewHolder {
        private final ItemMemberBinding itemMemberBinding;

        MemberViewHolder(@NonNull ItemMemberBinding itemBinding) {
            super(itemBinding.getRoot());
            this.itemMemberBinding = itemBinding;
        }

        private void bind(Member member) {
            String name = member.getName();
            itemMemberBinding.tvMemberName.setText(name);
            // 이모지 등 서로게이트 문자도 한 글자로 자른다.
            itemMemberBinding.tvAvatar.setText(name.isEmpty() ? "?"
                    : name.substring(0, name.offsetByCodePoints(0, 1)));
            itemMemberBinding.tvLeaderBadge.setVisibility(member.isLeader() ? View.VISIBLE : View.GONE);
        }
    }
}
