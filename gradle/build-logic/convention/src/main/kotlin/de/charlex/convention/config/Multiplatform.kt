package de.charlex.convention.config

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import de.charlex.convention.libs
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

fun KotlinMultiplatformExtension.configureAndroidTarget(project: Project) {
    this.android {
        compileSdk = project.libs.versions.compileSdk.get().toInt()
        minSdk = project.libs.versions.minSdk.get().toInt()
        this.compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(project.libs.versions.javaVersion.get()))

            namespace = "de.charlex.compose.${project.name.replace("-", ".")}"
        }
    }
}

fun KotlinMultiplatformExtension.configureIosTargets(baseName: String? = null) {
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            this.baseName = baseName ?: project.path.substring(1).replace(':', '-')
                .replace("-", "_") // workaround for https://github.com/luca992/multiplatform-swiftpackage/issues/12
            isStatic = true
        }
    }
}

fun KotlinMultiplatformExtension.configureJvm() {
    jvm()
}

fun KotlinMultiplatformExtension.android(action: KotlinMultiplatformAndroidLibraryTarget.() -> Unit) {
    targets.withType<KotlinMultiplatformAndroidLibraryTarget>(action)
}