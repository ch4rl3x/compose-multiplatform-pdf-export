package de.charlex.convention

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import de.charlex.convention.libs
import kotlin.text.get

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
            }

            extensions.configure<ApplicationExtension> {
                compileSdk {
                    version = release(libs.versions.compileSdk.get().toInt())
                }

                defaultConfig {
                    minSdk = libs.versions.minSdk.get().toInt()
                    targetSdk = libs.versions.targetSdk.get().toInt()
                }

                // Can remove this once https://issuetracker.google.com/issues/260059413 is fixed.
                // See https://kotlinlang.org/docs/gradle-configure-project.html#gradle-java-toolchains-support
                compileOptions {
                    sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
                    targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
                }
            }
        }
    }
}