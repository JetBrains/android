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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.loadNewFile
import com.google.common.truth.Truth.assertThat
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
    StudioFlags.COMPOSE_EDITOR_SUPPORT.override(true)

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

  public override fun tearDown() {
    StudioFlags.COMPOSE_EDITOR_SUPPORT.clearOverride()
    super.tearDown()
  }

  fun testSurroundWithAction() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
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
      """.trimIndent()
    )

    val action = myFixture.availableIntentions.find { it.text == "Surround with widget" }
    assertThat(action).isNotNull()

    WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable {
      // Within unit tests ListPopupImpl.showInBestPositionFor doesn't open popup and acts like fist item was selected.
      // In our case wrap in Box will be selected.
      action!!.invoke(myFixture.project, myFixture.editor, myFixture.file)
    })

    myFixture.checkResult(
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

  fun testSurroundWithBox() {

    myFixture.loadNewFile(
      "src/com/example/Test.kt",
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
      """.trimIndent()
    )

    WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable {
      ComposeSurroundWithBoxAction().invoke(myFixture.project, myFixture.editor, myFixture.file)
    })

    myFixture.checkResult(
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

    myFixture.loadNewFile(
      "src/com/example/Test.kt",
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
      """.trimIndent()
    )

    WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable {
      ComposeSurroundWithRowAction().invoke(myFixture.project, myFixture.editor, myFixture.file)
    })
    myFixture.checkResult(
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

    myFixture.loadNewFile(
      "src/com/example/Test.kt",
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
      """.trimIndent()
    )

    WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable {
      ComposeSurroundWithColumnAction().invoke(myFixture.project, myFixture.editor, myFixture.file)
    })

    myFixture.checkResult(
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