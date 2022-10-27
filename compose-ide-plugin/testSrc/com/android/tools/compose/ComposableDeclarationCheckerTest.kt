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
package com.android.tools.compose

import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.android.compose.stubComposeRuntime
import org.jetbrains.android.compose.stubKotlinStdlib

/**
 * Tests for [ComposeSampleResolutionService]
 */
class ComposableDeclarationCheckerTest : JavaCodeInsightFixtureTestCase() {
  fun testPropertyWithInitializer() {
    doTest(
      """
      import androidx.compose.runtime.Composable

      val <error descr="[COMPOSABLE_PROPERTY_BACKING_FIELD] Composable properties are not able to have backing fields">bar</error>: Int = 123
          @Composable get() = field
        """
    )
  }

  fun testComposableFunctionReferences() {
    doTest(
      """
      import androidx.compose.runtime.Composable

      @Composable fun A() {}
      val aCallable: () -> Unit = <error descr="[COMPOSABLE_FUNCTION_REFERENCE] Function References of @Composable functions are not currently supported">::A</error>
      val bCallable: @Composable () -> Unit =<error descr="Expecting an expression"> </error><error descr="Property getter or setter expected"><!COMPOSABLE_FUNCTION_REFERENCE,TYPE_MISMATCH!>::A</error>
      val cCallable = <error descr="[COMPOSABLE_FUNCTION_REFERENCE] Function References of @Composable functions are not currently supported">::A</error>
      fun doSomething(fn: () -> Unit) { print(fn) }
      @Composable fun B(content: @Composable () -> Unit) {
          content()
          doSomething(<error descr="[COMPOSABLE_FUNCTION_REFERENCE] Function References of @Composable functions are not currently supported">::A</error>)
          B(<error descr="Expecting ')'"><error descr="Expecting an expression"><</error></error><error descr="Unexpected tokens (use ';' to separate expressions on the same line)">!COMPOSABLE_FUNCTION_REFERENCE,TYPE_MISMATCH!>::A)</error>
      }
        """
    )
  }

  fun testNonComposableFunctionReferences() {
    doTest(
      """
        import androidx.compose.runtime.Composable

        fun A() {}
        val aCallable: () -> Unit = ::A
        val bCallable: @Composable () -> Unit = <error descr="[TYPE_MISMATCH] Type inference failed. Expected type mismatch: inferred type is KFunction0<Unit> but @Composable () -> Unit was expected">::A</error>
        val cCallable = ::A
        fun doSomething(fn: () -> Unit) { print(fn) }
        @Composable fun B(content: @Composable () -> Unit) {
            content()
            doSomething(::A)
            B(<error descr="[TYPE_MISMATCH] Type inference failed. Expected type mismatch: inferred type is KFunction0<Unit> but @Composable () -> Unit was expected">::A</error>)
        }
        """
    )
  }

  fun testPropertyWithJustGetter() {
    doTest(
      """
          import androidx.compose.runtime.Composable

          <error descr="[WRONG_ANNOTATION_TARGET] This annotation is not applicable to target 'top level property without backing field or delegate'">@Composable</error>
          val bar: Int get() = 123
        """
    )
  }

  fun testPropertyWithGetterAndSetter() {
    doTest(
      """
      import androidx.compose.runtime.Composable

      var <error descr="[COMPOSABLE_VAR] Composable properties are not able to have backing fields">bam2</error>: Int
          @Composable get() { return 123 }
          set(value) { print(value) }

      var <error descr="[COMPOSABLE_VAR] Composable properties are not able to have backing fields">bam3</error>: Int
          @Composable get() { return 123 }
          <error descr="[WRONG_ANNOTATION_TARGET] This annotation is not applicable to target 'setter'">@Composable</error> set(value) { print(value) }

      var <error descr="[COMPOSABLE_VAR] Composable properties are not able to have backing fields">bam4</error>: Int
          get() { return 123 }
          <error descr="[WRONG_ANNOTATION_TARGET] This annotation is not applicable to target 'setter'">@Composable</error> set(value) { print(value) }
        """
    )
  }

  fun testPropertyGetterAllForms() {
    doTest(
      """
            import androidx.compose.runtime.Composable

            val bar2: Int @Composable get() = 123
            @get:Composable val bar3: Int get() = 123

            interface Foo {
                val bar2: Int @Composable get() = 123
                @get:Composable val bar3: Int get() = 123
            }
        """
    )
  }

  fun testSuspendComposable() {
    doTest(
      """
      import androidx.compose.runtime.Composable

      @Composable suspend fun <error descr="[COMPOSABLE_SUSPEND_FUN] Suspend functions cannot be made Composable">Foo</error>() {}

      fun acceptSuspend(fn: suspend () -> Unit) { print(fn) }
      fun acceptComposableSuspend(fn: <error descr="[COMPOSABLE_SUSPEND_FUN] Suspend functions cannot be made Composable">@Composable suspend () -> Unit</error>) { print(fn.hashCode()) }

      val foo: suspend () -> Unit = <error descr="[TYPE_MISMATCH] Type inference failed. Expected type mismatch: inferred type is @Composable suspend () -> Unit but suspend () -> Unit was expected">@Composable {}</error>
      val bar: suspend () -> Unit = {}
      fun Test() {
          val composableLambda = @Composable {}
          acceptSuspend <error descr="[TYPE_MISMATCH] Type inference failed. Expected type mismatch: inferred type is @Composable suspend () -> Unit but suspend () -> Unit was expected">@Composable {}</error>
          acceptComposableSuspend @Composable {}
          acceptComposableSuspend(composableLambda)
          acceptSuspend(<error descr="Expecting ')'"><error descr="Expecting an expression"><</error></error><error descr="Unexpected tokens (use ';' to separate expressions on the same line)">!COMPOSABLE_SUSPEND_FUN, TYPE_MISMATCH!>@Composable suspend fun()</error> <warning descr="[UNUSED_LAMBDA_EXPRESSION] The lambda expression is unused. If you mean a block, you can use 'run { ... }'">{ }</warning><error descr="Unexpected tokens (use ';' to separate expressions on the same line)">)</error>
      }
        """
    )
  }

  fun testMissingComposableOnOverride() {
    doTest(
      """
      import androidx.compose.runtime.Composable

      interface Foo {
          @Composable
          fun composableFunction(param: Boolean): Boolean
          fun nonComposableFunction(param: Boolean): Boolean
          val nonComposableProperty: Boolean
      }

      object FakeFoo : Foo {
          <error descr="[CONFLICTING_OVERLOADS] Conflicting overloads: public open fun composableFunction(param: Boolean): Boolean defined in com.example.FakeFoo, @Composable public abstract fun composableFunction(param: Boolean): Boolean defined in com.example.Foo">override fun composableFunction(param: Boolean)</error> = true
          <error descr="[CONFLICTING_OVERLOADS] Conflicting overloads: @Composable public open fun nonComposableFunction(param: Boolean): Boolean defined in com.example.FakeFoo, public abstract fun nonComposableFunction(param: Boolean): Boolean defined in com.example.Foo">@Composable override fun nonComposableFunction(param: Boolean)</error> = true
          <error descr="[CONFLICTING_OVERLOADS] Conflicting overloads: public open val nonComposableProperty: Boolean defined in com.example.FakeFoo, public abstract val nonComposableProperty: Boolean defined in com.example.Foo">override val nonComposableProperty: Boolean</error> <error descr="[CONFLICTING_OVERLOADS] Conflicting overloads: @Composable public open fun `<get-nonComposableProperty>`(): Boolean defined in com.example.FakeFoo, public abstract fun `<get-nonComposableProperty>`(): Boolean defined in com.example.Foo">@Composable get()</error> = true
      }

      interface Bar {
          @Composable
          fun composableFunction(param: Boolean): Boolean
          val composableProperty: Boolean @Composable get()<EOLError descr="Expecting function body"></EOLError>
          fun nonComposableFunction(param: Boolean): Boolean
          val nonComposableProperty: Boolean
      }

      object FakeBar : Bar {
          <error descr="[CONFLICTING_OVERLOADS] Conflicting overloads: public open fun composableFunction(param: Boolean): Boolean defined in com.example.FakeBar, @Composable public abstract fun composableFunction(param: Boolean): Boolean defined in com.example.Bar">override fun composableFunction(param: Boolean)</error> = true
          <error descr="[CONFLICTING_OVERLOADS] Conflicting overloads: public open val composableProperty: Boolean defined in com.example.FakeBar, public abstract val composableProperty: Boolean defined in com.example.Bar">override val composableProperty: Boolean</error> <error descr="[CONFLICTING_OVERLOADS] Conflicting overloads: public open fun `<get-composableProperty>`(): Boolean defined in com.example.FakeBar, @Composable public abstract fun `<get-composableProperty>`(): Boolean defined in com.example.Bar">get()</error> = true
          <error descr="[CONFLICTING_OVERLOADS] Conflicting overloads: @Composable public open fun nonComposableFunction(param: Boolean): Boolean defined in com.example.FakeBar, public abstract fun nonComposableFunction(param: Boolean): Boolean defined in com.example.Bar">@Composable override fun nonComposableFunction(param: Boolean)</error> = true
          <error descr="[CONFLICTING_OVERLOADS] Conflicting overloads: public open val nonComposableProperty: Boolean defined in com.example.FakeBar, public abstract val nonComposableProperty: Boolean defined in com.example.Bar">override val nonComposableProperty: Boolean</error> <error descr="[CONFLICTING_OVERLOADS] Conflicting overloads: @Composable public open fun `<get-nonComposableProperty>`(): Boolean defined in com.example.FakeBar, public abstract fun `<get-nonComposableProperty>`(): Boolean defined in com.example.Bar">@Composable get()</error> = true
      }

        """
    )
  }

  fun testComposableMainFun1() {
    doTest(
      """
            import androidx.compose.runtime.Composable

            @Composable fun <error descr="[COMPOSABLE_FUN_MAIN] Composable main functions are not currently supported">main</error>() {}
        """
    )
  }

  fun testComposableMainFun2() {
    doTest(
      """
            import androidx.compose.runtime.Composable

            fun print(<warning descr="[UNUSED_PARAMETER] Parameter 'message' is never used">message</warning>: Any?) {}

            @Composable fun <error descr="[COMPOSABLE_FUN_MAIN] Composable main functions are not currently supported">main</error>(args: Array<String>) {
                print(args)
            }
        """
    )
  }

  fun testComposableMainFun3() {
    doTest(
      """
            import androidx.compose.runtime.Composable

            fun print(<warning descr="[UNUSED_PARAMETER] Parameter 'message' is never used">message</warning>: Any?) {}

            class Foo

            @Composable fun main(foo: Foo) {
                print(foo)
            }
        """
    )
  }

  fun testInferenceOverComplexConstruct1() {
    doTest(
      """
            import androidx.compose.runtime.Composable
            val composable: @Composable ()->Unit = if(true) { { } } else { { } }
        """
    )
  }

  fun testInferenceOverComplexConstruct2() {
    doTest(
      """
            import androidx.compose.runtime.Composable
            @Composable fun foo() { }
            val composable: @Composable ()->Unit = if(true) { { } } else { { foo() } }
        """
    )
  }

  private fun doTest(expectedText: String): Unit = myFixture.run {
    stubComposeRuntime()
    stubKotlinStdlib()

    val file = addFileToProject(
      "src/com/example/test.kt",
      """
      package com.example

      $expectedText
      """.trimIndent()
    )

    configureFromExistingVirtualFile(file.virtualFile)
    checkHighlighting()
  }

}