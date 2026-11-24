package com.example.tteonam;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.tteonam.PhotoDao;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Database(entities = {Photo.class}, version = 2, exportSchema = false)
public abstract class PhotoDB extends RoomDatabase {

    public abstract PhotoDao photoDao();

    // DB 스레드(메인 스레드 x)
    public static final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    private static volatile PhotoDB INSTANCE;

    // 앱 전체에서 DB는 하나만 만들기
    public static PhotoDB get(Context context) {
        if (INSTANCE == null) {
            synchronized (PhotoDB.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(), PhotoDB.class, "tteonam.db")
                            // 테스트 단계: 스키마가 바뀌면 기존 로컬 DB를 지우고 새로 만듦
                            .fallbackToDestructiveMigration(true)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}