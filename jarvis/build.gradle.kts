import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.kapt)
}

fun String.toBuildConfigString(): String = "\"" + this.replace("\"", "\\\"") + "\""

val localProps = Properties().apply {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}

fun Properties.getFirstNonBlank(vararg keys: String): String =
    keys.firstNotNullOfOrNull { key -> getProperty(key)?.trim()?.takeIf { it.isNotEmpty() } } ?: ""

val novaAccessKey =
    localProps.getFirstNonBlank(
        "frontier.aws.novaAccessKeyId",
        "frontier.aws.transcribeAccessKeyId"
    )
val novaSecretKey =
    localProps.getFirstNonBlank(
        "frontier.aws.novaSecretAccessKey",
        "frontier.aws.transcribeSecretAccessKey"
    )
val novaSessionToken =
    localProps.getFirstNonBlank(
        "frontier.aws.novaSessionToken",
        "frontier.aws.transcribeSessionToken"
    )
val novaRegionOverride =
    localProps.getProperty("frontier.aws.novaRegion")
        ?.takeIf { it.isNotBlank() }
        ?: "us-east-1"

val sharedRegion =
    localProps.getProperty("frontier.aws.region")
        ?.takeIf { it.isNotBlank() }
        ?: "us-east-1"

android {
    namespace = "com.example.frontieraudio.jarvis"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "NOVA_MODEL_ID", "\"amazon.nova-sonic-v1:0\"")
        buildConfigField("String", "NOVA_REGION", sharedRegion.toBuildConfigString())
        buildConfigField("long", "NOVA_HEARTBEAT_INTERVAL_MS", "15000L")
        buildConfigField("long", "NOVA_SESSION_TIMEOUT_MS", "120000L")
        buildConfigField("String", "NOVA_ACCESS_KEY", novaAccessKey.toBuildConfigString())
        buildConfigField("String", "NOVA_SECRET_KEY", novaSecretKey.toBuildConfigString())
        buildConfigField("String", "NOVA_SESSION_TOKEN", novaSessionToken.toBuildConfigString())
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.aws.sdk.java.bedrockruntime)
    implementation(libs.aws.sdk.java.sts)
    implementation(libs.aws.sdk.java.netty.nio)
    implementation(libs.aws.sdk.java.crt) {
        exclude(group = "software.amazon.awssdk.crt", module = "aws-crt")
    }
    implementation("software.amazon.awssdk.crt:aws-crt-android:0.39.4")
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

