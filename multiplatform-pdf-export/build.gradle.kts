import de.charlex.convention.config.configureAndroidTarget
import de.charlex.convention.config.configureIosTargets
import de.charlex.convention.libs

plugins {
    id("de.charlex.convention.kotlin.multiplatform")
    id("de.charlex.convention.compose.multiplatform")
    id("de.charlex.convention.centralPublish")
}

mavenPublishConfig {
    description = "A Compose Multiplatform library for exporting UI content to PDF across desktop and mobile platforms."
    url = "https://github.com/ch4rl3x/HtmlText"

    scm {
        connection = "scm:git:github.com/ch4rl3x/HtmlText.git"
        developerConnection = "scm:git:ssh://github.com/ch4rl3x/HtmlText.git"
        url = "https://github.com/ch4rl3x/HtmlText/tree/main"
    }

    developers {
        developer {
            id = "ch4rl3x"
            name = "Alexander Karkossa"
            email = "alexander.karkossa@googlemail.com"
        }
        developer {
            id = "kalinjul"
            name = "Julian Kalinowski"
            email = "julakali@gmail.com"
        }
    }
}

kotlin {
    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.core)
        }
        jvmMain.dependencies {
            implementation(libs.apache.batik.transcoder)
            implementation(libs.apache.fop)
            implementation(libs.apache.pdfbox)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3.material3)
            implementation(libs.compose.components.resources)

        }
    }
}
