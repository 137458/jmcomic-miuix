plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.jmcomic.api)
    implementation(libs.jmcomic.core) {
        exclude(group = "io.github.darkxanter", module = "webp-imageio")
    }
    // SessionCookies / LibraryBridge / LibraryReflection 直接使用 okhttp 的 Cookie 与 HttpUrl，
    // 依赖不再靠 jmcomic-core 的传递依赖偶然存在。
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
