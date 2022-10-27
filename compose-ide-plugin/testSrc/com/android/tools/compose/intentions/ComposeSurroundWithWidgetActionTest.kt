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
package com.android.tools.compose.intentions

import com.android.tools.idea.testing.loadNewFile
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.android.compose.stubComposableAnnotation

/**
 * Test for [ComposeSurroundWithWidgetActionGroup] and [ComposeSurroundWithWidgetAction]
 */
class ComposeSurroundWithWidgetActionTest : JavaCodeInsightFixtureTestCase() {
  public override fun setUp() {
    super.setUp()
    myFixture.stubComposableAnnotation()

    myFixture.addFileToProject(
      "src/androidx/compose/foundation/layout/ColumnAndRow.kt",
      // language=kotlin
      """
    package androidx.compose.foundation.layout

    import androidx.compose.Composable

    inline fun Row(content: @Composable () -> Unit) {}
    inline fun Column(content: @Composable () -> Unit) {}
    inline fun Box(content: @Composable () -> Unit) {}
    """.trimIndent()
    )
  }

  private fun invokeActionAndAssertResult(
    actionProvider: () -> IntentionAction,
    inputFileContent: String,
    expectedResult: String
  ) {
    myFixture.loadNewFile("src/com/example/Test.kt", inputFileContent)
    val action = actionProvider()
    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      // Within unit tests ListPopupImpl.showInBestPositionFor doesn't open popup and acts like fist item was selected.
      // In our case wrap in Box will be selected.
      action.invoke(myFixture.project, myFixture.editor, myFixture.file)
    }

    myFixture.checkResult(expectedResult)
  }

  private fun invokeActionAndAssertResult(
    actionName: String,
    inputFileContent: String,
    expectedResult: String
  ) {
    invokeActionAndAssertResult({
                                  val action = myFixture.availableIntentions.find { it.text == actionName }
                                  assertThat(action).isNotNull()
                                  action!!
                                }, inputFileContent, expectedResult)
  }

  private fun invokeActionAndAssertResult(
    action: IntentionAction,
    inputFileContent: String,
    expectedResult: String
  ) {
    invokeActionAndAssertResult({ action }, inputFileContent, expectedResult)
  }

  fun testSurroundWithAction() {
    invokeActionAndAssertResult(
      "Surround with widget",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          <selection>

          Text("A day in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")

          </selection><caret>
      }
      """.trimIndent(),
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable
      import androidx.compose.foundation.layout.Box

      @Composable
      fun NewsStory() {


          Box {
              Text("A day in Shark Fin Cove")
              Text("Davenport, California")
              Text("December 2018")
          }

          
      }
    """.trimIndent()
    )
  }

  fun testSurroundWithWidgetWithoutSelection() {
    invokeActionAndAssertResult(
      "Surround with widget",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          Text("A day <caret>in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")
      }
      """.trimIndent(),
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable
      import androidx.compose.foundation.layout.Box

      @Composable
      fun NewsStory() {
          Box {
              Text("A day in Shark Fin Cove")
          }
          Text("Davenport, California")
          Text("December 2018")
      }
    """.trimIndent())

    invokeActionAndAssertResult(
      "Surround with widget",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          Text("A day in Shark Fin Cove")
          <caret>Text("Davenport, California")
          Text("December 2018")
      }
      """.trimIndent(),
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable
      import androidx.compose.foundation.layout.Box

      @Composable
      fun NewsStory() {
          Text("A day in Shark Fin Cove")
          Box {
              Text("Davenport, California")
          }
          Text("December 2018")
      }
    """.trimIndent())

    invokeActionAndAssertResult(
      "Surround with widget",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          Text("A day in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")<caret>
      }
      """.trimIndent(),
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable
      import androidx.compose.foundation.layout.Box

      @Composable
      fun NewsStory() {
          Text("A day in Shark Fin Cove")
          Text("Davenport, California")
          Box {
              Text("December 2018")
          }
      }
    """.trimIndent())

    invokeActionAndAssertResult(
      "Surround with widget",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          Button(onClick = {}) {<caret>
              Text("Davenport, California")
          }
      }
      """.trimIndent(),
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable
      import androidx.compose.foundation.layout.Box

      @Composable
      fun NewsStory() {
          Box {
              Button(onClick = {}) {
                  Text("Davenport, California")
              }
          }
      }
    """.trimIndent())
  }

  /**
   * Checks the cases where the intention should not be available.
   */
  fun testSurroundWithWidgetWithoutSelectionNotAvailable() {
    val cases = listOf(
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable
      import androidx.compose.foundation.layout.Box

      @Composable
      fun NewsStory() {
          Box {
              Text("A day in Shark Fin Cove")
              Text("Davenport, California")
              Text("December 2018")
          }
      }
    """.trimIndent(),
      // language=kotlin
      """
    package com.example

    import androidx.compose.Composable
    import androidx.compose.foundation.layout.Box

    @Composable
    fun NewsStory<caret>() {
        Box {
            Text("A day in Shark Fin Cove")
            Text("Davenport, California")
            Text("December 2018")
        }
    }
    """.trimIndent(),
      // language=kotlin
      """
    package com.example

    import androidx.compose.Composable
    import androidx.compose.foundation.layout.Box

    @Composable
    fun NewsStory() {
        // A <caret>comment

        Box {
            Text("A day in Shark Fin Cove")
            Text("Davenport, California")
            Text("December 2018")
        }
    }
    """.trimIndent(),
      // language=kotlin
      """
    package com.example

    import androidx.compose.Composable
    import androidx.compose.foundation.layout.Box

    @Composable
    fun NewsStory() {<caret>
        Text("A day in Shark Fin Cove")
    }
    """.trimIndent()
    )

    cases.forEachIndexed { index, content ->
      myFixture.loadNewFile("src/com/example/Test${index}.kt", content)
      assertThat(myFixture.availableIntentions.map { intention -> intention.text }.toList())
        .doesNotContain("Surround with widget")
    }
  }

  /**
   * Checks surround with widget when the selection starts and/or stops in the middle or an element and not
   * in empty space.
   */
  fun testSurroundWithWidgetWithPartialSelection() {
    invokeActionAndAssertResult(
      "Surround with widget",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          Text("A day <selection>in Shark Fin Cove")
          Text("Davenport, Cali</selection>fornia")
          Text("December 2018")
      }
      """.trimIndent(),
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable
      import androidx.compose.foundation.layout.Box

      @Composable
      fun NewsStory() {
          Box {
              Text("A day in Shark Fin Cove")
              Text("Davenport, California")
          }
          Text("December 2018")
      }
    """.trimIndent())

    invokeActionAndAssertResult(
      "Surround with widget",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          Text("A day <selection>in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")
          // A comment
          </selection>
      }
      """.trimIndent(),
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable
      import androidx.compose.foundation.layout.Box

      @Composable
      fun NewsStory() {
          Box {
              Text("A day in Shark Fin Cove")
              Text("Davenport, California")
              Text("December 2018")
              // A comment 
          }
          
      }
    """.trimIndent())

    invokeActionAndAssertResult(
      "Surround with widget",
      // language=kotlin
      """
    package com.example

    import androidx.compose.Composable
    import androidx.compose.foundation.layout.Box
    import androidx.compose.foundation.layout.Column

    @Composable
    fun NewsStory() {
        Column {
            <selection>Text("A day in Shark Fin Cove")
            Text("Davenport, California")
            Text("December 2018")</selection>
        }
    }
    """.trimIndent(),
      // language=kotlin
      """
        package com.example

        import androidx.compose.Composable
        import androidx.compose.foundation.layout.Box
        import androidx.compose.foundation.layout.Column

        @Composable
        fun NewsStory() {
            Column {
                Box {
                    Text("A day in Shark Fin Cove")
                    Text("Davenport, California")
                    Text("December 2018")
                }
            }
        }
      """.trimIndent()
    )

    invokeActionAndAssertResult(
      "Surround with widget",
      // language=kotlin
      """
    package com.example

    import androidx.compose.Composable
    import androidx.compose.foundation.layout.Box
    import androidx.compose.foundation.layout.Column

    @Composable
    fun NewsStory() {
        <selection>// A comment
        Column {
            Text("A day in Shark Fin Cove")
            Text("Davenport, California")
            Text("December 2018")
        }</selection>
    }
    """.trimIndent(),
      // language=kotlin
      """
        package com.example

        import androidx.compose.Composable
        import androidx.compose.foundation.layout.Box
        import androidx.compose.foundation.layout.Column

        @Composable
        fun NewsStory() {
            Box {
                // A comment
                Column {
                    Text("A day in Shark Fin Cove")
                    Text("Davenport, California")
                    Text("December 2018")
                }
            }
        }
      """.trimIndent()
    )
  }

  /**
   * Checks the cases where the intention should not be available.
   */
  fun testSurroundWithWidgetWithPartialSelectionNotAvailable() {
    val cases = listOf(
      // language=kotlin
      """
    package com.example

    import androidx.compose.Composable
    import androidx.compose.foundation.layout.Box

    @Composable
    fun NewsStory<selection>() {
        Box {
            Text("A day in</selection> Shark Fin Cove")
            Text("Davenport, California")
            Text("December 2018")
        }
    }
    """.trimIndent(),
      // language=kotlin
      """
    package com.example

    import androidx.compose.Composable
    import androidx.compose.foundation.layout.Box

    @Composable
    fun NewsStory1() {
        Box {
            <selection>Text("A day in Shark Fin Cove")
            Text("Davenport, California")
            Text("December 2018")
        }
    }

    @Composable
    fun NewsStory2() {
        Box {
            Text("A day in Shark Fin Cove")
            Text("Davenport, California")</selection>
            Text("December 2018")
        }
    }
    """.trimIndent(),
      // language=kotlin
      """
    package com.example

    import androidx.compose.Composable
    import androidx.compose.foundation.layout.Box

    <selection>
    @Composable
    fun NewsStory1() {
        Box {
            Text("A day in Shark Fin Cove")
            Text("Davenport, California")
            Text("December 2018")
        }
    }

    @Composable
    fun NewsStory2() {
        Box {
            Text("A day in Shark Fin Cove")
            Text("Davenport, California")
            Text("December 2018")
        }
    }
    </selection>
    """.trimIndent(),
      // language=kotlin
      """
    package com.example

    import androidx.compose.Composable
    import androidx.compose.foundation.layout.Box


    @Composable
    fun NewsStory() {
        // A comment<selection>
        Box {
            Text("A day in Shark Fin Cove")
            Text("Davenport, California")</selection>
            Text("December 2018")
        }
    }
    """.trimIndent()
    )

    cases.forEachIndexed { index, content ->
      myFixture.loadNewFile("src/com/example/Test${index}.kt", content)
      assertWithMessage("'Surround with widget' intention not expected for:\n$content")
        .that(myFixture.availableIntentions.map { intention -> intention.text }.toList())
        .doesNotContain("Surround with widget")
    }
  }

  fun testSurroundWithBox() {
    invokeActionAndAssertResult(
      ComposeSurroundWithBoxAction(),
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          <selection>Text("A day in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")</selection><caret>
      }
      """.trimIndent(),
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable
      import androidx.compose.foundation.layout.Box

      @Composable
      fun NewsStory() {
          Box {
              Text("A day in Shark Fin Cove")
              Text("Davenport, California")
              Text("December 2018")
          }
      }
    """.trimIndent()
    )
  }

  fun testSurroundWithRow() {
    invokeActionAndAssertResult(
      ComposeSurroundWithRowAction(),
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          <selection>Text("A day in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")</selection><caret>
      }
      """.trimIndent(),
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable
      import androidx.compose.foundation.layout.Row

      @Composable
      fun NewsStory() {
          Row {
              Text("A day in Shark Fin Cove")
              Text("Davenport, California")
              Text("December 2018")
          }
      }
    """.trimIndent()
    )
  }

  fun testSurroundWithColumn() {
    invokeActionAndAssertResult(
      ComposeSurroundWithColumnAction(),
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          <selection>Text("A day in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")</selection><caret>
      }
      """.trimIndent(),
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable
      import androidx.compose.foundation.layout.Column

      @Composable
      fun NewsStory() {
          Column {
              Text("A day in Shark Fin Cove")
              Text("Davenport, California")
              Text("December 2018")
          }
      }
    """.trimIndent()
    )
  }
}