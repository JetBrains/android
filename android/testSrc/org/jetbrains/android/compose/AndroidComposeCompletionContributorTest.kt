/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android.compose

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.loadNewFile
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.uipreview.AndroidEditorSettings

class AndroidComposeCompletionContributorTest : AndroidTestCase() {

  public override fun setUp() {
    super.setUp()
    StudioFlags.COMPOSE_EDITOR_SUPPORT.override(true)
    StudioFlags.COMPOSE_COMPLETION_PRESENTATION.override(true)
    StudioFlags.COMPOSE_COMPLETION_INSERT_HANDLER.override(true)
    StudioFlags.COMPOSE_COMPLETION_WEIGHER.override(true)
    (myModule.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    myFixture.stubComposableAnnotation(ANDROIDX_COMPOSE_PACKAGE)
  }

  override fun tearDown() {
    StudioFlags.COMPOSE_EDITOR_SUPPORT.clearOverride()
    StudioFlags.COMPOSE_COMPLETION_PRESENTATION.clearOverride()
    StudioFlags.COMPOSE_COMPLETION_INSERT_HANDLER.clearOverride()
    StudioFlags.COMPOSE_COMPLETION_WEIGHER.clearOverride()
    super.tearDown()
  }

  fun testSignatures() {
    myFixture.addFileToProject(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

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

      import androidx.compose.Composable

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

      import androidx.compose.Composable

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

  fun testInsertHandler() {
    // Given:
    myFixture.addFileToProject(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun FoobarOne(first: Int, second: String, third: String? = null) {}

      """.trimIndent()
    )

    val file = myFixture.addFileToProject(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

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

      import androidx.compose.Composable

      @Composable
      fun HomeScreen() {
        FoobarOne(first = , second = )
      }
      """.trimIndent()
    )
  }

  fun testInsertHandler_dont_insert_before_parenthesis() {
    // Given:
    myFixture.addFileToProject(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun FoobarOne(first: Int, second: String, third: String? = null) {}

      """.trimIndent()
    )

    var file = myFixture.addFileToProject(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

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

      import androidx.compose.Composable

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

      import androidx.compose.Composable

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

      import androidx.compose.Composable

      @Composable
      fun HomeScreen() {
        FoobarOne()
      }
      """.trimIndent()
    )
  }

  fun testInsertHandler_lambda() {
    // Given:
    myFixture.addFileToProject(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun FoobarOne(children: @Composable() () -> Unit) {}

      """.trimIndent()
    )

    val file = myFixture.addFileToProject(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

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

      import androidx.compose.Composable

      @Composable
      fun HomeScreen() {
        FoobarOne {

        }
      }
      """.trimIndent()
      , true)
  }

  fun testInsertHandler_lambda_before_curly_braces() {
    // Given:
    myFixture.addFileToProject(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun FoobarOne(children: @Composable() () -> Unit) {}

      """.trimIndent()
    )

    var file = myFixture.addFileToProject(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

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

      import androidx.compose.Composable

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

      import androidx.compose.Composable

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

      import androidx.compose.Composable

      @Composable
      fun HomeScreen() {
      // No space after caret.
        FoobarOne{

        }
      }
      """.trimIndent()
      , true)
  }

  fun testInsertHandler_lambdaWithOptional() {
    // Given:
    myFixture.addFileToProject(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun FoobarOne(optional: String? = null, children: @Composable() () -> Unit) {}

      """.trimIndent()
    )

    val file = myFixture.addFileToProject(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

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

      import androidx.compose.Composable

      @Composable
      fun HomeScreen() {
        FoobarOne() {

        }
      }
      """.trimIndent()
      , true)
  }

  fun testInsertHandler_onClick() {
    // Given:
    myFixture.addFileToProject(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun AppBarIcon(icon: String, onClick: () -> Unit) {}

      """.trimIndent()
    )

    val file = myFixture.addFileToProject(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

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

      import androidx.compose.Composable

      @Composable
      fun HomeScreen() {
        AppBarIcon(icon = , onClick = )
      }
      """.trimIndent()
      , true)
  }

  fun testInsertHandler_disabledThroughSettings() {
    // Given:
    myFixture.addFileToProject(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun FoobarOne(first: Int, second: String, third: String? = null) {}

      """.trimIndent()
    )

    val file = myFixture.addFileToProject(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun HomeScreen() {
        Foobar${caret}
      }
      """.trimIndent()
    )

    // When:
    AndroidEditorSettings.getInstance().globalState.isComposeInsertHandlerEnabled = false
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.completeBasic()

    // Then:
    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun HomeScreen() {
        FoobarOne()
      }
      """.trimIndent()
    )

    AndroidEditorSettings.getInstance().globalState.isComposeInsertHandlerEnabled = true
  }

  private val JavaCodeInsightTestFixture.renderedLookupElements: Collection<String>
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
