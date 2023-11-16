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
package com.android.tools.compose

import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.getEnclosing
import com.android.tools.idea.testing.loadNewFile
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTypeReference
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PsiUtilsTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val fixture: CodeInsightTestFixture by lazy { projectRule.fixture }

  @Before
  fun setUp() {
    (fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    fixture.stubComposableAnnotation()
  }

  @Test
  fun isComposableFunction_functionIsComposable() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun Greet${caret}ing() {}
      """
        .trimIndent()
    )

    runReadAction {
      assertThat(fixture.elementAtCaret.isComposableFunction()).isTrue()
      assertThat(fixture.elementAtCaret.getComposableAnnotation()).isNotNull()
    }
  }

  @Test
  fun isComposableFunction_functionIsNotComposable() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      fun Greet${caret}ing() {}
      """
        .trimIndent()
    )

    runReadAction {
      assertThat(fixture.elementAtCaret.isComposableFunction()).isFalse()
      assertThat(fixture.elementAtCaret.getComposableAnnotation()).isNull()
    }
  }

  @Test
  fun isComposableFunction_elementIsNotFunction() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      val greet${caret}ing = ""
      """
        .trimIndent()
    )

    runReadAction {
      assertThat(fixture.elementAtCaret.isComposableFunction()).isFalse()
      assertThat(fixture.elementAtCaret.getComposableAnnotation()).isNull()
    }
  }

  @Test
  fun isDeprecated_funnctionIsDeprecated() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      @Deprecated
      fun Greet${caret}ing() {}
      """
        .trimIndent()
    )

    runReadAction { assertThat(fixture.elementAtCaret.isDeprecated()).isTrue() }
  }

  @Test
  fun isDeprecated_functionIsNotDeprecated() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      fun Greet${caret}ing() {}
      """
        .trimIndent()
    )

    runReadAction { assertThat(fixture.elementAtCaret.isDeprecated()).isFalse() }
  }

  @Test
  fun isDeprecated_elementCannotBeAnnotated() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.exa${caret}mple

      fun Greeting() {}
      """
        .trimIndent()
    )

    runReadAction { assertThat(fixture.elementAtCaret.isDeprecated()).isFalse() }
  }

  @Test
  fun isDeprecatedAndIsComposableFunctionCaching() {
    // These utility methods both utilize caching internally, and if they use the incorrect method
    // (with a default key), their cached values
    // can collide. This test validates that a single method with different values for each is still
    // correctly returned.
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      @Deprecated
      fun Gree${caret}ting() {}
      """
        .trimIndent()
    )

    runReadAction {
      assertThat(fixture.elementAtCaret.isComposableFunction()).isFalse()
      assertThat(fixture.elementAtCaret.getComposableAnnotation()).isNull()
      assertThat(fixture.elementAtCaret.isDeprecated()).isTrue()
    }

    fixture.loadNewFile(
      "src/com/example/Test2.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun Gree${caret}ting() {}
      """
        .trimIndent()
    )

    runReadAction {
      assertThat(fixture.elementAtCaret.isComposableFunction()).isTrue()
      assertThat(fixture.elementAtCaret.getComposableAnnotation()).isNotNull()
      assertThat(fixture.elementAtCaret.isDeprecated()).isFalse()
    }
  }

  @Test
  fun composableScope_function() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun Greeting() {
        val a = 35
      }
      """
        .trimIndent()
    )

    runReadAction {
      val element: KtElement = fixture.getEnclosing("35")
      val scope = element.composableScope()
      assertThat(scope).isNotNull()
      val function: KtNamedFunction = fixture.getEnclosing("Greeting")
      assertThat(scope).isEqualTo(function)
    }
  }

  @Test
  fun composableScope_lambda() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      fun repeat(times: Int, action: @Composable (Int) -> Unit) {
        for (index in 0 until times) {
          action(index)
        }
      }

      @Composable
      fun Greeting() {
        repeat(2) { val a = 35 }
      }
      """
        .trimIndent()
    )

    runReadAction {
      val element: KtElement = fixture.getEnclosing("35")
      val scope = element.composableScope()
      assertThat(scope).isNotNull()
      val function: KtLambdaExpression = fixture.getEnclosing("35")
      assertThat(scope).isEqualTo(function)
    }
  }

  @Test
  fun composableScope_inlineLambda() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      inline fun repeat(times: Int, action: (Int) -> Unit) {
        for (index in 0 until times) {
          action(index)
        }
      }

      @Composable
      fun Greeting() {
        repeat(2) { val a = 35 }
      }
      """
        .trimIndent()
    )

    runReadAction {
      val element: KtElement = fixture.getEnclosing("35")
      val scope = element.composableScope()
      assertThat(scope).isNotNull()
      val function: KtNamedFunction = fixture.getEnclosing("Greeting")
      assertThat(scope).isEqualTo(function)
    }
  }

  @Test
  fun composableScope_inlineLambda_noinline() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      inline fun repeat(times: Int, noinline action: @Composable (Int) -> Unit) {
        for (index in 0 until times) {
          action(index)
        }
      }

      @Composable
      fun Greeting() {
        repeat(2) { val a = 35 }
      }
      """
        .trimIndent()
    )

    runReadAction {
      val element: KtElement = fixture.getEnclosing("35")
      val scope = element.composableScope()
      assertThat(scope).isNotNull()
      val function: KtLambdaExpression = fixture.getEnclosing("35")
      assertThat(scope).isEqualTo(function)
    }
  }

  @Test
  fun composableScope_getter() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      val myGreatValue: Int
        @Composable get() {
          return 35
        }
      """
        .trimIndent()
    )

    runReadAction {
      val element: KtElement = fixture.getEnclosing("35")
      val scope = element.composableScope()
      assertThat(scope).isNotNull()
      val function: KtPropertyAccessor = fixture.getEnclosing("35")
      assertThat(scope).isEqualTo(function)
    }
  }

  @Test
  fun composableScope_setter() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      var myGreatValue: Int = 2
        @Composable set(newValue) {
          field = newValue + 2
        }
      """
        .trimIndent()
    )

    runReadAction {
      val element: KtElement = fixture.getEnclosing("field =")
      val scope = element.composableScope()
      assertThat(scope).isNull() // Setter is not a valid scope
    }
  }

  @Test
  fun expectedComposableAnnotationHolder_variable() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      fun Greeting() {
        val a: () -> Int = { 35 }
      }
      """
        .trimIndent()
    )

    runReadAction {
      val element: KtElement = fixture.getEnclosing("35")
      val scope = element.expectedComposableAnnotationHolder()
      assertThat(scope).isNotNull()
      val function: KtTypeReference = fixture.getEnclosing("() -> Int")
      assertThat(scope).isEqualTo(function)
    }
  }

  @Test
  fun expectedComposableAnnotationHolder_function() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      fun Greeting() { val a = 35 }
      """
        .trimIndent()
    )

    runReadAction {
      val element: KtElement = fixture.getEnclosing("35")
      val scope = element.expectedComposableAnnotationHolder()
      assertThat(scope).isNotNull()
      val function: KtNamedFunction = fixture.getEnclosing("Greeting")
      assertThat(scope).isEqualTo(function)
    }
  }

  @Test
  fun expectedComposableAnnotationHolder_inlineLambda() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      inline fun repeat(times: Int, action: (Int) -> Unit) {
        for (index in 0 until times) {
          action(index)
        }
      }

      fun Greeting() {
        repeat(2) {  val a = 35 }
      }
      """
        .trimIndent()
    )

    runReadAction {
      val element: KtElement = fixture.getEnclosing("35")
      val scope = element.expectedComposableAnnotationHolder()
      assertThat(scope).isNotNull()
      val function: KtNamedFunction = fixture.getEnclosing("Greeting")
      assertThat(scope).isEqualTo(function)
    }
  }

  @Test
  fun expectedComposableAnnotationHolder_inlineLambda_noinline() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      inline fun repeat(times: Int, noinline action: (Int) -> Unit) {
        for (index in 0 until times) {
          action(index)
        }
      }

      fun Greeting() {
        repeat(2) { val a = 35 }
      }
      """
        .trimIndent()
    )

    runReadAction {
      val element: KtElement = fixture.getEnclosing("35")
      val scope = element.expectedComposableAnnotationHolder()
      assertThat(scope).isNotNull()
      val function: KtTypeReference = fixture.getEnclosing("(Int) -> Unit")
      assertThat(scope).isEqualTo(function)
    }
  }

  @Test
  fun expectedComposableAnnotationHolder_lambda() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      fun repeat(times: Int, action: (Int) -> Unit) {
        for (index in 0 until times) {
          action(index)
        }
      }

      fun Greeting() {
        repeat(2) {
          val a = 35
        }
      }
      """
        .trimIndent()
    )

    runReadAction {
      val element: KtElement = fixture.getEnclosing("35")
      val scope = element.expectedComposableAnnotationHolder()
      assertThat(scope).isNotNull()
      val function: KtTypeReference = fixture.getEnclosing("(Int) -> Unit")
      assertThat(scope).isEqualTo(function)
    }
  }

  @Test
  fun expectedComposableAnnotationHolder_getter() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      val myGreatValue: Int
        get() {
          val a = 35
        }
      """
        .trimIndent()
    )

    runReadAction {
      val element: KtElement = fixture.getEnclosing("35")
      val scope = element.expectedComposableAnnotationHolder()
      assertThat(scope).isNotNull()
      val function: KtPropertyAccessor = fixture.getEnclosing("35")
      assertThat(scope).isEqualTo(function)
    }
  }

  @Test
  fun expectedComposableAnnotationHolder_setter() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      var myGreatValue: Int = 23
        set(newValue) {
          field = newValue + 35
        }
      """
        .trimIndent()
    )

    runReadAction {
      val element: KtElement = fixture.getEnclosing("35")
      val scope = element.expectedComposableAnnotationHolder()
      assertThat(scope).isNull()
    }
  }
}
