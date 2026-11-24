import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// versionCode = número de commits de la rama (igual que en sesolibre-mind):
// siempre crece y sale igual en local y en CI (CI clona con fetch-depth: 0).
// VERSION_CODE en el entorno lo sobreescribe si hiciera falta.
// Historial: hasta la 0.2.5 se usó run_number y luego valores a mano (5, 6, 7);
// el conteo de commits ya va arriba de eso.
val gitCommitCount: Int? = runCatching {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt()
}.getOrNull()
val ciVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: gitCommitCount ?: 1
// versionName: manual y semántico (se sube a mano antes de etiquetar un release).
val appVersionName = "0.2.5"

android {
    namespace = "com.sesolibre.somnia"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sesolibre.somnia"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        noCompress += "tflite" // permite mapear el modelo directo desde assets
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.navigation.compose)
    implementation(libs.litert)
    implementation(libs.androidx.datastore.preferences)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    debugImplementation(libs.compose.ui.tooling)
}
