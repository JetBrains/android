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
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.android.compose.stubPreviewAnnotation

/**
 * Test for [ComposeCreatePreviewAction]
 */
class ComposeCreatePreviewActionTest : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.stubComposableAnnotation()
    myFixture.stubPreviewAnnotation()
  }

  fun testCursorAtAnnotation() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      <caret>@Composable
      fun NewsStory() {
          Text("A day in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")
      }
      """.trimIndent()
    )

    val action = myFixture.availableIntentions.find { it.text == "Create Preview" }
    assertThat(action).isNotNull()

    WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable {
      // Within unit tests ListPopupImpl.showInBestPositionFor doesn't open popup and acts like fist item was selected.
      // In our case wrap in Container will be selected.
      action!!.invoke(myFixture.project, myFixture.editor, myFixture.file)
    })

    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable
      import androidx.compose.ui.tooling.preview.Preview

      @Preview
      @Composable
      fun NewsStory() {
          Text("A day in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")
      }
    """.trimIndent()
    )
  }

  fun testSelection() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable
      <caret><selection>
      @Composable
      fun NewsStory() {
          Text("A day in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")
      }

      </selection>
      """.trimIndent()
    )

    var action = myFixture.availableIntentions.find { it.text == "Create Preview" }
    assertThat(action).isNotNull()

    WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable {
      // Within unit tests ListPopupImpl.showInBestPositionFor doesn't open popup and acts like fist item was selected.
      // In our case wrap in Container will be selected.
      action!!.invoke(myFixture.project, myFixture.editor, myFixture.file)
    })

    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable
      import androidx.compose.ui.tooling.preview.Preview

      @Preview
      @Composable
      fun NewsStory() {
          Text("A day in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")
      }


    """.trimIndent()
    )


    myFixture.loadNewFile(
      "src/com/example/Test2.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable
      <caret>

      <selection>
      @Composable
      fun NewsStory2() {
          Text("A day in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")
      }

      </selection>
      """.trimIndent()
    )

    action = myFixture.availableIntentions.find { it.text == "Create Preview" }
    assertThat(action).isNotNull()

    WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable {
      // Within unit tests ListPopupImpl.showInBestPositionFor doesn't open popup and acts like fist item was selected.
      // In our case wrap in Container will be selected.
      action!!.invoke(myFixture.project, myFixture.editor, myFixture.file)
    })

    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable
      import androidx.compose.ui.tooling.preview.Preview


      @Preview
      @Composable
      fun NewsStory2() {
          Text("A day in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")
      }


    """.trimIndent()
    )
  }
}
