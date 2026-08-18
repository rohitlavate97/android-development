package com.enterprise.financetracker.release

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class ProGuardRulesSanityTest {

    @Test
    fun given_proguard_rules_file_when_inspected_then_contains_essential_keep_rules() {
        val rulesFile = File("proguard-rules.pro")
        // If running from module directory
        val fileContent = if (rulesFile.exists()) {
            rulesFile.readText()
        } else {
            File("app/proguard-rules.pro").readText()
        }

        assertThat(fileContent).contains("kotlinx.serialization")
        assertThat(fileContent).contains("androidx.room.RoomDatabase")
        assertThat(fileContent).contains("retrofit2")
        assertThat(fileContent).contains("assumenosideeffects class android.util.Log")
    }

    @Test
    fun given_proguard_rules_when_checked_for_line_numbers_then_LineNumberTable_is_preserved() {
        val rulesFile = File("proguard-rules.pro")
        val fileContent = if (rulesFile.exists()) {
            rulesFile.readText()
        } else {
            File("app/proguard-rules.pro").readText()
        }

        assertThat(fileContent).contains("-keepattributes LineNumberTable,SourceFile")
    }
}
