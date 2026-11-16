package com.example.tteonam;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewTreeLifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.tteonam.databinding.ItemMemberBinding;

import com.example.tteonam.databinding.ActivityGroupManageBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class GroupManageActivity extends AppCompatActivity {

    private ActivityGroupManageBinding binding;
    private String groupName;
    private String inviteCode;
    private long getStartTime;
    ArrayList<Member> members = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        //setContentView(R.layout.activity_group_manage);

        binding = ActivityGroupManageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //test
        //groupName = "테스트";

        //화면 상태 확인
        groupViewUpdate();

        //멤버확인
        loadMembers();

        binding.rvMembers.setLayoutManager(new LinearLayoutManager(this));
        binding.rvMembers.setAdapter(new MemberAdapter(members));

        ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
            @Override
            public void onActivityResult(ActivityResult o) {
                if(o.getResultCode() == RESULT_OK && o.getData() != null) {
                    Intent data = o.getData();

                    SimpleDateFormat fmt = new SimpleDateFormat("yyyy.MM.dd.", Locale.KOREA);
                    fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                    //그룹이름
                    groupName = data.getStringExtra("groupName");
                    binding.tvGroupName.setText(groupName);
                    //시작날짜
                    long startDate = data.getLongExtra("startDate",-1);
                    binding.gmStartDate.setText(fmt.format(new Date(startDate)));
                    //종료날짜
                    long endDate = data.getLongExtra("endDate",-1);
                    binding.gmEndDate.setText(fmt.format(new Date(endDate)));
                    //초대 코드
                    inviteCode = data.getStringExtra("inviteCode");
                    binding.tvInviteCode.setText(inviteCode);

                    //초대코드 시작시간
                    getStartTime = data.getLongExtra("currentTime",-1);

                    //만료시간 표시
                    binding.expirationDate.setText(stringExpirationTime());

                    groupViewUpdate();
                }
            }
        });

        //코드복사터치
        binding.btnCopy.setOnClickListener(view ->{
            //시스템한테 클립보드관리자를 받는다.
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            //복사할 문자열을 포장에 담는다.
            ClipData clip = ClipData.newPlainText("inviteCode",inviteCode);
            //클립보드에 올린다.
            clipboard.setPrimaryClip(clip);
            //토스트 띄우기
            Toast.makeText(this,"복사완료",Toast.LENGTH_SHORT).show();
        });

        //back버튼
        binding.btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        //+새 그룹 버튼
        binding.btnNewGroup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launcher.launch(new Intent(GroupManageActivity.this, GroupCreateActivity.class));
            }
        });

        //그룹참여 버튼
        binding.btnJoinGroup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });

        //여행끝내기 버튼
        binding.btnEndTrip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //강제종료
            }
        });
        
    }

    //만료기간 만들기
    private String stringExpirationTime() {
        long currentTime = System.currentTimeMillis();
        long expirationTime = getStartTime+(24 * 60 * 60 * 1000L);

        SimpleDateFormat fmt = new SimpleDateFormat("HH시간 mm분 후 만료", Locale.KOREA);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        if(expirationTime<currentTime) return fmt.format(new Date(expirationTime-currentTime));
        else return "코드 만료됨";
    }

    //화면에 그룹이 있을 때 없을 때 구분하는 함수
    private void groupViewUpdate() {
        if(groupName == null) {
            binding.inProcess.setVisibility(View.GONE);
            binding.btnEndTrip.setVisibility(View.GONE);
            binding.emptyGroup.setVisibility(View.VISIBLE);
        }
        else {
            binding.inProcess.setVisibility(View.VISIBLE);
            binding.btnEndTrip.setVisibility(View.VISIBLE);
            binding.emptyGroup.setVisibility(View.GONE);
            //+그룹 버튼 잠금
            binding.btnNewGroup.setVisibility(View.GONE);
        }
    }

    private void loadMembers() {
        //멤버 레이어 테스트용
        members.add(new Member("전찬영",true));
        members.add(new Member("박이든",false));
        members.add(new Member("임성민",false));
    }

    //Adapter
    private class MemberAdapter extends RecyclerView.Adapter<MemberViewHolder> {

        private List<Member> data;

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
            Member member = data.get(position);
            holder.bind(member);

            if(position == getItemCount() - 1) {
                holder.itemMemberBinding.divider.setVisibility(View.GONE);
            }
            else {
                holder.itemMemberBinding.divider.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    //viewholder
    private class MemberViewHolder extends RecyclerView.ViewHolder {
        private ItemMemberBinding itemMemberBinding;
        public MemberViewHolder(@NonNull ItemMemberBinding itemBinding) {
            super(itemBinding.getRoot());
            this.itemMemberBinding = itemBinding;
        }
        private void bind(Member member){
            String name = member.getName();

            itemMemberBinding.tvMemberName.setText(name);
            itemMemberBinding.tvAvatar.setText(name.substring(0, 1));

            if(member.isLeader()) {
                itemMemberBinding.tvLeaderBadge.setVisibility(View.VISIBLE);
            }
            else {
                itemMemberBinding.tvLeaderBadge.setVisibility(View.GONE);
            }
        }
    }
}
