import de.charlex.convention.libs

plugins {
    id("de.charlex.convention.android.application")
    id("de.charlex.convention.compose.multiplatform")
}

android {
    namespace = "de.charlex.compose.composetopdf"

    defaultConfig {
        applicationId = "de.charlex.compose.composetopdf"
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(projects.sample.shared)

    implementation(libs.androidx.activity.compose)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)

//    implementation(libs.compose.runtime)
//    implementation(libs.compose.foundation)
//    implementation(libs.compose.material3.material3)

    implementation(libs.vinceglb.filekit.dialogs.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.runner)
    androidTestImplementation(libs.espresso.core)
}