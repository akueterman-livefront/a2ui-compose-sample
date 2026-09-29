import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Agent selection lives in local.properties (gitignored), so each developer chooses locally.
// With no ANTHROPIC_API_KEY, the app uses FakeAgent. See README "Use a real LLM (Claude)".
val localProperties =
    Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use(::load)
    }
val anthropicApiKey = localProperties.getProperty("ANTHROPIC_API_KEY").orEmpty()
val anthropicModel = localProperties.getProperty("ANTHROPIC_MODEL") ?: "claude-haiku-4-5"

android {
    namespace = "com.example.a2uisample"

    // The A2UI AARs require compileSdk 37.1 or newer.
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "com.example.a2uisample"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "ANTHROPIC_API_KEY", "\"$anthropicApiKey\"")
        buildConfigField("String", "ANTHROPIC_MODEL", "\"$anthropicModel\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.a2ui.compose.runtime)
    implementation(libs.a2ui.compose.ui)
    implementation(libs.a2ui.material3)

    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.anthropic.java)
}
