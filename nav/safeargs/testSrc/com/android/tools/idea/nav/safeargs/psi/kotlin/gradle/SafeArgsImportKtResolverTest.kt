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
import com.android.tools.idea.nav.safeargs.extensions.setText
import com.android.tools.idea.nav.safeargs.project.NavigationResourcesModificationListener
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.fileUnderGradleRoot
import com.android.tools.idea.testing.findAppModule
import com.google.common.truth.Truth.assertThat
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
class SafeArgsImportKtResolverTest {
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
      File(projectRoot, "app/src/main/java/com/example/myapplication/FooClass.kt").apply {
        parentFile.mkdirs()
        createNewFile()
        writeText(
          // language=kotlin
          """
            package com.example.myapplication

            class FooClass {
                fun myTest() {
                    val argsClass1 = First${caret}FragmentArgs.
                    val argsClass2 = FirstFragmentArgs().
                }
            }
          """.trimIndent())
      }
    }
    NavigationResourcesModificationListener.ensureSubscribed(fixture.project)
  }

  @Test
  fun testImportFixWithSingleSuggestion() {
    projectRule.requestSyncAndWait()

    val file = fixture.project.findAppModule().fileUnderGradleRoot("src/main/java/com/example/myapplication/FooClass.kt")
    fixture.configureFromExistingVirtualFile(file!!)

    // Before auto import fix
    val unresolvedReferences = fixture.doHighlighting()
      .filter { it.description?.contains("[UNRESOLVED_REFERENCE]") == true }

    assertThat(unresolvedReferences).hasSize(2)

    // Apply auto import fix
    fixture.getAvailableIntention("Import")?.invoke(fixture.project, fixture.editor, fixture.file)

    // After fix
    fixture.checkResult(
      // language=kotlin
      """
        package com.example.myapplication
        
        import com.example.mylibrary.FirstFragmentArgs
        
        class FooClass {
            fun myTest() {
                val argsClass1 = First${caret}FragmentArgs.
                val argsClass2 = FirstFragmentArgs().
            }
        }
      """.trimIndent())
  }

  @Test
  fun testImportFixWithAmbiguities() {
    projectRule.requestSyncAndWait()

    val file = fixture.project.findAppModule().fileUnderGradleRoot("src/main/java/com/example/myapplication/FooClass.kt")
    WriteCommandAction.runWriteCommandAction(fixture.project) {
      file!!.setText(
        //language=kotlin
        """
          class FooClass {
              fun myTest() {
                  val argsClass1 = Second${caret}FragmentArgs.
                  val argsClass2 = SecondFragmentArgs().
              }
          }
        """.trimIndent(),
        fixture.project)
    }
    fixture.configureFromExistingVirtualFile(file!!)

    // Before auto import fix
    val unresolvedReferences = fixture.doHighlighting()
      .filter { it.description?.contains("[UNRESOLVED_REFERENCE]") == true }

    assertThat(unresolvedReferences).hasSize(2)

    // Apply auto import fix: though first option is selected by default if it's unit test.
    // In this test, it has 2 options, 'import com.example.mylibrary.SecondFragmentArgs'
    // and 'import com.example.myapplication.SecondFragmentArgs', as package name is missing
    // here.
    fixture.getAvailableIntention("Import")?.invoke(fixture.project, fixture.editor, fixture.file)

    // After fix
    fixture.checkResult(
      // language=kotlin
      """
        import com.example.mylibrary.SecondFragmentArgs

        class FooClass {
            fun myTest() {
                val argsClass1 = Second${caret}FragmentArgs.
                val argsClass2 = SecondFragmentArgs().
            }
        }
      """.trimIndent())
  }

  @Test
  fun testImportFixForCompanionFunctions() {
    projectRule.requestSyncAndWait()

    val file = fixture.project.findAppModule().fileUnderGradleRoot("src/main/java/com/example/myapplication/FooClass.kt")
    WriteCommandAction.runWriteCommandAction(fixture.project) {
      file!!.setText(
        //language=kotlin
        """
          package com.example.myapplication

          class FooClass {
              fun myTest() {
                  val argsClass1 = from${caret}Bundle()
              }
          }
        """.trimIndent(),
        fixture.project)
    }
    fixture.configureFromExistingVirtualFile(file!!)

    // Before auto import fix
    val unresolvedReferences = fixture.doHighlighting()
      .filter { it.description?.contains("[UNRESOLVED_REFERENCE]") == true }

    assertThat(unresolvedReferences).hasSize(1)

    fixture.getAvailableIntention("Import")?.invoke(fixture.project, fixture.editor, fixture.file)

    // After fix
    fixture.checkResult(
      // language=kotlin
      """
        package com.example.myapplication

        import com.example.mylibrary.FirstFragmentArgs.Companion.fromBundle

        class FooClass {
            fun myTest() {
                val argsClass1 = from${caret}Bundle()
            }
        }
      """.trimIndent())
  }
}