package com.example.tteonam.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.tteonam.util.TnLog;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AuthRepository {
    private static final String USERS = "users";

    public static final String FIELD_DISPLAY_NAME = "displayName";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_UPDATED_AT = "updatedAt";

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public AuthRepository(@NonNull Context context) {
        FirebaseProvider provider = FirebaseProvider.get(context);
        this.auth = provider.getAuth();
        this.db = provider.getFirestore();
    }

    @Nullable
    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    @NonNull
    public Task<AuthResult> signIn(@NonNull String email, @NonNull String password) {
        return TnLog.track(TnLog.AUTH, "signIn", auth.signInWithEmailAndPassword(email, password));
    }

    @NonNull
    public Task<AuthResult> signUp(@NonNull String email, @NonNull String password) {
        return TnLog.track(TnLog.AUTH, "signUp", auth.createUserWithEmailAndPassword(email, password));
    }

    public void signOut() {
        TnLog.i(TnLog.AUTH, "로그아웃");
        auth.signOut();
    }

    @NonNull
    public Task<DocumentSnapshot> loadProfile(@NonNull String uid) {
        return TnLog.track(TnLog.AUTH, "loadProfile", profileRef(uid).get());
    }

    @NonNull
    // users/{uid}는 닉네임과 생성·수정 시각만 저장한다.
    public Task<Void> createProfileIfMissing(@NonNull String uid, @NonNull String nickname) {
        DocumentReference ref = profileRef(uid);
        return TnLog.track(TnLog.AUTH, "createProfile", db.runTransaction(transaction -> {
            DocumentSnapshot existing = transaction.get(ref);
            if (!existing.exists()) {
                Map<String, Object> profile = new HashMap<>();
                profile.put(FIELD_DISPLAY_NAME, nickname);

                profile.put(FIELD_CREATED_AT, FieldValue.serverTimestamp());
                profile.put(FIELD_UPDATED_AT, FieldValue.serverTimestamp());
                transaction.set(ref, profile);
            }
            return null;
        }));
    }

    @NonNull

    private DocumentReference profileRef(@NonNull String uid) {
        return db.collection(USERS).document(uid);
    }
}
