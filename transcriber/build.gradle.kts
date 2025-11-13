import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.kapt)
}

fun String.toBuildConfigString(): String = "\"" + this.replace("\"", "\\\"") + "\""

fun String.toBooleanStrictOrNullCompat(): Boolean? =
    when (lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }

val localProps = Properties().apply {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}

val transcribeAccessKey =
    localProps.getProperty("frontier.aws.transcribeAccessKeyId")?.trim().orEmpty()
val transcribeSecretKey =
    localProps.getProperty("frontier.aws.transcribeSecretAccessKey")?.trim().orEmpty()
val transcribeSessionToken =
    localProps.getProperty("frontier.aws.transcribeSessionToken")?.trim().orEmpty()
val transcribeRegion =
    localProps.getProperty("frontier.aws.region")?.takeIf { it.isNotBlank() } ?: "us-east-2"
val transcribeEnabled =
    localProps.getProperty("frontier.aws.transcribeEnabled")
        ?.trim()
        ?.toBooleanStrictOrNullCompat()
        ?: true

android {
    namespace = "com.example.frontieraudio.transcriber"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "TRANSCRIBE_ACCESS_KEY", transcribeAccessKey.toBuildConfigString())
        buildConfigField("String", "TRANSCRIBE_SECRET_KEY", transcribeSecretKey.toBuildConfigString())
        buildConfigField("String", "TRANSCRIBE_SESSION_TOKEN", transcribeSessionToken.toBuildConfigString())
        buildConfigField("String", "TRANSCRIBE_REGION", transcribeRegion.toBuildConfigString())
        buildConfigField("boolean", "TRANSCRIBE_ENABLED", transcribeEnabled.toString())
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
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.tensorflow.lite)
    implementation(libs.aws.sdk.java.transcribestreaming)
    implementation(libs.kotlinx.coroutines.reactive)
    kapt(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}

