package com.example.tteonam;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

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
import androidx.recyclerview.widget.RecyclerView;
import com.example.tteonam.databinding.ItemMemberBinding;

import com.example.tteonam.databinding.ActivityGroupManageBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class GroupManageActivity extends AppCompatActivity {

    private ActivityGroupManageBinding binding;
    private String groupName;
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

        //화면 상태 확인
        groupViewUpdate();

        //멤버확인
        loadMembers();

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

                    groupViewUpdate();
                }
            }
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

        //참여 버튼
        binding.btnJoinGroup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });
        
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
        }
    }

    private void loadMembers() {
        //멤버 레이어 테스트용
        members.add(new Member("전찬영",true));
        members.add(new Member("박이든",false));
        members.add(new Member("임성민",false));
    }

    //viewholder
    private class viewHolder extends RecyclerView.ViewHolder {
        private ItemMemberBinding itemMemberBinding;
        public viewHolder(@NonNull ItemMemberBinding itemBinding) {
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
