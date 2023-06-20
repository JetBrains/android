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
package com.android.tools.compose.code

import com.android.tools.compose.COMPOSABLE_FQ_NAMES_ROOT
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.loadNewFile
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.kotlin.psi.KtDeclaration
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ComposableFunctionRenderingTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val myFixture: CodeInsightTestFixture by lazy { projectRule.fixture }

  @Before
  fun setUp() {
    (myFixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    myFixture.stubComposableAnnotation(COMPOSABLE_FQ_NAMES_ROOT)
  }

  @Test
  fun noParameters() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun ${caret}HomeScreen() {}
      """.trimIndent()
    )

    with(getComposableFunctionRenderPartsAtCaret()) {
      assertThat(parameters).isEqualTo("()")
      assertThat(tail).isNull()
    }
  }

  @Test
  fun onlyOptionalParameters() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun ${caret}HomeScreen(foo: Int = 0, bar: String = "") {}
      """.trimIndent()
    )

    with(getComposableFunctionRenderPartsAtCaret()) {
      assertThat(parameters).isEqualTo("(...)")
      assertThat(tail).isNull()
    }
  }

  @Test
  fun onlyRequiredParameters() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun ${caret}HomeScreen(foo: Int, bar: String) {}
      """.trimIndent()
    )

    with(getComposableFunctionRenderPartsAtCaret()) {
      assertThat(parameters).isEqualTo("(foo: Int, bar: String)")
      assertThat(tail).isNull()
    }
  }

  @Test
  fun requiredAndOptionalParameters() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun ${caret}HomeScreen(foo: Int, bar: String = "", baz: Int = "") {}
      """.trimIndent()
    )

    with(getComposableFunctionRenderPartsAtCaret()) {
      assertThat(parameters).isEqualTo("(foo: Int, ...)")
      assertThat(tail).isNull()
    }
  }

  @Test
  fun onlyComposableLambda() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun ${caret}HomeScreen(foo: @Composable () -> Unit) {}
      """.trimIndent()
    )

    with(getComposableFunctionRenderPartsAtCaret()) {
      assertThat(parameters).isNull()
      assertThat(tail).isEqualTo("{...}")
    }
  }

  @Test
  fun onlyNonComposableLambda() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun ${caret}HomeScreen(foo: () -> Unit) {}
      """.trimIndent()
    )

    with(getComposableFunctionRenderPartsAtCaret()) {
      assertThat(parameters).isEqualTo("(foo: () -> Unit)")
      assertThat(tail).isNull()
    }
  }

  @Test
  fun composableLambdaWithParameters() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun ${caret}HomeScreen(a: Int, b: Int = 0, foo: @Composable () -> Unit) {}
      """.trimIndent()
    )

    with(getComposableFunctionRenderPartsAtCaret()) {
      assertThat(parameters).isEqualTo("(a: Int, ...)")
      assertThat(tail).isEqualTo("{...}")
    }
  }

  @Test
  fun nonComposableLambdaWithParameters() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun ${caret}HomeScreen(a: Int, b: Int = 0, foo: () -> Unit) {}
      """.trimIndent()
    )

    // This seems like odd behavior, but it's documenting the existing behavior at the time this test is being written.
    with(getComposableFunctionRenderPartsAtCaret()) {
      assertThat(parameters).isEqualTo("(a: Int, foo: () -> Unit, ...)")
      assertThat(tail).isNull()
    }
  }

  private fun getComposableFunctionRenderPartsAtCaret() = runReadAction {
    val element = myFixture.elementAtCaret as KtDeclaration
    element.getComposableFunctionRenderParts() ?: throw AssertionError("Test must contain a valid composable function")
  }
}
