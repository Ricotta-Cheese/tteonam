import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// ───────── Firebase 연결 대상 ─────────
// 기본값: 실제 클라우드 프로젝트(tteonam). app/google-services.json의 값을 BuildConfig로 넣는다.
// 로컬 Firebase 에뮬레이터를 쓰려면 local.properties에 아래 줄을 추가한다(개인 설정, 커밋하지 않음).
//   tteonam.firebase=emulator
//   tteonam.firebase.emulatorHost=10.0.2.2   (선택, 기본 10.0.2.2 = AVD에서 본 PC)
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val useFirebaseEmulator = localProps.getProperty("tteonam.firebase", "cloud") == "emulator"
val firebaseEmulatorHost = localProps.getProperty("tteonam.firebase.emulatorHost", "10.0.2.2")

// google-services.json은 Gradle 플러그인 없이 직접 읽는다(AGP 버전과 무관하게 동작).
@Suppress("UNCHECKED_CAST")
val firebaseCloud: Map<String, String> = run {
    val file = file("google-services.json")
    if (!file.exists()) return@run emptyMap()
    val json = groovy.json.JsonSlurper().parse(file) as Map<String, Any?>
    val project = json["project_info"] as Map<String, Any?>
    val client = (json["client"] as List<Map<String, Any?>>).first { c ->
        val info = (c["client_info"] as Map<String, Any?>)["android_client_info"] as Map<String, Any?>
        info["package_name"] == "com.example.tteonam"
    }
    val clientInfo = client["client_info"] as Map<String, Any?>
    val apiKey = (client["api_key"] as List<Map<String, Any?>>).first()["current_key"] as String
    mapOf(
        "projectId" to project["project_id"] as String,
        "storageBucket" to project["storage_bucket"] as String,
        "gcmSenderId" to project["project_number"] as String,
        "appId" to clientInfo["mobilesdk_app_id"] as String,
        "apiKey" to apiKey,
    )
}

fun quoted(value: String?): String = "\"" + (value ?: "") + "\""

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

        buildConfigField("String", "FIREBASE_PROJECT_ID", quoted(firebaseCloud["projectId"]))
        buildConfigField("String", "FIREBASE_APP_ID", quoted(firebaseCloud["appId"]))
        buildConfigField("String", "FIREBASE_API_KEY", quoted(firebaseCloud["apiKey"]))
        buildConfigField("String", "FIREBASE_STORAGE_BUCKET", quoted(firebaseCloud["storageBucket"]))
        buildConfigField("String", "FIREBASE_GCM_SENDER_ID", quoted(firebaseCloud["gcmSenderId"]))
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "USE_FIREBASE_EMULATOR", useFirebaseEmulator.toString())
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", quoted(firebaseEmulatorHost))
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

    // 단위 테스트에서 android.util.Log 등 안드로이드 API는 아무 일도 하지 않게 한다(TnLog 호출 허용).
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.functions)

    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // 촬영(CameraX)·위치·임시 저장(Room)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.play.services.location)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // 초대 QR 생성·스캔
    implementation(libs.zxing.android.embedded)

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
