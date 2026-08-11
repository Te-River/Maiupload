import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.symbol.processing)
    id("androidx.room")
}

val appVersion: String = "1.2.6"
val appVersionCode: String = "006"

val gitCommitId: String = try {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        standardOutput = stdout
    }
    stdout.toString().trim()
} catch (_: Exception) {
    "0"
}

android {
    namespace = "io.github.teriver.maiupload"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.teriver.maiupload"
        minSdk = 26
        targetSdk = 34
        versionName = appVersion
        versionCode = (appVersion + appVersionCode).replace(".", "").toInt()

        versionNameSuffix = "-$gitCommitId"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 落雪 OAuth 接入：client_id 为公共客户端标识（PKCE 授权，无需 client_secret），
        // 直接硬编码入 BuildConfig，无需环境变量注入。
        buildConfigField("String", "LXNS_OAUTH_CLIENT_ID", "\"991a6c5c-6f9f-46c9-99a8-a7ff2c904ac3\"")
        // 全局默认 false，snapshot buildType 覆写为 true
        buildConfigField("boolean", "IS_SNAPSHOT", "false")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 本地构建签名：读环境变量 LOCAL_KEYSTORE_PATH / LOCAL_KEYSTORE_ALIAS
            // / LOCAL_STORE_PASSWORD / LOCAL_KEY_PASSWORD；未配置则构建 unsigned release。
            // CI 走 -Pandroid.injected.signing.* 注入，与本配置互不冲突。
            val keystorePath = System.getenv("LOCAL_KEYSTORE_PATH")
            if (!keystorePath.isNullOrEmpty()) {
                signingConfig = signingConfigs.create("localRelease") {
                    storeFile = file(keystorePath)
                    storePassword = System.getenv("LOCAL_STORE_PASSWORD") ?: ""
                    keyAlias = System.getenv("LOCAL_KEYSTORE_ALIAS") ?: "release"
                    keyPassword = System.getenv("LOCAL_KEY_PASSWORD") ?: ""
                }
            }
        }

        getByName("debug") {
            isDebuggable = true
        }

        create("snapshot") {
            initWith(getByName("release"))
            buildConfigField("boolean", "IS_SNAPSHOT", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            if (this is com.android.build.gradle.internal.api.BaseVariantOutputImpl) {
                outputFileName = "Maiupload-${versionName}-universal-${variant.buildType.name}.apk"
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // 未 mock 的 android.util.Log 等调用返回默认值，避免
            // 单元测试走异常分支时因 Log 崩溃（如 CheckUpdateTest）
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation.graphics)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Android room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Jsoup
    implementation(libs.jsoup)

    implementation(libs.nanohttpd.nanohttpd)

    // Ktor Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    // Ktor Serialization
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.protobuf)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)
}

room {
    schemaDirectory("$projectDir/schemas")
}

tasks.register("getCurrentAppVersion") {
    doLast {
        File("appVersion.txt").writeText(appVersion)
    }
}
