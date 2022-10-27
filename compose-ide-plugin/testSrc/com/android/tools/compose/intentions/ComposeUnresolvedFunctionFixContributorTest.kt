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


class ComposeUnresolvedFunctionFixContributorTest : JavaCodeInsightFixtureTestCase() {
  public override fun setUp() {
    super.setUp()
    myFixture.stubComposableAnnotation()
  }

  fun testCreateComposableFunction() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          <caret>UnresolvedFunction()
      }
      """.trimIndent()
    )

    val action = myFixture.availableIntentions.find { it.text == "Create @Composable function 'UnresolvedFunction'" }
    assertThat(action).isNotNull()

    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      action!!.invoke(myFixture.project, myFixture.editor, myFixture.file)
    }

    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          UnresolvedFunction()
      }

      @Composable
      fun UnresolvedFunction() {
          TODO("Not yet implemented")
      }

    """.trimIndent()
    )
  }

  fun `test don't create Composable function if return type is not Unit`() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          val k:Int = <caret>unresolvedFunction()
      }
      """.trimIndent()
    )

    val action = myFixture.availableIntentions.find { it.text == "Create @Composable function 'unresolvedFunction'" }
    assertThat(action).isNull()
  }

  fun `test don't create Composable function if unresolved function starts with lowercase letter`() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          <caret>unresolvedFunction()
      }
      """.trimIndent()
    )

    val action = myFixture.availableIntentions.find { it.text == "Create @Composable function 'unresolvedFunction'" }
    assertThat(action).isNull()
  }
}