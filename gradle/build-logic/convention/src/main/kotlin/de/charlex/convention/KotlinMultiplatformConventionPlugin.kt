package de.charlex.convention

import de.charlex.convention.config.configureAndroidTarget
import de.charlex.convention.config.configureIosTargets
import de.charlex.convention.config.configureJvm
import de.charlex.convention.config.configureKotlin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.internal.Actions.with
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

class KotlinMultiplatformConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // use mobile plugin and add jvm target
            with(pluginManager) {
//                apply("org.jetbrains.kotlin.jvm")
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.kotlin.multiplatform.library")
                apply(libs.plugins.compose.hotReload.get().pluginId)
            }

            extensions.configure<KotlinMultiplatformExtension> {
            }

            extensions.configure<KotlinMultiplatformExtension> {
                applyDefaultHierarchyTemplate()

                configureAndroidTarget(this@with)

                configureIosTargets()

                configureJvm()
            }

            configureKotlin()
        }
    }
}

//fun Project.addKspDependencyForAllTargets(dependencyNotation: Any) = addKspDependencyForAllTargets("", dependencyNotation)
//fun Project.addKspTestDependencyForAllTargets(dependencyNotation: Any) = addKspDependencyForAllTargets("Test", dependencyNotation)
//
//private fun Project.addKspDependencyForAllTargets(
//    configurationNameSuffix: String,
//    dependencyNotation: Any,
//) {
//    val kmpExtension = extensions.getByType<KotlinMultiplatformExtension>()
//    dependencies {
//        kmpExtension.targets
//            .asSequence()
//            .filter { target ->
//                // Don't add KSP for common target, only final platforms
//                target.platformType != KotlinPlatformType.common
//            }
//            .forEach { target ->
//                add(
//                    "ksp${target.targetName}$configurationNameSuffix",
//                    dependencyNotation,
//                )
//            }
//    }
//}
