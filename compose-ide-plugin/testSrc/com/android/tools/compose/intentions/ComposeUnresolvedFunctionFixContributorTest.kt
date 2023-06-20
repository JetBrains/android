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

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.kotlin.idea.base.plugin.isK2Plugin
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ComposeUnresolvedFunctionFixContributorTest {

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  private lateinit var myFixture: CodeInsightTestFixture

  @Before
  fun setUp() {
    myFixture = projectRule.fixture
    myFixture.stubComposableAnnotation()
  }

  @Test
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

    // language=kotlin
    val expectedText = """
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
    """
    val expectedTextForFE10 = expectedText + '\n'

    // TODO(b/267429486): Revisit after implementing the K2 version of `CreateCallableFromUsageFix`.
    if (isK2Plugin()) myFixture.checkResult(expectedText.trimIndent())
    else myFixture.checkResult(expectedTextForFE10.trimIndent())
  }

  @Test
  fun testCreateComposableFunctionWithTypeAndValueParameters() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          <caret>UnresolvedFunction<Int>(45, f = { 43 }, "OK".length)
      }
      """.trimIndent()
    )

    val action = myFixture.availableIntentions.find { it.text == "Create @Composable function 'UnresolvedFunction'" }
    assertThat(action).isNotNull()

    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      action!!.invoke(myFixture.project, myFixture.editor, myFixture.file)
    }

    // TODO(b/267429486): Revisit after implementing the K2 version of `CreateCallableFromUsageFix`.
    if (isK2Plugin()) {
      myFixture.checkResult(
        // language=kotlin
        """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          UnresolvedFunction<Int>(45, f = { 43 }, "OK".length)
      }

      @Composable
      fun <T0> UnresolvedFunction(x0: Int, f: () -> Int, x2: Int) {
          TODO("Not yet implemented")
      }
    """.trimIndent()
      )
    } else {
      myFixture.checkResult(
        // language=kotlin
        """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          UnresolvedFunction<Int>(45, f = { 43 }, "OK".length)
      }

      @Composable
      fun <T> UnresolvedFunction(t: T, f: () -> T, length: T) {
          TODO("Not yet implemented")
      }

    """.trimIndent()
      )
    }
  }

  @Test
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

  @Test
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

  @Test
  fun testCreateComposableFunctionWithLambda() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          <caret>UnresolvedFunction {}
      }
      """.trimIndent()
    )

    val action = myFixture.availableIntentions.find { it.text == "Create @Composable function 'UnresolvedFunction'" }
    assertThat(action).isNotNull()

    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      action!!.invoke(myFixture.project, myFixture.editor, myFixture.file)
    }

    // TODO(b/267429486): Revisit after implementing the K2 version of `CreateCallableFromUsageFix`.
    if (isK2Plugin()) {
      myFixture.checkResult(
        // language=kotlin
        """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          UnresolvedFunction {}
      }

      @Composable
      fun UnresolvedFunction(x0: () -> Unit) {
          TODO("Not yet implemented")
      }
    """.trimIndent()
      )
    } else {
      myFixture.checkResult(
        // language=kotlin
        """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun NewsStory() {
          UnresolvedFunction {}
      }

      @Composable
      fun UnresolvedFunction(content: @Composable () -> Unit) {
          TODO("Not yet implemented")
      }

    """.trimIndent()
      )
    }
  }
}
