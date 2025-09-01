plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.droidtunnel"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.droidtunnel"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // Mantém compatível com a configuração do projeto
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        // Alinhado com Kotlin 1.9.20 (1.5.4/1.5.5 são compatíveis):contentReference[oaicite:3]{index=3}
        kotlinCompilerExtensionVersion = "1.5.5"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")

    // BOM fixa versões coordenadas do Compose
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))

    // UI Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // ===== ADIÇÕES para compilar o projeto =====
    // Fundamentos do Compose (LazyColumn, Pager experimental etc.)
    implementation("androidx.compose.foundation:foundation")
    // Ícones (Icons.Default.*)
    implementation("androidx.compose.material:material-icons-extended")
    // LocalBroadcastManager usado pelo serviço
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    // ===========================================

    // Outras libs já usadas no código
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.jcraft:jsch:0.1.55")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
