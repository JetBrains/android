/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.compose.analysis.setUpCompilerArgumentsForComposeCompilerPlugin
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import junit.framework.TestCase.fail
import org.jetbrains.android.compose.stubComposableAnnotation
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AddComposableAnnotationQuickFixTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val myFixture by lazy { projectRule.fixture }
  private val myProject by lazy { projectRule.project }

  @Before
  fun setUp() {
    myFixture.stubComposableAnnotation()
    setUpCompilerArgumentsForComposeCompilerPlugin(myProject)
  }

  @Test
  fun simpleMissingComposable_invokeOnFunctionDefinition() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun ComposableFunction() {}

      fun NonComposable<caret>Function() {
          ComposableFunction()
      }
      """
        .trimIndent()
    )

    invokeQuickFix()

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun ComposableFunction() {}

      @Composable
      fun NonComposableFunction() {
          ComposableFunction()
      }
      """
        .trimIndent()
    )
  }

  @Test
  fun simpleMissingComposable_invokeOnFunctionCall() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun ComposableFunction() {}

      fun NonComposableFunction() {
          Composable<caret>Function()
      }
      """
        .trimIndent()
    )

    invokeQuickFix()

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun ComposableFunction() {}

      @Composable
      fun NonComposableFunction() {
          ComposableFunction()
      }
      """
        .trimIndent()
    )
  }

  @Test
  fun missingComposableWithoutImport() {
    myFixture.addFileToProject(
      "src/com/example/ComposableFunction.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun ComposableFunction() {}
      """
        .trimIndent()
    )

    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      fun NonComposable<caret>Function() {
          ComposableFunction()
      }
      """
        .trimIndent()
    )

    invokeQuickFix()

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun NonComposableFunction() {
          ComposableFunction()
      }
      """
        .trimIndent()
    )
  }

  @Test
  fun errorInsideInlineLambda() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      // Redefine a version of `let` since the real one isn't defined in the test context.
      inline fun <T, R> T.myLet(block: (T) -> R): R = block(this)

      @Composable
      fun ComposableFunction() {}

      fun NonComposableFunction() {
          val foo = 1
          foo.myLet {
              ComposableFunction()  // invocation
          }
      }
      """
        .trimIndent()
    )

    assertQuickFixNotAvailable("Composable|Function()  // invocation")

    ApplicationManager.getApplication().invokeAndWait {
      myFixture.moveCaret("fun NonComposable|Function")
    }
    invokeQuickFix()

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.runtime.Composable

      // Redefine a version of `let` since the real one isn't defined in the test context.
      inline fun <T, R> T.myLet(block: (T) -> R): R = block(this)

      @Composable
      fun ComposableFunction() {}

      @Composable
      fun NonComposableFunction() {
          val foo = 1
          foo.myLet {
              ComposableFunction()  // invocation
          }
      }
      """
        .trimIndent()
    )
  }

  @Test
  fun errorInsideNonComposableLambda() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun ComposableFunction() {}

      fun functionThatTakesALambda(content: () -> Unit) {}

      fun NonComposableFunction() {
          functionThatTakesALambda {
              ComposableFunction()  // invocation
          }
      }
      """
        .trimIndent()
    )

    // Adding @Composable to `NonComposableFunction` isn't correct here. To fix the build error,
    // @Composable should be added to the
    // `content` parameter of `functionThatTakesALambda`. That's currently out of scope for this
    // quick fix (although could be added in the
    // future), so for now we just assert that the quick fix isn't available.
    assertQuickFixNotAvailable("fun NonComposable|Function() {")
    assertQuickFixNotAvailable("functionThatTake|sALambda {")
    assertQuickFixNotAvailable("Composable|Function()  // invocation")
  }

  @Test
  fun errorInClassInit() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun ComposableFunction() {}

      fun getMyClass(): Any {
          class MyClass {
              init {
                  ComposableFunction()  // invocation
              }
          }
          return MyClass()
      }
      """
        .trimIndent()
    )

    assertQuickFixNotAvailable("fun getMy|Class(): Any")
    assertQuickFixNotAvailable("Composable|Function()  // invocation")
  }

  @Test
  fun errorInPropertyGetter_invokeOnFunctionCall() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun ComposableFunction() {}

      fun getMyClass(): Any {
          class MyClass {
              val property: String
                  get() {
                      ComposableFunction()  // invocation
                      return ""
                  }
          }
          return MyClass()
      }
      """
        .trimIndent()
    )

    // The compiler reports this error on the property, even though it's the getter function that
    // needs to have @Composable added.
    // Furthermore, it does the same when the setter function calls a @Composable function, but that
    // scenario can't be handled because
    // @Composable doesn't apply to setters. It's not trivial to determine from the compiler error
    // on the property which accessor it should
    // apply to, so we just don't show the fix. There will be a separate error on the getter() in
    // this case that shows the fix anyway, which
    // suffices for the user to be able to quickly fix the error.
    assertQuickFixNotAvailable("val pro|perty: String")

    ApplicationManager.getApplication().invokeAndWait {
      myFixture.moveCaret("Composable|Function()  // invocation")
    }
    invokeQuickFix("property.get()")

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun ComposableFunction() {}

      fun getMyClass(): Any {
          class MyClass {
              val property: String
                  @Composable
                  get() {
                      ComposableFunction()  // invocation
                      return ""
                  }
          }
          return MyClass()
      }
      """
        .trimIndent()
    )
  }

  @Test
  fun errorInPropertySetter_noFixes() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun ComposableFunction() {}

      fun getMyClass(): Any {
          class MyClass {
              val property: String
                  get() {
                      return ""
                  }
                  set(value) {
                      Composable<caret>Function()  // invocation
                  }
          }
          return MyClass()
      }
      """
        .trimIndent()
    )

    // @Composable is not allowed on setters, so we shouldn't suggest adding it.
    assertQuickFixNotAvailable("fun getMy|Class(): Any")
    assertQuickFixNotAvailable("val prop|erty")
    assertQuickFixNotAvailable("se|t(value)")
    assertQuickFixNotAvailable("Composable|Function()  // invocation")
  }

  @Test
  fun errorInPropertyInitializer() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun ComposableFunction() {}

      fun getMyClass(): Any {
          class MyClass {
              val property = {
                  ComposableFunction()  // invocation
                  ""
              }
          }
          return MyClass()
      }
      """
        .trimIndent()
    )

    assertQuickFixNotAvailable("fun getMy|Class(): Any")
    assertQuickFixNotAvailable("val prop|erty = {")
    assertQuickFixNotAvailable("Composable|Function()  // invocation")
  }

  private fun invokeQuickFix(expectedFunctionName: String? = null) {
    val fixFilter: (IntentionAction) -> Boolean =
      if (expectedFunctionName != null) {
        { action -> action.text == "Add '@Composable' to function '$expectedFunctionName'" }
      } else {
        { action -> action.text.startsWith("Add '@Composable' to function '") }
      }

    val action = myFixture.availableIntentions.singleOrNull(fixFilter)
    if (action == null) {
      val intentionTexts =
        myFixture.availableIntentions.joinToString(transform = IntentionAction::getText)
      fail("Could not find expected quick fix. Available intentions: $intentionTexts")
    } else {
      WriteCommandAction.runWriteCommandAction(myProject) {
        action.invoke(myProject, myFixture.editor, myFixture.file)
      }
    }
  }

  private fun assertQuickFixNotAvailable(window: String) {
    ApplicationManager.getApplication().invokeAndWait { myFixture.moveCaret(window) }
    assertThat(
        myFixture.availableIntentions.filter {
          it.text.startsWith("Add '@Composable' to function '")
        }
      )
      .isEmpty()
  }
}
