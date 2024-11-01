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
package com.android.tools.compose.formatting

import com.android.tools.compose.COMPOSE_UI_PACKAGE
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Test for [ComposePostFormatProcessor]. */
class ComposePostFormatProcessorTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val fixture: CodeInsightTestFixture by lazy { projectRule.fixture }

  private val project: Project by lazy { fixture.project }

  @Before
  fun setUp() {
    (fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    fixture.stubComposableAnnotation()
    fixture.addFileToProject(
      "src/${COMPOSE_UI_PACKAGE.replace(".", "/")}/Modifier.kt",
      // language=kotlin
      """
    package $COMPOSE_UI_PACKAGE

    interface Modifier {
      fun adjust(): Modifier
      companion object : Modifier {
        fun adjust(): Modifier {}
      }
    }
    """
        .trimIndent(),
    )

    val settings =
      CodeStyle.getSettings(project).getCustomSettings(KotlinCodeStyleSettings::class.java)
    settings.CONTINUATION_INDENT_FOR_CHAINED_CALLS = false
  }

  @Test
  fun modifierChainIsWrapped() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun HomeScreen() {
          val m = Modifier.adjust().adjust()
      }
      """
        .trimIndent(),
    )

    WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
      CodeStyleManager.getInstance(project)
        .reformatText(fixture.file, listOf(fixture.file.textRange))
    }

    fixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun HomeScreen() {
          val m = Modifier
              .adjust()
              .adjust()
      }
      """
        .trimIndent()
    )
  }

  @Test
  fun nestedChainInModifierIsNotWrapped() {
    // Regression test for b/364549431
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun BugRepro(modifier: Modifier) {
          SomeComposable(modifier = Modifier.onClick { "foo".someFunction().someFunction() }.secondCall())

          SomeComposable(modifier = Modifier.onClick {
            "foo".someFunction().someFunction()
          }.secondCall())
      }

      @Composable
      fun SomeComposable(modifier: Modifier) {
          TODO()
      }

      fun Modifier.onClick(onClick: @Composable () -> Unit): Modifier = this

      fun Modifier.secondCall(): Modifier = this

      fun Any.someFunction() = this
      """
        .trimIndent(),
    )

    WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
      CodeStyleManager.getInstance(project)
        .reformatText(fixture.file, listOf(fixture.file.textRange))
    }

    fixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun BugRepro(modifier: Modifier) {
          SomeComposable(modifier = Modifier
              .onClick { "foo".someFunction().someFunction() }
              .secondCall())

          SomeComposable(modifier = Modifier
              .onClick {
                  "foo".someFunction().someFunction()
              }
              .secondCall())
      }

      @Composable
      fun SomeComposable(modifier: Modifier) {
          TODO()
      }

      fun Modifier.onClick(onClick: @Composable () -> Unit): Modifier = this

      fun Modifier.secondCall(): Modifier = this

      fun Any.someFunction() = this
      """
        .trimIndent()
    )
  }

  @Test
  fun shortModifierChainIsNotWrapped() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun HomeScreen() {
          val m = Modifier.adjust()
      }
      """
        .trimIndent(),
    )

    WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
      CodeStyleManager.getInstance(project)
        .reformatText(fixture.file, listOf(fixture.file.textRange))
    }

    fixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun HomeScreen() {
          val m = Modifier.adjust()
      }
      """
        .trimIndent()
    )
  }
}
