plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.tteonam"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.tteonam"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "USE_FIREBASE_EMULATOR", "true")
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"10.0.2.2\"")
        }
        release {
            buildConfigField("boolean", "USE_FIREBASE_EMULATOR", "false")
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    viewBinding { enable = true }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

//    camerax
    var camerax_version = "1.7.0-rc01"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-video:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")
    implementation("androidx.camera:camera-mlkit-vision:${camerax_version}")
    implementation("androidx.camera:camera-extensions:${camerax_version}")


//    play service location
    implementation("com.google.android.gms:play-services-location:21.4.0")


//    ml kit (앱과 함께 번들로 묶기)
    // Use this dependency to bundle the model with your app
    implementation("com.google.mlkit:image-labeling:17.0.9")


    // Firebase BoM · auth · firestore 는 위 libs.firebase.* 로 이미 선언 (merge-test에서 중복 제거)

    // When using the BoM, don't specify versions in Firebase dependencies
    implementation("com.google.firebase:firebase-analytics")

    // Add the dependencies for any other desired Firebase products
    // https://firebase.google.com/docs/android/setup#available-libraries

    // Add the dependency for the Firebase Authentication library
    // When using the BoM, you don't specify versions in Firebase library dependencies

    // Also add the dependencies for the Credential Manager libraries and specify their versions
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Add the dependency for the Cloud Storage library
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation("com.google.firebase:firebase-storage")

    // FirebaseUI Storage only
    implementation("com.firebaseui:firebase-ui-storage:9.0.0")

    // For @GlideModule
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // room
    val room_version = "2.8.5"
    implementation("androidx.room:room-runtime:$room_version")
    annotationProcessor("androidx.room:room-compiler:$room_version")

    // Declare the dependency for the Cloud Firestore library
    // When using the BoM, you don't specify versions in Firebase library dependencies

    implementation("com.firebaseui:firebase-ui-firestore:9.0.0")
    implementation("androidx.paging:paging-runtime:3.5.3")
}
