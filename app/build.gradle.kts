plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ownstream.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ownstream.app"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += setOf("libsignal_jni*.dylib", "signal_jni*.dll", "META-INF/INDEX.LIST")
        }
        jniLibs {
            excludes += setOf("**/libsignal_jni_testing.so")
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":protocol"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    implementation(libs.kotlinx.serialization.json)
    
    implementation(libs.ktorClientCore)
    implementation(libs.ktorClientOkhttp)
    implementation(libs.ktorClientContentNegotiation)
    implementation(libs.ktorClientWebsockets)
    implementation(libs.ktor.serialization.json)
    implementation(libs.androidx.security.crypto)
    
    implementation(libs.libsignal.client)
    implementation(libs.libsignal.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.robolectric.shadows)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(project(":relay"))
    testImplementation(libs.bcprov)
    testImplementation(libs.ktor.server.core)
    testImplementation(libs.ktorServerCio)
    testImplementation(libs.ktor.server.content.negotiation)
    testImplementation(libs.ktor.server.websockets)
    testImplementation(libs.ktor.serialization.json)
    testImplementation(libs.androidx.room.ktx)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.appcompat)

    androidTestImplementation(project(":relay")) {
        exclude(group = "org.postgresql", module = "postgresql")
        exclude(group = "com.zaxxer", module = "HikariCP")
        exclude(group = "io.ktor", module = "ktor-server-netty-jvm")
    }
    androidTestImplementation(libs.ktor.server.core)
    androidTestImplementation(libs.ktorServerCio)
    androidTestImplementation(libs.ktor.server.content.negotiation)
    androidTestImplementation(libs.ktor.server.websockets)
    androidTestImplementation(libs.ktor.serialization.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.ktorClientMock)
    androidTestImplementation(libs.ktor.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.testManifest)
}
