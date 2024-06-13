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

import com.android.tools.compose.ComposeBundle
import com.android.tools.compose.analysis.setUpCompilerArgumentsForComposeCompilerPlugin
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runUndoTransparentWriteAction
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
  fun simpleMissingComposable_readOnlyFile() {
    myFixture
      .addFileToProject(
        "MyFunctionWithLambda.kt",
        // language=kotlin
        """
       fun MyFunctionWithLambda(content: () -> Unit) {
         content()
       }
       """
          .trimIndent(),
      )
      .also {
        invokeAndWaitIfNeeded {
          runUndoTransparentWriteAction { it.virtualFile.isWritable = false }
        }
      }

    myFixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
       import androidx.compose.runtime.Composable
       @Composable
       fun ComposableFunction() {}
       fun NonComposableFunction() {
         MyFunctionWithLambda {
           ComposableFunction()  // invocation
         }
       }
       """
        .trimIndent(),
    )

    assertQuickFixNotAvailable("Composable|Function()  // invocation")
  }

  @Test
  fun simpleMissingComposable_invokeOnFunctionDefinition() {
    myFixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      @Composable
      fun ComposableFunction() {}
      fun NonComposableFunction() {
          ComposableFunction()
      }
      """
        .trimIndent(),
    )

    invokeQuickFix("NonComposable|Function")

    myFixture.checkResult(
      // language=kotlin
      """
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
      "Test.kt",
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      @Composable
      fun ComposableFunction() {}
      fun NonComposableFunction() {
          ComposableFunction()  // invocation
      }
      """
        .trimIndent(),
    )

    invokeQuickFix("Composable|Function()  // invocation")

    myFixture.checkResult(
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      @Composable
      fun ComposableFunction() {}
      @Composable
      fun NonComposableFunction() {
          ComposableFunction()  // invocation
      }
      """
        .trimIndent()
    )
  }

  @Test
  fun missingComposableWithoutImport() {
    myFixture.addFileToProject(
      "ComposableFunction.kt",
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      @Composable
      fun ComposableFunction() {}
      """
        .trimIndent(),
    )

    myFixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
      fun NonComposableFunction() {
          ComposableFunction()
      }
      """
        .trimIndent(),
    )

    invokeQuickFix("NonComposable|Function")

    myFixture.checkResult(
      // language=kotlin
      """
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
  fun errorInsideInlineLambda_invokeOnFunction() {
    myFixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
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
        .trimIndent(),
    )

    invokeQuickFix("NonComposable|Function", "NonComposableFunction")

    myFixture.checkResult(
      // language=kotlin
      """
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
  fun errorInsideInlineLambda_invokeOnFunctionCall() {
    myFixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
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
        .trimIndent(),
    )

    invokeQuickFix("Composable|Function()  // invocation")

    myFixture.checkResult(
      // language=kotlin
      """
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
      "Test.kt",
      // language=kotlin
      """
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
        .trimIndent(),
    )

    // Currently only showing a fix on the invocation.
    // TODO(b/309364913) compiler shouldn't mark NonComposableFunction with COMPOSABLE_EXPECTED
    // assertQuickFixNotAvailable("fun NonComposable|Function() {")
    assertQuickFixNotAvailable("functionThatTake|sALambda {")
    assertQuickFixNotAvailable("cont|ent")
    assertQuickFixNotAvailable("() -|> Unit")

    invokeQuickFix("Composable|Function()  // invocation")

    myFixture.checkResult(
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      @Composable
      fun ComposableFunction() {}
      fun functionThatTakesALambda(content: @Composable () -> Unit) {}
      fun NonComposableFunction() {
          functionThatTakesALambda {
              ComposableFunction()  // invocation
          }
      }
      """
        .trimIndent()
    )
  }

  @Test
  fun errorInsideNonComposableLambda_paramOfAnonymousFunction() {
    myFixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      @Composable
      fun ComposableFunction() {}
      val functionTypedValThatTakesALambda = fun (content: () -> Unit) {}
      fun NonComposableFunction() {
          functionTypedValThatTakesALambda {
              ComposableFunction()  // invocation
          }
      }
      """
        .trimIndent(),
    )

    // Currently only showing a fix on the invocation.
    // TODO(b/309364913) compiler shouldn't mark NonComposableFunction with COMPOSABLE_EXPECTED
    // assertQuickFixNotAvailable("fun NonComposable|Function() {")
    assertQuickFixNotAvailable("functionTypedValThatTake|sALambda {")
    assertQuickFixNotAvailable("cont|ent")
    assertQuickFixNotAvailable("() -|> Unit")

    invokeQuickFix("Composable|Function()  // invocation")

    myFixture.checkResult(
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      @Composable
      fun ComposableFunction() {}
      val functionTypedValThatTakesALambda = fun (content: @Composable () -> Unit) {}
      fun NonComposableFunction() {
          functionTypedValThatTakesALambda {
              ComposableFunction()  // invocation
          }
      }
      """
        .trimIndent()
    )
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
        .trimIndent(),
    )

    assertQuickFixNotAvailable("fun getMy|Class(): Any")
    assertQuickFixNotAvailable("class My|Class")
    assertQuickFixNotAvailable("in|it")
    assertQuickFixNotAvailable("Composable|Function()  // invocation")
  }

  @Test
  fun errorInPropertyGetter_noSetter_invokeOnFunctionCall() {
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
        .trimIndent(),
    )

    invokeQuickFix("prop|erty", "property.get()")

    myFixture.checkResult(
      // language=kotlin
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
  fun errorInPropertyGetter_withSetter_invokeOnFunctionCall() {
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
              var property: String = "foo"
                  get() {
                      ComposableFunction()  // invocation
                      return ""
                  }
                  set(newValue) {
                    field = newValue + "bar"
                  }
          }
          return MyClass()
      }
      """
        .trimIndent(),
    )

    // The compiler reports this error on the property, even though it's the getter function that
    // needs to have @Composable added. Furthermore, it does the same when the setter function calls
    // a @Composable function, but that scenario can't be handled because @Composable doesn't apply
    // to setters. If the property has both a getter and a setter, it's not trivial to determine
    // from the compiler error on the property which accessor it should apply to, so we just don't
    // show the fix. There will be a separate error on the getter() in this case that shows the fix
    // anyway, which suffices for the user to be able to quickly fix the error.
    assertQuickFixNotAvailable("var pro|perty: String")

    invokeQuickFix("Composable|Function()  // invocation", "property.get()")

    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun ComposableFunction() {}

      fun getMyClass(): Any {
          class MyClass {
              var property: String = "foo"
                  @Composable
                  get() {
                      ComposableFunction()  // invocation
                      return ""
                  }
                  set(newValue) {
                    field = newValue + "bar"
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
      @Composable fun ComposableFunction() {}
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
        .trimIndent(),
    )

    // @Composable is not allowed on setters, so we shouldn't suggest adding it.
    assertQuickFixNotAvailable("fun getMy|Class(): Any")
    assertQuickFixNotAvailable("val prop|erty")
    assertQuickFixNotAvailable("se|t(value)")
    assertQuickFixNotAvailable("Composable|Function()  // invocation")
  }

  @Test
  fun errorInPropertyInitializer_invokeOnFunctionCall() {
    myFixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      @Composable fun ComposableFunction() {}
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
        .trimIndent(),
    )

    assertQuickFixNotAvailable("fun getMy|Class(): Any")
    assertQuickFixNotAvailable("val prop|erty")

    invokeQuickFix("Composable|Function()  // invocation")

    // For some reason when we add the annotation to a function literal, it does so without
    // shortening the annotation. This isn't a huge deal as this is a _very_ cornery case, and it's
    // still technically correct code. See https://youtrack.jetbrains.com/issue/KTIJ-27854
    myFixture.checkResult(
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      @Composable fun ComposableFunction() {}
      fun getMyClass(): Any {
          class MyClass {
              val property = @androidx.compose.runtime.Composable {
                  ComposableFunction()  // invocation
                  ""
              }
          }
          return MyClass()
      }
      """
        .trimIndent()
    )
  }

  @Test
  fun errorInPropertyInitializerWithAnonymousFunction_invokeOnFunctionCall() {
    myFixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      @Composable fun ComposableFunction() {}
      fun getMyClass(): Any {
          class MyClass {
              val property = fun() {
                  ComposableFunction()  // invocation
              }
          }
          return MyClass()
      }
      """
        .trimIndent(),
    )

    assertQuickFixNotAvailable("fun getMy|Class(): Any")

    invokeQuickFix("Composable|Function()  // invocation")

    myFixture.checkResult(
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      @Composable fun ComposableFunction() {}
      fun getMyClass(): Any {
          class MyClass {
              val property = @Composable
              fun() {
                  ComposableFunction()  // invocation
              }
          }
          return MyClass()
      }
      """
        .trimIndent()
    )
  }

  @Test
  fun errorInPropertyInitializerWithAnonymousFunction_invokeOnFunction() {
    myFixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      @Composable fun ComposableFunction() {}
      fun getMyClass(): Any {
          class MyClass {
              val property = fun() {
                  ComposableFunction()  // invocation
              }
          }
          return MyClass()
      }
      """
        .trimIndent(),
    )

    assertQuickFixNotAvailable("fun getMy|Class(): Any")

    invokeQuickFix("fun|()")

    myFixture.checkResult(
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      @Composable fun ComposableFunction() {}
      fun getMyClass(): Any {
          class MyClass {
              val property = @Composable
              fun() {
                  ComposableFunction()  // invocation
              }
          }
          return MyClass()
      }
      """
        .trimIndent()
    )
  }

  @Test
  fun errorInPropertyInitializerWithAnonymousFunction_invokeOnProperty() {
    myFixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      @Composable fun ComposableFunction() {}
      fun getMyClass(): Any {
          class MyClass {
              val property = fun() {
                  ComposableFunction()  // invocation
              }
          }
          return MyClass()
      }
      """
        .trimIndent(),
    )

    assertQuickFixNotAvailable("fun getMy|Class(): Any")
    invokeQuickFix("prop|erty")

    myFixture.checkResult(
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      @Composable fun ComposableFunction() {}
      fun getMyClass(): Any {
          class MyClass {
              val property = @Composable
              fun() {
                  ComposableFunction()  // invocation
              }
          }
          return MyClass()
      }
      """
        .trimIndent()
    )
  }

  @Test
  fun errorInPropertyInitializerWithType_invokeOnFunctionCall() {
    myFixture.loadNewFile(
      "Test.kt",
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      @Composable fun ComposableFunction() {}
      fun getMyClass(): Any {
          class MyClass {
              val property: () -> String = {
                  ComposableFunction()  // invocation
                  ""
              }
          }
          return MyClass()
      }
      """
        .trimIndent(),
    )

    assertQuickFixNotAvailable("fun getMy|Class(): Any")
    assertQuickFixNotAvailable("val prop|erty")

    invokeQuickFix("Composable|Function()  // invocation")

    myFixture.checkResult(
      // language=kotlin
      """
      import androidx.compose.runtime.Composable
      @Composable fun ComposableFunction() {}
      fun getMyClass(): Any {
          class MyClass {
              val property: @Composable () -> String = {
                  ComposableFunction()  // invocation
                  ""
              }
          }
          return MyClass()
      }
      """
        .trimIndent()
    )
  }

  private fun invokeQuickFix(window: String, expectedFunctionName: String? = null) {
    ApplicationManager.getApplication().invokeAndWait { myFixture.moveCaret(window) }
    val fixFilter: (IntentionAction) -> Boolean =
      if (expectedFunctionName != null) {
        { action ->
          action.text ==
            ComposeBundle.message("add.composable.to.element.with.name", expectedFunctionName)
        }
      } else {
        { action -> action.text.startsWith("Add '@Composable' to ") }
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
    assertThat(myFixture.availableIntentions.filter { it.text.startsWith("Add '@Composable' to ") })
      .isEmpty()
  }
}
