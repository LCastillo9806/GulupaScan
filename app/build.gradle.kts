plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Elimina recursos de lanzador antiguos que entran en conflicto con los actuales
run {
    listOf(
        "src/main/res/drawable/ic_launcher_foreground.xml",
        "src/main/res/drawable/ic_launcher_background.xml",
        "src/main/res/values/colors.xml",
        "src/main/res/values-night/themes.xml",
        "src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
    ).forEach { rel ->
        val f = file(rel)
        if (f.exists()) f.delete()
    }
}

android {
    namespace = "co.edu.unal.gulupascan"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "co.edu.unal.gulupascan"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.3.0"

        // Servidor opcional (Space de Hugging Face con api.py desplegado).
        // Se puede cambiar dentro de la app en Perfil > Configuración.
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"https://jf-floresriera-powderyvision7.hf.space\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        noCompress += "onnx"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)

    // ONNX Runtime para inferencia directa en el teléfono (offline)
    implementation(libs.onnxruntime.android)

    // Cámara
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Red + JSON + imágenes
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
