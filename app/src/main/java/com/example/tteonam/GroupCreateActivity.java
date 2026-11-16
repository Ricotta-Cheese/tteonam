package com.example.tteonam;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.util.Pair;

import com.example.tteonam.databinding.ActivityGroupCreateBinding;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

public class GroupCreateActivity extends AppCompatActivity {
    ActivityGroupCreateBinding binding;
    private long startDate;
    private long endDate;
    private long currentTime;
    private String inviteCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());

        binding = ActivityGroupCreateBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //back버튼
        binding.btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        //텍스트 변환 읽기
        binding.etTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                binding.btnDone.setEnabled(isDone());
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }
        });

        //기간선택
        binding.layoutDateRange.setOnClickListener(view->{
            MaterialDatePicker<Pair<Long,Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText("여행기간 선택")
                    .build();

            picker.addOnPositiveButtonClickListener(selection ->{
                startDate = selection.first;
                endDate = selection.second;

                SimpleDateFormat fmt = new SimpleDateFormat("yyyy.MM.dd.", Locale.KOREA);
                fmt.setTimeZone(TimeZone.getTimeZone("UTC"));

                binding.tvStartDate.setText(fmt.format(new Date(startDate)));
                binding.tvEndDate.setText(fmt.format(new Date(endDate)));

                binding.btnDone.setEnabled(isDone());
            });

            picker.show(getSupportFragmentManager(), "DATE_PICKER");
        });

        //done버튼
        binding.btnDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                setResult(RESULT_OK, new Intent()
                        .putExtra("groupName",binding.etTitle.getText().toString())
                        .putExtra("startDate",startDate)
                        .putExtra("endDate",endDate)
                        .putExtra("inviteCode",makeInviteCode())
                        .putExtra("currentTime",currentTime));
                finish();
            }
        });
    }

    private boolean isDone() {
        boolean isDoneText = !binding.etTitle.getText().toString().trim().isEmpty();
        return (startDate!=0 && isDoneText);
    }

    private String makeInviteCode() {
        String alpha = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();

        for(int i=0; i<6; i++) {
            sb.append(alpha.charAt(random.nextInt(alpha.length())));
        }
        inviteCode = sb.toString();

        currentTime = System.currentTimeMillis();

        return inviteCode;
    }
}