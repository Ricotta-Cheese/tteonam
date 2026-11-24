package com.example.tteonam;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PhotoDao {

    @Insert
    long insert(Photo photo);       // 새로 붙은 id를 돌려줌

    @Update
    void update(Photo photo);

    @Delete
    void delete(Photo photo);

    // LiveData로 받으면 DB가 바뀔 때마다 자동으로 다시 알려줌 (앨범 화면용)
    @Query("SELECT * FROM photos ORDER BY capturedAt DESC")
    LiveData<List<Photo>> observeAll();

    @Query("SELECT * FROM photos WHERE id = :id")
    Photo findById(long id);

    // 지도 · 경로용: 위치가 있는 사진만, 찍은 순서대로
    @Query("SELECT * FROM photos WHERE lat IS NOT NULL AND lng IS NOT NULL ORDER BY capturedAt ASC")
    List<Photo> getAllWithLocation();

    // 저장보다 늦게 도착한 위치를 채워 넣기
    @Query("UPDATE photos SET lat = :lat, lng = :lng WHERE id = :id")
    void updateLocation(long id, double lat, double lng);

    // 업로드를 처음 시작할 때 정한 여행·서버 id 저장 (재시도 때 같은 문서·경로를 다시 쓰기 위해)
    @Query("UPDATE photos SET tripId = :tripId, serverPhotoId = :serverPhotoId WHERE id = :id")
    void assignServerIds(long id, String tripId, String serverPhotoId);

    @Query("UPDATE photos SET docCreated = 1 WHERE id = :id")
    void markDocCreated(long id);

    @Query("DELETE FROM photos WHERE id = :id")
    void deleteById(long id);
}