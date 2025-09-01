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
        // A versão do compilador do Kotlin deve ser compatível com a versão do Kotlin
        // e do Jetpack Compose. A versão 1.5.10 é recomendada para Kotlin 1.9.22+.
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.13.1") // Atualizado
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0") // Atualizado
    implementation("androidx.activity:activity-compose:1.9.0") // Atualizado

    // BOM (Bill of Materials) para o Compose, atualizado para a versão estável mais recente
    val composeBomVersion = "2024.05.00" // A pesquisa indica esta como a versão estável mais recente
    implementation(platform("androidx.compose:compose-bom:$composeBomVersion"))

    // UI Compose (sem especificar versões, pois são geridas pela BOM)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")

    // Dependência necessária para LocalBroadcastManager
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // Outras dependências do projeto
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.android.material:material:1.12.0") // Atualizado
    implementation("com.jcraft:jsch:0.1.55")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Dependências de Teste
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:$composeBomVersion"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
