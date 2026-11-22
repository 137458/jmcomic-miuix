import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// 签名信息仅从 local.properties 读取（密钥绝不入库）；四键齐全且 keystore 真实存在才启用 release 签名。
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val claimedReleaseSigning = releaseSigningKeys.all { !localProps.getProperty(it).isNullOrBlank() }
val releaseStoreFile = localProps.getProperty("storeFile")
    ?.takeIf { it.isNotBlank() }
    ?.let { rootProject.file(it) }
// 「四个键都写了」不等于「keystore 真在」。此前只判键非空：storeFile 指错时 release 包会静默签成
// debug 签名，装机被系统以 INSTALL_FAILED_UPDATE_INCOMPATIBLE 拒掉，且本应用 allowBackup=false，
// 用户按提示卸载重装就丢全部本地数据。配置期直接失败，比发布后才发现便宜。
if (claimedReleaseSigning && releaseStoreFile?.isFile != true) {
    throw GradleException(
        "local.properties 声明了 release 签名，但 keystore 不存在：" +
            "${releaseStoreFile?.absolutePath ?: "(storeFile 为空)"}。" +
            "请修正 storeFile，或删掉 storeFile/storePassword/keyAlias/keyPassword 四键以改用 debug 签名构建。",
    )
}
val hasReleaseSigning = claimedReleaseSigning
if (!hasReleaseSigning) {
    logger.warn(
        "未配置 release 签名：release 包将使用 debug 签名，仅可本地侧载，不得对外分发（CI 只归档 mapping，不归档该包）。",
    )
}

android {
    namespace = "com.jm3.app.miuix"
    compileSdkVersion("android-37.0") // 依赖 AAR 元数据要求 >=37

    defaultConfig {
        applicationId = "com.jm3.app.miuix"
        // 应用基准要求 Android 12（API 31）；模糊组件低版本已通过 manifest tools:overrideLibrary 降级兼容。
        minSdk = 31
        targetSdk = 36
        versionCode = 20000
        versionName = "2.0.0"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(localProps.getProperty("storeFile"))
                storePassword = localProps.getProperty("storePassword")
                keyAlias = localProps.getProperty("keyAlias")
                keyPassword = localProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 未配置签名时回退 debug，保证 assembleRelease 产物仍可本地侧载；
            // 上面的配置期告警与 keystore 存在性校验负责把「以为签了正式包」的错觉挡在前面。
            signingConfig = signingConfigs.getByName(if (hasReleaseSigning) "release" else "debug")
        }
    }

    testOptions {
        unitTests {
            // 单测跑在 JVM 上，android.* 桩方法默认抛「not mocked」：下载落库这类失败分支要打日志，
            // 日志不该成为被测逻辑的阻断点，让桩返回默认值。
            isReturnDefaultValues = true
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// 导出 Room 表结构快照到 app/schemas：迁移脚本与实体定义一旦漂移，schema JSON 的 diff 会在评审里暴露。
ksp {
    arg("room.schemaLocation", "${projectDir}/schemas")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.telephoto.zoomable.image.coil3)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.miuix.ui)
    implementation(libs.miuix.nav)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.squircle)

    implementation(libs.coui2.ui)
    implementation(libs.coui2.preference)
    implementation(libs.coui2.icons)
    implementation(libs.coui2.blur)
    implementation(libs.coui2.squircle)

    // 无 AWT 的 Android 环境必须提供 ImageProcessor SPI 实现
    implementation(libs.jmcomic.android.support) {
        exclude(group = "io.github.darkxanter", module = "webp-imageio")
    }

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
