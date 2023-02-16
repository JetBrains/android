/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.compose.code.completion

import com.android.tools.compose.COMPOSABLE_FQ_NAMES_ROOT
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.loadNewFile
import com.google.common.truth.Truth
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ComposeCompletionWeigherTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val myFixture: CodeInsightTestFixture by lazy { projectRule.fixture }

  @Before
  fun setUp() {
    (myFixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    myFixture.stubComposableAnnotation(COMPOSABLE_FQ_NAMES_ROOT)
  }

  /** Regression test for b/155314487. */
  @Test
  fun testLookupElementOrder_materialThemeInStatement() {
    doTestLookupElementOrder_materialThemeInStatement("androidx.compose.material")
  }

  /** Regression test for b/155314487. */
  @Test
  fun testLookupElementOrder_materialTheme3InStatement() {
    doTestLookupElementOrder_materialThemeInStatement("androidx.compose.material3")
  }

  fun doTestLookupElementOrder_materialThemeInStatement(materialThemePackage: String) {
    myFixture.addFileToProject(
      "src/${materialThemePackage.replace('.', '/')}/MaterialTheme.kt",
      // language=kotlin
      """
      package $materialThemePackage

      import androidx.compose.runtime.Composable

      // This simulates the Composable function
      @Composable
      fun MaterialTheme(children: @Composable() () -> Unit) {}

      // This simulates the MaterialTheme object that should be promoted instead of the MaterialTheme
      object MaterialTheme
      """)


    // Add a MaterialTheme that is not part of androidx to ensure is not affected by the promotion/demotion
    myFixture.addFileToProject(
      "src/com/example/MaterialTheme.kt",
      // language=kotlin
      """
      package com.example

      object MaterialTheme
      """)


    // Given:
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        Material$caret
      }
      """.trimIndent()
    )

    // When:
    myFixture.completeBasic()

    // Then:
    Truth.assertThat(myFixture.renderedLookupElements).containsExactly(
      "MaterialTheme ($materialThemePackage)",
      "MaterialTheme {...}",
      "MaterialTheme (com.example)",
    ).inOrder()
  }

  /** Regression test for b/155314487. */
  @Test
  fun testLookupElementOrder_materialThemeInArgument() {
    doTestLookupElementOrder_materialThemeInArgument("androidx.compose.material")
  }

  /** Regression test for b/155314487. */
  @Test
  fun testLookupElementOrder_materialTheme3InArgument() {
    doTestLookupElementOrder_materialThemeInArgument("androidx.compose.material3")
  }

  fun doTestLookupElementOrder_materialThemeInArgument(materialThemePackage: String) {
    myFixture.addFileToProject(
      "src/${materialThemePackage.replace('.', '/')}/MaterialTheme.kt",
      // language=kotlin
      """
      package $materialThemePackage

      import androidx.compose.runtime.Composable

      // This simulates the Composable function
      @Composable
      fun MaterialTheme(children: @Composable() () -> Unit) {}

      // This simulates the MaterialTheme object that should be promoted instead of the MaterialTheme
      object MaterialTheme
      """)


    // Add a MaterialTheme that is not part of androidx to ensure is not affected by the promotion/demotion
    myFixture.addFileToProject(
      "src/com/example/MaterialTheme.kt",
      // language=kotlin
      """
      package com.example

      object MaterialTheme
      """)


    // Add Color so it can be referenced without causing a missing reference.
    myFixture.addFileToProject(
      "src/androidx/compose/ui/graphics/Color.kt",
      // language=kotlin
      """
      package androidx.compose.ui.graphics

      class Color
      """)


    // Given:
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.graphics.Color

      @Composable
      fun HomeScreen() {
        HomeScreenElement(color = Material<caret>)
      }

      @Composable
      fun HomeScreenElement(color: Color) {}
      """.trimIndent()
    )

    // When:
    myFixture.completeBasic()

    // Then:
    Truth.assertThat(myFixture.renderedLookupElements).containsExactly(
      "MaterialTheme ($materialThemePackage)",
      "MaterialTheme (com.example)",
      "MaterialTheme {...}",
    ).inOrder()
  }

  @Test
  fun testLookupElementOrder_valueArgumentWithDotExpression() {
    // This test applies to any value argument being filled in with a dot expression, as in "icon = Icons.<caret>". Using Icons specifically
    // just because they can fulfill that scenario, and the autocomplete list would be in a different order if the weighing code didn't run.
    myFixture.addFileToProject(
      "src/androidx/compose/material/icons/Icons.kt",
      // language=kotlin
      """
      package androidx.compose.material.icons

      object Icons {
        final val Default = Filled
        object Filled
        object Outlined
        object Rounded
        object Sharp
        object TwoTone
      }
      """)

    // Given:
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.material.icons.Icons
      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        HomeScreenElement(icon = Icons.<caret>)
      }

      @Composable
      fun HomeScreenElement(icon: Any) {}
      """.trimIndent()
    )

    // When:
    myFixture.completeBasic()

    // Then:
    val renderedLookupElements = myFixture.renderedLookupElements

    // There should be at least one more suggestion that's not one of the Icons object, but we don't really care what it is as long as it's
    // ranked lower than the Icons entries.
    Truth.assertThat(renderedLookupElements.size).isAtLeast(7)
    Truth.assertThat(renderedLookupElements.toList().subList(0, 6)).containsExactly(
      "Defaultnull Icons.Filled",
      "Filled (androidx.compose.material.icons.Icons)",
      "Outlined (androidx.compose.material.icons.Icons)",
      "Rounded (androidx.compose.material.icons.Icons)",
      "Sharp (androidx.compose.material.icons.Icons)",
      "TwoTone (androidx.compose.material.icons.Icons)"
    ).inOrder()
  }

  @Test
  fun testLookupElementOrder_namedArgument() {
    myFixture.addFileToProject(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      // "Foobar" is a unique prefix that no other lookup elements will match.

      fun foobarOne(required: Int): Int = 1

      fun foobarTwo(required: Int, optional: Int = 42): Int = 2

      @Composable
      fun WrappingFunction(foobarArg: Int) {}
      """.trimIndent()
    )

    // Given:
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        WrappingFunction(foobar<caret>)
      }
      """.trimIndent()
    )

    // When:
    myFixture.completeBasic()

    // Then:
    Truth.assertThat(myFixture.renderedLookupElements).containsExactly(
      "foobarArg = Int",
      "foobarOne(required: Int) (com.example) Int",
      "foobarTwo(required: Int, optional: Int = ...) (com.example) Int",
    ).inOrder()
  }

  private val CodeInsightTestFixture.renderedLookupElements: Collection<String>
    get() {
      return runReadAction {
        lookupElements.orEmpty().map { lookupElement ->
          val presentation = LookupElementPresentation()
          lookupElement.renderElement(presentation)
          buildString {
            append(presentation.itemText)
            append(presentation.tailText)
            if (!presentation.typeText.isNullOrEmpty()) {
              append(" ")
              append(presentation.typeText)
            }
          }
        }
      }
    }
}
