package com.example.tteonam.data;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.tteonam.BuildConfig;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public final class FirebaseProvider {
    private static final String LOCAL_APP_NAME = "tteonam-local";

    private static final String LOCAL_PROJECT_ID = "demo-tteonam";

    private static final int AUTH_EMULATOR_PORT = 9099;
    private static final int FIRESTORE_EMULATOR_PORT = 8080;

    private static FirebaseProvider instance;

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    @NonNull
    public static synchronized FirebaseProvider get(@NonNull Context context) {
        if (instance == null) {
            instance = new FirebaseProvider(context.getApplicationContext());
        }
        return instance;
    }

    private FirebaseProvider(@NonNull Context appContext) {
        FirebaseApp app;
        // debug는 demo-tteonam, release는 실제 프로젝트 설정을 사용한다.
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            app = createEmulatorApp(appContext);
        } else {
            app = getCloudApp(appContext);
        }

        auth = FirebaseAuth.getInstance(app);
        firestore = FirebaseFirestore.getInstance(app);

        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            String host = BuildConfig.FIREBASE_EMULATOR_HOST;
            auth.useEmulator(host, AUTH_EMULATOR_PORT);
            firestore.useEmulator(host, FIRESTORE_EMULATOR_PORT);
        }
    }

    @NonNull
    private static FirebaseApp createEmulatorApp(@NonNull Context appContext) {
        for (FirebaseApp existing : FirebaseApp.getApps(appContext)) {
            if (LOCAL_APP_NAME.equals(existing.getName())) {
                return existing;
            }
        }
        FirebaseOptions options = new FirebaseOptions.Builder()
                .setProjectId(LOCAL_PROJECT_ID)
                .setApplicationId("1:000000000000:android:0000000000000000")
                .setApiKey("demo-api-key")
                .build();
        return FirebaseApp.initializeApp(appContext, options, LOCAL_APP_NAME);
    }

    @NonNull
    private static FirebaseApp getCloudApp(@NonNull Context appContext) {
        FirebaseApp app = FirebaseApp.initializeApp(appContext);
        if (app == null) {
            throw new IllegalStateException(
                    "실제 Firebase 설정이 없습니다. Firebase에 Android 앱을 등록하고 "
                            + "app/google-services.json 과 Google Services 플러그인을 연결하세요.");
        }
        return app;
    }

    @NonNull
    public FirebaseAuth getAuth() {
        return auth;
    }

    @NonNull
    public FirebaseFirestore getFirestore() {
        return firestore;
    }
}
