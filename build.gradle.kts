plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.google.services) apply false
}

buildscript {
    repositories {
        google()        // 🔥 thêm nếu chưa có
        mavenCentral()
    }
}

