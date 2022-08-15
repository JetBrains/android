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
package com.android.tools.compose.code.completion

import com.android.tools.compose.ComposeFqNames
import com.android.tools.compose.ComposeSettings
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.loadNewFile
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [ComposeCompletionContributor].
 */
class ComposeCompletionContributorTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val myFixture: CodeInsightTestFixture by lazy { projectRule.fixture }

  @Before
  fun setUp() {
    StudioFlags.COMPOSE_EDITOR_SUPPORT.override(true)
    StudioFlags.COMPOSE_COMPLETION_PRESENTATION.override(true)
    StudioFlags.COMPOSE_COMPLETION_INSERT_HANDLER.override(true)
    StudioFlags.COMPOSE_COMPLETION_WEIGHER.override(true)
    (myFixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    myFixture.stubComposableAnnotation(ComposeFqNames.root)
  }

  @Test
  fun testSignatures() {
    myFixture.addFileToProject(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      // "Foobar" is a unique prefix that no other lookup elements will match.

      @Composable
      fun FoobarOne(required: Int) {}

      @Composable
      fun FoobarTwo(required: Int, optional: Int = 42) {}

      @Composable
      fun FoobarThree(optional: Int = 42, children: @Composable() () -> Unit) {}

      @Composable
      fun FoobarFour(children: @Composable() () -> Unit) {}

      @Composable
      fun FoobarFive(icon: String, onClick: () -> Unit) {}
      """.trimIndent()
    )

    val expectedLookupItems = listOf(
      "FoobarOne(required: Int)",
      "FoobarTwo(required: Int, ...)",
      "FoobarThree(...) {...}",
      "FoobarFour {...}",
      "FoobarFive(icon: String, onClick: () -> Unit)"
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
        Foobar${caret}
      }
      """.trimIndent()
    )

    // When:
    myFixture.completeBasic()

    // Then:
    assertThat(myFixture.renderedLookupElements).containsExactlyElementsIn(expectedLookupItems)

    // Given:
    myFixture.loadNewFile(
      "src/com/example/Test2.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      fun setContent(content: @Composable() () -> Unit) { TODO() }

      class MainActivity {
        fun onCreate() {
          setContent {
            Foobar${caret}
          }
        }
      }
      """.trimIndent()
    )

    // When:
    myFixture.completeBasic()

    // Then:
    assertThat(myFixture.renderedLookupElements).containsExactlyElementsIn(expectedLookupItems)
  }

  @Test
  fun testInsertHandler() {
    // Given:
    myFixture.addFileToProject(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun FoobarOne(first: Int, second: String, third: String? = null) {}

      """.trimIndent()
    )

    val file = myFixture.addFileToProject(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        Foobar${caret}
      }
      """.trimIndent()
    )

    // When:
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.completeBasic()

    // Then:
    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        FoobarOne(first = , second = )
      }
      """.trimIndent()
    )
  }

  @Test
  fun testInsertHandler_dont_insert_before_parenthesis() {
    // Given:
    myFixture.addFileToProject(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun FoobarOne(first: Int, second: String, third: String? = null) {}

      """.trimIndent()
    )

    var file = myFixture.addFileToProject(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        Foobar${caret}()
      }
      """.trimIndent()
    )

    // When:
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.completeBasic()

    // Then:
    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        FoobarOne()
      }
      """.trimIndent()
    )


    // Check completion with tab
    file = myFixture.addFileToProject(
      "src/com/example/Test2.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        ${caret}()
      }
      """.trimIndent()
    )

    // When:
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.completeBasic()
    myFixture.type("Foobar\t")

    // Then:
    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        FoobarOne()
      }
      """.trimIndent()
    )
  }

  @Test
  fun testInsertHandler_lambda() {
    // Given:
    myFixture.addFileToProject(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun FoobarOne(children: @Composable() () -> Unit) {}

      @Composable
      fun FoobarTwo(children: () -> Unit) {}

      """.trimIndent()
    )

    val file = myFixture.addFileToProject(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        FoobarO${caret}
      }
      """.trimIndent()
    )

    // When:
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.completeBasic()

    // Then:
    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        FoobarOne {

        }
      }
      """.trimIndent()
      , true)

    val file2 = myFixture.loadNewFile(
      "src/com/example/Test2.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        FoobarT${caret}
      }
      """.trimIndent()
    )

    // When:
    myFixture.configureFromExistingVirtualFile(file2.virtualFile)
    myFixture.completeBasic()

    // Then:
    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        FoobarTwo {
          
        }
      }
      """.trimIndent()
      , true)
  }

  @Test
  fun testInsertHandler_lambda_before_curly_braces() {
    // Given:
    myFixture.addFileToProject(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun FoobarOne(children: @Composable() () -> Unit) {}

      """.trimIndent()
    )

    var file = myFixture.addFileToProject(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
      // Space after caret.
        $caret {

        }
      }
      """.trimIndent()
    )

    // When:
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.type("Foobar")
    myFixture.completeBasic()

    // Then:
    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
      // Space after caret.
        FoobarOne {

        }
      }
      """.trimIndent()
      , true)

    // Given:
    file = myFixture.addFileToProject(
      "src/com/example/Test2.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
      // No space after caret.
        $caret{

        }
      }
      """.trimIndent()
    )

    // When:
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.type("Foobar")
    myFixture.completeBasic()

    // Then:
    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
      // No space after caret.
        FoobarOne{

        }
      }
      """.trimIndent()
      , true)
  }

  @Test
  fun testInsertHandler_lambdaWithOptional() {
    // Given:
    myFixture.addFileToProject(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun FoobarOne(optional: String? = null, children: @Composable() () -> Unit) {}

      """.trimIndent()
    )

    val file = myFixture.addFileToProject(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        Foobar${caret}
      }
      """.trimIndent()
    )

    // When:
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.completeBasic()

    // Then:
    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        FoobarOne() {

        }
      }
      """.trimIndent()
      , true)
  }

  @Test
  fun testInsertHandler_onClick() {
    // Given:
    myFixture.addFileToProject(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun AppBarIcon(icon: String, onClick: () -> Unit) {}

      """.trimIndent()
    )

    val file = myFixture.addFileToProject(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        AppBarIcon${caret}
      }
      """.trimIndent()
    )

    // When:
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.completeBasic()

    // Then:
    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        AppBarIcon(icon = ) {
          
        }
      }
      """.trimIndent()
      , true)
  }

  @Test
  fun testInsertHandler_onClick_lastParameterIsNotLambdaOrRequired() {
    // Given:
    myFixture.addFileToProject(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun RadioButton(text: String, onClick: () -> Unit, label: String = "label") {}

      """.trimIndent()
    )

    val file = myFixture.addFileToProject(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RadioButton${caret}
      }
      """.trimIndent()
    )

    // When:
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.completeBasic()

    // Then:
    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RadioButton(text = , onClick = { /*TODO*/ })
      }
      """.trimIndent()
      , true)
  }

  @Test
  fun testInsertHandler_disabledThroughSettings() {
    // Given:
    myFixture.addFileToProject(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun FoobarOne(first: Int, second: String, third: String? = null) {}

      """.trimIndent()
    )

    val file = myFixture.addFileToProject(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        Foobar${caret}
      }
      """.trimIndent()
    )

    // When:
    ComposeSettings.getInstance().state.isComposeInsertHandlerEnabled = false
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.completeBasic()

    // Then:
    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        FoobarOne()
      }
      """.trimIndent()
    )

    ComposeSettings.getInstance().state.isComposeInsertHandlerEnabled = true
  }

  @Test
  fun testInsertHandler_inKDoc() {
    // Given:
    myFixture.addFileToProject(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun FoobarOne(first: Int, second: String, third: String? = null) {}

      """.trimIndent()
    )

    val file = myFixture.addFileToProject(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      /**
       * [Foobar${caret}]
       */
      @Composable
      fun HomeScreen() {
      }
      """.trimIndent()
    )

    // When:
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.completeBasic()

    // Then:
    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      /**
       * [FoobarOne]
       */
      @Composable
      fun HomeScreen() {
      }
      """.trimIndent()
    )
  }

  @Test
  fun testMaterialThemeComposableIsDemotedInCompletion() {
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
        Material${caret}
      }
      """.trimIndent()
    )

    // When:
    myFixture.completeBasic()

    // Then:
    assertThat(myFixture.renderedLookupElements).containsExactlyElementsIn(
      listOf(
        "MaterialTheme (androidx.compose.material)",
        "MaterialTheme {...}",
        "MaterialTheme (com.example)",
        )
    ).inOrder()
  }

  /**
   * Regression test for b/153769933. The Compose insertion handler adds the parameters automatically when completing the name
   * of a Composable. This is incorrect if the insertion point is not a call statement. This ensures that the insertion is not triggered
   * for imports.
   */
  @Test
  fun testImportCompletionDoesNotTriggerInsertionHandler() {
    myFixture.addFileToProject(
      "src/androidx/compose/foundation/Canvas.kt",
      // language=kotlin
      """
      package androidx.compose.foundation

      import androidx.compose.runtime.Composable

      // This simulates the Canvas composable
      @Composable
      fun Canvas(children: @Composable() () -> Unit) {}
    """)


    // Given:
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Ca${caret}

      @Composable
      fun Test() {
      }
      """.trimIndent()
    )

    // When:
    myFixture.completeBasic()

    // Then:
    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.Canvas

      @Composable
      fun Test() {
      }
      """.trimIndent()
      , true)
  }

  /**
   * Regression test for b/209672710. Ensure that completing Composables that are not top-level does not fully qualify them incorrectly.
   */
  @Test
  fun testCompletingComposablesWithinObjects() {
    myFixture.addFileToProject(
      "src/com/example/ObjectWithComposables.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      object ObjectWithComposables {
        // This simulates the Canvas composable
        @Composable
        fun TestMethod(children: @Composable() () -> Unit) {}
      }
    """)


    // Given:
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun Test() {
        ObjectWithComposables.Test${caret}
      }
      """.trimIndent()
    )

    // When:
    myFixture.completeBasic()

    // Then:
    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun Test() {
        ObjectWithComposables.TestMethod {

        }
      }
      """.trimIndent()
      , true)
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
