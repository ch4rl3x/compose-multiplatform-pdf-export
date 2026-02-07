import de.charlex.convention.libs
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("de.charlex.convention.kotlin.multiplatform")
    id("de.charlex.convention.compose.multiplatform")
}

kotlin {
//    listOf(
//        iosArm64(),
//        iosSimulatorArm64()
//    ).forEach { iosTarget ->
//        iosTarget.binaries.framework {
//            baseName = "ComposeApp"
//            isStatic = true
//        }
//    }
    
    sourceSets {
//        androidMain.dependencies {
//            implementation(libs.compose.uiToolingPreview)
//            implementation(libs.androidx.activity.compose)
//        }
        commonMain.dependencies {
            api(projects.multiplatformPdfExport)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3.material3)

            implementation(libs.vinceglb.filekit.dialogs.compose)


//            implementation(libs.compose.foundation)
//            implementation(libs.compose.material3)
//            implementation(libs.compose.ui)
//            implementation(libs.compose.components.resources)
//            implementation(libs.compose.uiToolingPreview)
//            implementation(libs.androidx.lifecycle.viewmodelCompose)
//            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
//        commonTest.dependencies {
//            implementation(libs.kotlin.test)
//        }
    }
}

dependencies {
//    debugImplementation(libs.compose.uiTooling)
}

