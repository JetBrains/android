/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.nav.safeargs.psi.kotlin.gradle

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.nav.safeargs.TestDataPaths
import com.android.tools.idea.nav.safeargs.extensions.replaceWithSaving
import com.android.tools.idea.nav.safeargs.project.NavigationResourcesModificationListener
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.fileUnderGradleRoot
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File

@RunsInEdt
class SafeArgsKtCompletionContributorTest {
  private val projectRule = AndroidGradleProjectRule()

  @get:Rule
  val restoreSafeArgsFlagRule = FlagRule(StudioFlags.NAV_SAFE_ARGS_SUPPORT)

  // The tests need to run on the EDT thread but we must initialize the project rule off of it
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  private val fixture get() = projectRule.fixture as JavaCodeInsightTestFixture

  @Before
  fun setUp() {
    StudioFlags.NAV_SAFE_ARGS_SUPPORT.override(true)
    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    projectRule.load(TestDataPaths.SIMPLE_KOTLIN_PROJECT) { projectRoot ->
      // Create a placeholder class that we can use as a hook to replace with various
      // versions throughout these tests.
      File(projectRoot, "app/src/main/java/com/example/myapplication/FooClass.kt").apply {
        parentFile.mkdirs()
        createNewFile()
        writeText(
          // language=kotlin
          """
            package com.example.myapplication
            class FooClass
          """.trimIndent())
      }
    }

    NavigationResourcesModificationListener.ensureSubscribed(fixture.project)
  }

  /**
   * Check args and directions classes shown up in the completions
   *
   * Test Project structure:
   * base app module(safe arg mode is on) --> lib dep module(safe arg mode is on)
   */
  @Test
  fun testBasicCompletion() {
    projectRule.requestSyncAndWait()

    val file = fixture.project.findAppModule().fileUnderGradleRoot("src/main/java/com/example/myapplication/FooClass.kt")
    WriteCommandAction.runWriteCommandAction(fixture.project) {
      file!!.replaceWithSaving(
        "class FooClass",
        //language=kotlin
        """
        class FooClass {
            fun myTest() {
                val argsClass = 
                val directionsClass = 
                val generatedClass = 
            }
        }
      """.trimIndent(),
        fixture.project)
    }

    fixture.openFileInEditor(file!!)

    // check args classes
    fixture.moveCaret("val argsClass = |")
    fixture.type("Args")
    fixture.completeBasic()
    val argsElements = fixture.lookupElements
      .map {
        val presentation = LookupElementPresentation()
        it.renderElement(presentation)
        presentation
      }
      .map { it.itemText + " " + it.tailText }

    assertThat(argsElements).containsAllOf(
      "FirstFragmentArgs  (com.example.mylibrary)",
      "SecondFragmentArgs  (com.example.myapplication)",
      "SecondFragmentArgs  (com.example.mylibrary)")

    // check directions classes
    fixture.moveCaret("val directionsClass = |")
    fixture.type("Directions")
    fixture.completeBasic()
    val directionsElements = fixture.lookupElements
      .map {
        val presentation = LookupElementPresentation()
        it.renderElement(presentation)
        presentation
      }
      .map { it.itemText + " " + it.tailText }

    assertThat(directionsElements).containsAllOf(
      "SecondFragmentDirections  (com.example.myapplication)",
      "FirstFragmentDirections  (com.example.mylibrary)",
      "SecondFragmentDirections  (com.example.mylibrary)")


    // check all safe args classes
    fixture.moveCaret("val generatedClass = |")
    fixture.completeBasic()
    val allElements = fixture.lookupElements
      .map {
        val presentation = LookupElementPresentation()
        it.renderElement(presentation)
        presentation
      }
      .filter { it.itemText!!.endsWith("Args") || it.itemText!!.endsWith("Directions") }
      .map { it.itemText + " " + it.tailText }

    assertThat(allElements).containsAllOf(
      "FirstFragmentArgs  (com.example.mylibrary)",
      "SecondFragmentArgs  (com.example.myapplication)",
      "SecondFragmentArgs  (com.example.mylibrary)",
      "SecondFragmentDirections  (com.example.myapplication)",
      "FirstFragmentDirections  (com.example.mylibrary)",
      "SecondFragmentDirections  (com.example.mylibrary)")
  }

  @Test
  fun testCompletionInImports() {
    projectRule.requestSyncAndWait()

    val file = fixture.project.findAppModule().fileUnderGradleRoot("src/main/java/com/example/myapplication/FooClass.kt")
    WriteCommandAction.runWriteCommandAction(fixture.project) {
      file!!.replaceWithSaving(
        "class FooClass",
        //language=kotlin
        """
        import com.
        import com.example.mylibrary.
        class FooClass
      """.trimIndent(),
        fixture.project)
    }

    fixture.openFileInEditor(file!!)

    // no safe args classes show up in completions
    fixture.moveCaret("import com.|")
    fixture.completeBasic()
    fixture.lookupElements
      .map {
        val presentation = LookupElementPresentation()
        it.renderElement(presentation)
        presentation
      }
      .filter { it.itemText!!.endsWith("Args") || it.itemText!!.endsWith("Directions") }
      .let { assertThat(it).isEmpty() }

    // all safe args classes in mylibrary package show up in completions
    // (Just for sanity check, completions are not provided by [SafeArgsKtCompletionContributor])
    fixture.moveCaret("import com.example.mylibrary.|")
    fixture.completeBasic()
    val allElements = fixture.lookupElements
      .map {
        val presentation = LookupElementPresentation()
        it.renderElement(presentation)
        presentation
      }
      .filter { it.itemText!!.endsWith("Args") || it.itemText!!.endsWith("Directions") }
      .map { it.itemText + " " + it.tailText }

    assertThat(allElements).containsAllOf(
      "FirstFragmentArgs  (com.example.mylibrary)",
      "FirstFragmentDirections  (com.example.mylibrary)",
      "SecondFragmentArgs  (com.example.mylibrary)",
      "SecondFragmentDirections  (com.example.mylibrary)")
  }

  @Test
  fun testCompletionWithReceiver() {
    projectRule.requestSyncAndWait()

    val file = fixture.project.findAppModule().fileUnderGradleRoot("src/main/java/com/example/myapplication/FooClass.kt")
    WriteCommandAction.runWriteCommandAction(fixture.project) {
      file!!.replaceWithSaving(
        "class FooClass",
        //language=kotlin
        """
        class FooClass {
          val a = com.
          val b = com.example.mylibrary.
        }
      """.trimIndent(),
        fixture.project)
    }

    fixture.openFileInEditor(file!!)

    // no safe args classes show up in completions
    fixture.moveCaret("com.|")
    fixture.completeBasic()
    fixture.lookupElements
      .map {
        val presentation = LookupElementPresentation()
        it.renderElement(presentation)
        presentation
      }
      .filter { it.itemText!!.endsWith("Args") || it.itemText!!.endsWith("Directions") }
      .let { assertThat(it).isEmpty() }

    // all safe args classes in mylibrary package show up in completions
    // (Just for sanity check, completions are not provided by [SafeArgsKtCompletionContributor])
    fixture.moveCaret("com.example.mylibrary.|")
    fixture.completeBasic()
    val allElements = fixture.lookupElements
      .map {
        val presentation = LookupElementPresentation()
        it.renderElement(presentation)
        presentation
      }
      .filter { it.itemText!!.endsWith("Args") || it.itemText!!.endsWith("Directions") }
      .map { it.itemText + " " + it.tailText }

    assertThat(allElements).containsAllOf(
      "FirstFragmentArgs  (com.example.mylibrary)",
      "FirstFragmentDirections  (com.example.mylibrary)",
      "SecondFragmentArgs  (com.example.mylibrary)",
      "SecondFragmentDirections  (com.example.mylibrary)")
  }
}