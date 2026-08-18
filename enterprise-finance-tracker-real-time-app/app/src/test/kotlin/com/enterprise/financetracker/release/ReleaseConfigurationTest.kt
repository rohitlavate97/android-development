package com.enterprise.financetracker.release

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class ReleaseConfigurationTest {

    @Test
    fun given_build_gradle_file_when_inspected_then_release_enables_minify_and_shrink_resources() {
        val buildFile = File("build.gradle.kts")
        val fileContent = if (buildFile.exists()) {
            buildFile.readText()
        } else {
            File("app/build.gradle.kts").readText()
        }

        assertThat(fileContent).contains("isMinifyEnabled = true")
        assertThat(fileContent).contains("isShrinkResources = true")
        assertThat(fileContent).contains("proguardFiles")
    }
}
