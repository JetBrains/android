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
import com.intellij.codeInsight.lookup.DefaultLookupItemRenderer
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.util.PlatformIcons
import org.jetbrains.kotlin.idea.KotlinIcons
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File

@RunsInEdt
class SafeArgsIconsRenderingTest {
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

  @Test
  fun testClassIcons() {
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
    var icons = fixture.lookupElements
      .filter { it.lookupString.endsWith("FragmentArgs") }
      .map { DefaultLookupItemRenderer.getRawIcon(it) }
      .toSet()
    assertThat(icons).containsExactly(PlatformIcons.CLASS_ICON)

    // check directions classes
    fixture.moveCaret("val directionsClass = |")
    fixture.type("Directions")
    fixture.completeBasic()
    icons = fixture.lookupElements
      .filter { it.lookupString.endsWith("FragmentDirections") }
      .mapNotNull { DefaultLookupItemRenderer.getRawIcon(it) }
      .toSet()
    assertThat(icons).containsExactly(PlatformIcons.CLASS_ICON)
  }

  @Test
  fun testMethodAndPropertyIcons() {
    projectRule.requestSyncAndWait()

    val file = fixture.project.findAppModule().fileUnderGradleRoot("src/main/java/com/example/myapplication/FooClass.kt")
    WriteCommandAction.runWriteCommandAction(fixture.project) {
      file!!.replaceWithSaving(
        "class FooClass",
        //language=kotlin
        """
        class FooClass {
            fun myTest() {
                val argsClass1 = SecondFragmentArgs.
                val directionsClass1 = SecondFragmentDirections.
                
                val argsClass2 = SecondFragmentArgs().
                val directionsClass2 = SecondFragmentDirections().
            }
        }
      """.trimIndent(),
        fixture.project)
    }

    fixture.openFileInEditor(file!!)

    // check static method from args class
    fixture.moveCaret("val argsClass1 = SecondFragmentArgs.|")
    fixture.completeBasic()
    var icons = fixture.lookupElements
      .map { it.lookupString to DefaultLookupItemRenderer.getRawIcon(it) }
      .toSet()
    assertThat(icons).contains("fromBundle" to PlatformIcons.FUNCTION_ICON)

    // check static method from directions class
    fixture.moveCaret("val directionsClass1 = SecondFragmentDirections.|")
    fixture.completeBasic()
    icons = fixture.lookupElements
      .mapNotNull { it.lookupString to DefaultLookupItemRenderer.getRawIcon(it) }
      .toSet()
    assertThat(icons).contains("actionSecondFragmentToFirstFragment" to PlatformIcons.FUNCTION_ICON)

    // check methods from args class
    fixture.moveCaret("val argsClass2 = SecondFragmentArgs().|")

    fixture.completeBasic()
    icons = fixture.lookupElements
      .map { it.lookupString to DefaultLookupItemRenderer.getRawIcon(it) }
      .toSet()
    assertThat(icons).containsAllOf(
      // componentN() functions of data class are filtered out when collecting variants during completions.
      "arg1" to KotlinIcons.FIELD_VAL,
      "copy" to PlatformIcons.FUNCTION_ICON,
      "toBundle" to PlatformIcons.FUNCTION_ICON
    )

    // directions class only has companion object
  }
}