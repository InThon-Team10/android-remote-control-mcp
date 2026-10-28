plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

ktlint {
    version.set("1.8.0")
}

// Same rationale as :app — ktlint-cli 1.8.0 bundles a CVE-flagged logback used only at build time.
configurations.matching { it.name.startsWith("ktlint") }.configureEach {
    resolutionStrategy {
        force(
            "ch.qos.logback:logback-core:1.5.34",
            "ch.qos.logback:logback-classic:1.5.34",
        )
    }
}

android {
    namespace = "com.danielealbano.composetestapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.danielealbano.composetestapp"
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
}
