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


import com.google.common.truth.Truth
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.android.compose.stubComposableAnnotation

/**
 * Test for [ComposeUnwrapAction].
 */
internal class ComposeUnwrapActionTest : JavaCodeInsightFixtureTestCase() {

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

  fun testRemoveColumn() {
    myFixture.configureByText(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable
      import androidx.compose.foundation.layout.Column

      @Composable
      fun NewsStory() {
          Colum<caret>n {
              Text("A day in Shark Fin Cove")
              Text("Davenport, California")
              Text("December 2018")
          }
      }
    """.trimIndent()
    )

    val action = myFixture.availableIntentions.find { it.text == "Remove wrapper" }
    Truth.assertThat(action).isNotNull()

    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      action!!.invoke(myFixture.project, myFixture.editor, myFixture.file)
    }

    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable
      import androidx.compose.foundation.layout.Column

      @Composable
      fun NewsStory() {
          Text("A day in Shark Fin Cove")
          Text("Davenport, California")
          Text("December 2018")
      }
      """.trimIndent()
    )
  }
}