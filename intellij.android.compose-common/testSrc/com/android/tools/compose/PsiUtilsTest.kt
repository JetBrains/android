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
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunctionLiteral
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

  private val fixture by lazy { projectRule.fixture }

  @Before
  fun setUp() {
    (fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    fixture.stubComposableAnnotation()
  }

  @Test
  fun isComposableFunction_functionIsComposable() {
    fixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
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
      "Test.kt",
      // language=kotlin
      """
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
      "Test.kt",
      // language=kotlin
      """
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
  fun isDeprecated_functionIsDeprecated() {
    fixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
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
      "Test.kt",
      // language=kotlin
      """
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
      "Test.kt",
      // language=kotlin
      """
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
      "Test2.kt",
      // language=kotlin
      """
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
      "Test.kt",
      // language=kotlin
      """
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

    fixture.removeComposableAnnotation()

    runReadAction {
      val element: KtElement = fixture.getEnclosing("35")
      assertThat(element.composableScope()).isNull()
    }
  }

  @Test
  fun composableScope_lambdaArgument_positional() {
    fixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
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
      val scope = fixture.getEnclosing<KtElement>("35").composableScope()
      assertThat(scope).isNotNull()
      val function: KtLambdaExpression = fixture.getEnclosing("35")
      assertThat(scope).isEqualTo(function)
    }

    fixture.removeComposableAnnotation("|@Composable| (Int)")

    runReadAction {
      assertThat(fixture.getEnclosing<KtElement>("35").composableScope()).isNull()
    }
  }

  @Test
  fun composableScope_lambdaArgument_named() {
    fixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      fun repeat(times: Int, action: @Composable (Int) -> Unit) {
        for (index in 0 until times) {
          action(index)
        }
      }
      @Composable
      fun Greeting() {
        repeat(2, action = { val a = 35 })
      }
      """
        .trimIndent()
    )

    runReadAction {
      val scope = fixture.getEnclosing<KtElement>("35").composableScope()
      assertThat(scope).isNotNull()
      val function: KtLambdaExpression = fixture.getEnclosing("35")
      assertThat(scope).isEqualTo(function)
    }

    fixture.removeComposableAnnotation("|@Composable| (Int)")

    runReadAction {
      assertThat(fixture.getEnclosing<KtElement>("35").composableScope()).isNull()
    }
  }

  // This specifically tests for the issue in b/313902116 in which we threw NoSuchElementException
  // when the parameter was misnamed.
  @Test
  fun composableScope_lambdaArgument_misnamed() {
    fixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      fun repeat(times: Int, action: @Composable (Int) -> Unit) {
        for (index in 0 until times) {
          action(index)
        }
      }
      @Composable
      fun Greeting() {
        repeat(2, notNamedAction = { val a = 35 })
      }
      """
        .trimIndent()
    )

    runReadAction {
      assertThat(fixture.getEnclosing<KtElement>("35").composableScope()).isNull()
    }
  }


  @Test
  fun composableScope_lambdaArgument_anonymousFunction() {
    fixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      val repeat = fun (times: Int, action: @Composable (Int) -> Unit) {
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

    fixture.removeComposableAnnotation("|@Composable| (Int)")

    runReadAction {
      val element: KtElement = fixture.getEnclosing("35")
      assertThat(element.composableScope()).isNull()
    }
  }

  @Test
  fun composableScope_inlineLambdaArgument() {
    fixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
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

    fixture.removeComposableAnnotation()

    runReadAction {
      val element: KtElement = fixture.getEnclosing("35")
      assertThat(element.composableScope()).isNull()
    }
  }

  @Test
  fun composableScope_inlineLambdaArgument_noinline() {
    fixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
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

    fixture.removeComposableAnnotation("|@Composable| (Int)")

    runReadAction {
      val element: KtElement = fixture.getEnclosing("35")
      assertThat(element.composableScope()).isNull()
    }
  }

  @Test
  fun composableScope_getter() {
    fixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
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

    fixture.removeComposableAnnotation()

    runReadAction {
      val element: KtElement = fixture.getEnclosing("35")
      assertThat(element.composableScope()).isNull()
    }
  }

  @Test
  fun composableScope_setter() {
    fixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
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
  fun composableScope_classInitializer() {
    fixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      @Composable
      fun foo() {
        class MyClass {
          init {
            val a = 35
          }
        }
      }
      """
        .trimIndent()
    )

    runReadAction {
      val element: KtElement = fixture.getEnclosing("35")
      val scope = element.composableScope()
      assertThat(scope).isNull() // Neither init nor MyClass is a valid scope
    }
  }

  @Test
  fun composableScope_functionLiteral() {
    fixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      val myGreatValue = @Composable {
        return 35
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

    fixture.removeComposableAnnotation()

    runReadAction {
      val element: KtElement = fixture.getEnclosing("35")
      assertThat(element.composableScope()).isNull()
    }
  }

  @Test
  fun expectedComposableAnnotationHolder_functionVariable_withType() {
    fixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
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
  fun expectedComposableAnnotationHolder_functionVariable_withoutType() {
    fixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
      fun Greeting() {
        val a = { 35 }
      }
      """
        .trimIndent()
    )

    runReadAction {
      val element: KtElement = fixture.getEnclosing("35")
      val scope = element.expectedComposableAnnotationHolder()
      assertThat(scope).isNotNull()
      val function: KtFunctionLiteral = fixture.getEnclosing("35")
      assertThat(scope).isEqualTo(function)
    }
  }

  @Test
  fun expectedComposableAnnotationHolder_anonymousFunctionVariable() {
    fixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
      fun Greeting() {
        val a = fun() { 35 }
      }
      """
        .trimIndent()
    )

    runReadAction {
      val element: KtElement = fixture.getEnclosing("35")
      val scope = element.expectedComposableAnnotationHolder()
      assertThat(scope).isNotNull()
      val function: KtNamedFunction = fixture.getEnclosing("35")
      assertThat(scope).isEqualTo(function)
      val outerFunction: KtNamedFunction = fixture.getEnclosing("val a")
      assertThat(scope).isNotEqualTo(outerFunction)
    }
  }

  @Test
  fun expectedComposableAnnotationHolder_function() {
    fixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
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
      "Test.kt",
      // language=kotlin
      """
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
      "Test.kt",
      // language=kotlin
      """
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
  fun expectedComposableAnnotationHolder_lambdaParameterType_positional() {
    fixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
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
  fun expectedComposableAnnotationHolder_lambdaParameterType_named() {
    fixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
      fun repeat(times: Int, action: (Int) -> Unit) {
        for (index in 0 until times) {
          action(index)
        }
      }
      fun Greeting() {
        repeat(2, action= { val a = 35 })
      }
      """
        .trimIndent()
    )

    runReadAction {
      val scope = fixture.getEnclosing<KtElement>("35").expectedComposableAnnotationHolder()
      assertThat(scope).isNotNull()
      val function: KtTypeReference = fixture.getEnclosing("(Int) -> Unit")
      assertThat(scope).isEqualTo(function)
    }
  }

  // This specifically tests for the issue in b/313902116 in which we threw NoSuchElementException
  // when the parameter was misnamed.
  @Test
  fun expectedComposableAnnotationHolder_lambdaParameterType_misnamed() {
    fixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
      fun repeat(times: Int, action: (Int) -> Unit) {
        for (index in 0 until times) {
          action(index)
        }
      }
      fun Greeting() {
        repeat(2, notNamedAction = { val a = 35 })
      }
      """
        .trimIndent()
    )

    runReadAction {
      assertThat(fixture.getEnclosing<KtElement>("35").expectedComposableAnnotationHolder()).isNull()
    }
  }

  @Test
  fun expectedComposableAnnotationHolder_lambdaParameterType_anonymousFunction() {
    fixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
      val repeat = fun(times: Int, action: (Int) -> Unit) {
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
      "Test.kt",
      // language=kotlin
      """
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
      "Test.kt",
      // language=kotlin
      """
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

  @Test
  fun expectedComposableAnnotationHolder_classInitializer() {
    fixture.loadNewFile(
      "MyClass.kt",
      // language=kotlin
      """
      class MyClass {
        init {
          val a = 35
        }
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

  private fun CodeInsightTestFixture.removeComposableAnnotation(window: String = "@Composable") {
    invokeAndWaitIfNeeded {
      runUndoTransparentWriteAction {
        val annotation: KtAnnotationEntry = getEnclosing(window)
        annotation.delete()
      }
    }
  }
}
