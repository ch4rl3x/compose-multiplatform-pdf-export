package de.charlex.convention

import de.charlex.convention.config.configureAndroidComposeMultiplatform
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.Actions.with
import org.gradle.kotlin.dsl.dependencies

class ComposeMultiplatformConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply(libs.plugins.composeMultiplatform.get().pluginId)
                apply(libs.plugins.compose.compiler.get().pluginId)
            }

            configureAndroidComposeMultiplatform()
        }
    }
}
