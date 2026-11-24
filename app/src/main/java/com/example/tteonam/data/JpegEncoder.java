package com.example.tteonam.data;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.tteonam.util.TnLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

// 업로드용 JPEG 만들기.
// 서버는 이미지 디코딩·EXIF 삭제를 하지 않으므로(backend.md) 앱에서 다시 인코딩한다.
// Bitmap을 거쳐 새로 압축하면 EXIF(GPS 포함)가 남지 않고, 회전도 픽셀에 반영된다.
// 메인 스레드에서 부르지 않는다.
public final class JpegEncoder {
    public static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;   // image.jpg 10MiB
    public static final int MAX_THUMB_BYTES = 512 * 1024;         // thumb.jpg 512KiB
    public static final int MAX_COVER_BYTES = 2 * 1024 * 1024;    // 대표 이미지 2MiB

    private static final int IMAGE_LONG_SIDE = 3072;
    private static final int THUMB_LONG_SIDE = 640;
    private static final int COVER_LONG_SIDE = 1280;

    private JpegEncoder() {
    }

    public static final class PhotoBytes {
        @NonNull public final byte[] image;
        @NonNull public final byte[] thumbnail;

        PhotoBytes(@NonNull byte[] image, @NonNull byte[] thumbnail) {
            this.image = image;
            this.thumbnail = thumbnail;
        }
    }

    // 촬영 원본 파일 → 원본(긴 변 3072px 이하)과 썸네일(640px)
    @NonNull
    public static PhotoBytes encodePhoto(@NonNull String path, int rotationDegrees) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("사진을 읽을 수 없음: " + path);
        }
        Bitmap decoded = BitmapFactory.decodeFile(path,
                scaledOptions(bounds.outWidth, bounds.outHeight, IMAGE_LONG_SIDE));
        if (decoded == null) {
            throw new IOException("사진 디코딩 실패: " + path);
        }
        Bitmap upright = rotate(decoded, rotationDegrees);
        try {
            byte[] image = compressUnder(upright, MAX_IMAGE_BYTES, 90);
            Bitmap thumb = scaleDown(upright, THUMB_LONG_SIDE);
            byte[] thumbnail = compressUnder(thumb, MAX_THUMB_BYTES, 75);
            if (thumb != upright) thumb.recycle();
            TnLog.d(TnLog.IMAGE, "사진 인코딩: " + bounds.outWidth + "x" + bounds.outHeight + " → "
                    + upright.getWidth() + "x" + upright.getHeight() + ", 회전 " + rotationDegrees
                    + "°, image " + image.length + "B, thumb " + thumbnail.length + "B");
            return new PhotoBytes(image, thumbnail);
        } finally {
            upright.recycle();
        }
    }

    // 갤러리에서 고른 그룹 대표 이미지 → 2MiB 이하 JPEG
    @NonNull
    public static byte[] encodeCover(@NonNull ContentResolver resolver, @NonNull Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("이미지를 읽을 수 없음");
        }
        Bitmap decoded;
        try (InputStream in = resolver.openInputStream(uri)) {
            decoded = BitmapFactory.decodeStream(in, null,
                    scaledOptions(bounds.outWidth, bounds.outHeight, COVER_LONG_SIDE));
        }
        if (decoded == null) {
            throw new IOException("이미지 디코딩 실패");
        }
        try {
            byte[] cover = compressUnder(decoded, MAX_COVER_BYTES, 85);
            TnLog.d(TnLog.IMAGE, "대표 이미지 인코딩: " + bounds.outWidth + "x" + bounds.outHeight
                    + " → " + decoded.getWidth() + "x" + decoded.getHeight() + ", " + cover.length + "B");
            return cover;
        } finally {
            decoded.recycle();
        }
    }

    @Nullable
    public static Bitmap decode(@Nullable byte[] bytes) {
        if (bytes == null) return null;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    // inSampleSize(2의 거듭제곱)로 크게 줄이고, 밀도 값으로 정확한 크기까지 디코딩 중에 축소한다.
    // 큰 원본을 통째로 메모리에 올리지 않기 위해서다.
    @NonNull
    private static BitmapFactory.Options scaledOptions(int width, int height, int maxLongSide) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        int longSide = Math.max(width, height);
        int sample = 1;
        while (longSide / (sample * 2) >= maxLongSide) {
            sample *= 2;
        }
        options.inSampleSize = sample;
        int sampledLong = longSide / sample;
        if (sampledLong > maxLongSide) {
            options.inScaled = true;
            options.inDensity = sampledLong;
            options.inTargetDensity = maxLongSide;
        }
        return options;
    }

    @NonNull
    private static Bitmap rotate(@NonNull Bitmap src, int degrees) {
        if (degrees % 360 == 0) return src;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
        if (rotated != src) src.recycle();
        return rotated;
    }

    @NonNull
    private static Bitmap scaleDown(@NonNull Bitmap src, int maxLongSide) {
        int longSide = Math.max(src.getWidth(), src.getHeight());
        if (longSide <= maxLongSide) return src;
        float scale = (float) maxLongSide / longSide;
        return Bitmap.createScaledBitmap(src,
                Math.max(1, Math.round(src.getWidth() * scale)),
                Math.max(1, Math.round(src.getHeight() * scale)), true);
    }

    // 크기 제한을 넘으면 품질을 낮춰 다시 압축한다.
    @NonNull
    private static byte[] compressUnder(@NonNull Bitmap bitmap, int maxBytes, int startQuality) throws IOException {
        for (int quality = startQuality; quality >= 40; quality -= 10) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                throw new IOException("JPEG 압축 실패");
            }
            if (out.size() > 0 && out.size() <= maxBytes) {
                return out.toByteArray();
            }
            TnLog.d(TnLog.IMAGE, "품질 " + quality + "에서 " + out.size() + "B > " + maxBytes + "B, 품질 낮춤");
        }
        throw new IOException("JPEG 크기 제한 초과");
    }
}
