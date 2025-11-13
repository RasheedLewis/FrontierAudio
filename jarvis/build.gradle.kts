plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.example.frontieraudio.jarvis"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "NOVA_MODEL_ID", "\"amazon.nova-sonic-v1:0\"")
        buildConfigField("String", "NOVA_REGION", "\"us-east-2\"")
        buildConfigField("long", "NOVA_HEARTBEAT_INTERVAL_MS", "15000L")
        buildConfigField("long", "NOVA_SESSION_TIMEOUT_MS", "120000L")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    packaging {
        resources {
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    implementation(libs.aws.sdk.java.bedrockruntime)
    implementation(libs.aws.sdk.java.sts)
    implementation(libs.aws.sdk.java.netty.nio)
    kapt(libs.hilt.compiler)
}

