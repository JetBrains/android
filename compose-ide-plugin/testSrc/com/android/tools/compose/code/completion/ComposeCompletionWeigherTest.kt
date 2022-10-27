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

import com.android.tools.compose.ComposeFqNames
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.loadNewFile
import com.google.common.truth.Truth
import com.intellij.codeInsight.lookup.LookupElementPresentation
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
    myFixture.stubComposableAnnotation(ComposeFqNames.root)
  }

  /** Regression test for b/155314487. */
  @Test
  fun testLookupElementOrder_materialThemeInStatement() {
    myFixture.addFileToProject(
      "src/androidx/compose/material/MaterialTheme.kt",
      // language=kotlin
      """
      package androidx.compose.material

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
      "MaterialTheme (androidx.compose.material)",
      "MaterialTheme {...}",
      "MaterialTheme (com.example)",
    ).inOrder()
  }

  /** Regression test for b/155314487. */
  @Test
  fun testLookupElementOrder_materialThemeInArgument() {
    myFixture.addFileToProject(
      "src/androidx/compose/material/MaterialTheme.kt",
      // language=kotlin
      """
      package androidx.compose.material

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
    // b/155314487: This order is incorrect. I'm adding this test to document current behavior, and will update this test to the new
    // expected behavior once the fix is made.
    Truth.assertThat(myFixture.renderedLookupElements).containsExactly(
      "MaterialTheme (com.example)",
      "MaterialTheme {...}",
      "MaterialTheme (androidx.compose.material)",
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
      return lookupElements.orEmpty().map { lookupElement ->
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
