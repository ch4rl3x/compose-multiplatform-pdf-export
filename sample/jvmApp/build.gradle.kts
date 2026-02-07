import de.charlex.convention.libs
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("de.charlex.convention.kotlin.multiplatform")
    id("de.charlex.convention.compose.multiplatform")
    alias(libs.plugins.compose.hotReload)
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            implementation(projects.sample.shared)

            implementation(compose.desktop.currentOs)
            implementation(libs.compose.material3.material3)

            implementation(libs.vinceglb.filekit.dialogs.compose)
        }
    }
}


compose.desktop {
    application {
        mainClass = "de.charlex.compose.composetopdf.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "compose-to-pdf-sample"
            packageVersion = "1.0.0"
        }
    }
}
