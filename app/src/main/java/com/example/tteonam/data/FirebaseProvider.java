package com.example.tteonam.data;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.tteonam.BuildConfig;
import com.example.tteonam.util.TnLog;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.storage.FirebaseStorage;

public final class FirebaseProvider {
    private static final String LOCAL_APP_NAME = "tteonam-local";

    private static final String LOCAL_PROJECT_ID = "demo-tteonam";

    private static final int AUTH_EMULATOR_PORT = 9099;
    private static final int FIRESTORE_EMULATOR_PORT = 8080;
    private static final int FUNCTIONS_EMULATOR_PORT = 5001;
    private static final int STORAGE_EMULATOR_PORT = 9199;

    // 서버 함수는 모두 us-west1에 배포돼 있다(FirebaseControl/docs/backend.md).
    public static final String FUNCTIONS_REGION = "us-west1";
    private static final String LOCAL_STORAGE_BUCKET = "demo-tteonam.appspot.com";

    private static FirebaseProvider instance;

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final FirebaseStorage storage;
    private final FirebaseFunctions functions;

    @NonNull
    public static synchronized FirebaseProvider get(@NonNull Context context) {
        if (instance == null) {
            instance = new FirebaseProvider(context.getApplicationContext());
        }
        return instance;
    }

    private FirebaseProvider(@NonNull Context appContext) {
        FirebaseApp app;
        // 기본은 실제 클라우드(tteonam). local.properties에 tteonam.firebase=emulator를 넣은
        // 디버그 빌드만 로컬 에뮬레이터(demo-tteonam)를 쓴다. (app/build.gradle.kts 참고)
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            app = createEmulatorApp(appContext);
        } else {
            app = getCloudApp(appContext);
        }

        auth = FirebaseAuth.getInstance(app);
        firestore = FirebaseFirestore.getInstance(app);
        storage = FirebaseStorage.getInstance(app);
        functions = FirebaseFunctions.getInstance(app, FUNCTIONS_REGION);

        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            String host = BuildConfig.FIREBASE_EMULATOR_HOST;
            auth.useEmulator(host, AUTH_EMULATOR_PORT);
            firestore.useEmulator(host, FIRESTORE_EMULATOR_PORT);
            storage.useEmulator(host, STORAGE_EMULATOR_PORT);
            functions.useEmulator(host, FUNCTIONS_EMULATOR_PORT);
            TnLog.i(TnLog.APP, "Firebase 에뮬레이터 연결: " + host
                    + " (auth " + AUTH_EMULATOR_PORT + ", firestore " + FIRESTORE_EMULATOR_PORT
                    + ", storage " + STORAGE_EMULATOR_PORT + ", functions " + FUNCTIONS_EMULATOR_PORT + ")");
        } else {
            TnLog.i(TnLog.APP, "Firebase 클라우드 프로젝트 사용: " + app.getOptions().getProjectId());
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
                .setStorageBucket(LOCAL_STORAGE_BUCKET)
                .build();
        return FirebaseApp.initializeApp(appContext, options, LOCAL_APP_NAME);
    }

    // app/google-services.json의 값을 빌드 시점에 BuildConfig로 받아 기본 FirebaseApp을 만든다.
    @NonNull
    private static FirebaseApp getCloudApp(@NonNull Context appContext) {
        for (FirebaseApp existing : FirebaseApp.getApps(appContext)) {
            if (FirebaseApp.DEFAULT_APP_NAME.equals(existing.getName())) {
                return existing;
            }
        }
        if (BuildConfig.FIREBASE_PROJECT_ID.isEmpty() || BuildConfig.FIREBASE_APP_ID.isEmpty()
                || BuildConfig.FIREBASE_API_KEY.isEmpty()) {
            throw new IllegalStateException(
                    "실제 Firebase 설정이 없습니다. app/google-services.json을 확인하세요.");
        }
        FirebaseOptions options = new FirebaseOptions.Builder()
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
                .setGcmSenderId(BuildConfig.FIREBASE_GCM_SENDER_ID)
                .build();
        return FirebaseApp.initializeApp(appContext, options);
    }

    @NonNull
    public FirebaseAuth getAuth() {
        return auth;
    }

    @NonNull
    public FirebaseFirestore getFirestore() {
        return firestore;
    }

    @NonNull
    public FirebaseStorage getStorage() {
        return storage;
    }

    @NonNull
    public FirebaseFunctions getFunctions() {
        return functions;
    }
}
