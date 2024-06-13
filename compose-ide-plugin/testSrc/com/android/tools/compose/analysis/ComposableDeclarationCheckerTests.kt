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
package com.android.tools.compose.analysis

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.junit.Test

/** Tests for [ComposableDeclarationChecker] */
class ComposableDeclarationCheckerTests : AbstractComposeDiagnosticsTest() {

  @Test
  fun testPropertyWithInitializer() {
    doTest(
      """
      import androidx.compose.runtime.Composable

      val <error descr="[COMPOSABLE_PROPERTY_BACKING_FIELD] Composable properties are not able to have backing fields">bar</error>: Int = 123
          @Composable get() = field
        """
    )
  }

  @Test
  fun testComposableFunctionReferences() {
    if (!KotlinPluginModeProvider.isK2Mode()) {
      doTest(
        """
      import androidx.compose.runtime.Composable

      @Composable fun A() {}
      val aCallable: () -> Unit = <error descr="[COMPOSABLE_FUNCTION_REFERENCE] Function References of @Composable functions are not currently supported" textAttributesKey="ERRORS_ATTRIBUTES">::A</error>
      val bCallable: @Composable () -> Unit = <error descr="[COMPOSABLE_FUNCTION_REFERENCE] Function References of @Composable functions are not currently supported" textAttributesKey="ERRORS_ATTRIBUTES"><error descr="[TYPE_MISMATCH] Type inference failed. Expected type mismatch: inferred type is KFunction0<Unit> but @Composable () -> Unit was expected" textAttributesKey="ERRORS_ATTRIBUTES">::A</error></error>
      val cCallable = <error descr="[COMPOSABLE_FUNCTION_REFERENCE] Function References of @Composable functions are not currently supported" textAttributesKey="ERRORS_ATTRIBUTES">::A</error>
      fun doSomething(fn: () -> Unit) { print(fn) }
      @Composable fun B(content: @Composable () -> Unit) {
          content()
          doSomething(<error descr="[COMPOSABLE_FUNCTION_REFERENCE] Function References of @Composable functions are not currently supported" textAttributesKey="ERRORS_ATTRIBUTES">::A</error>)
          B(<error descr="[COMPOSABLE_FUNCTION_REFERENCE] Function References of @Composable functions are not currently supported" textAttributesKey="ERRORS_ATTRIBUTES"><error descr="[TYPE_MISMATCH] Type inference failed. Expected type mismatch: inferred type is KFunction0<Unit> but @Composable () -> Unit was expected" textAttributesKey="ERRORS_ATTRIBUTES">::A</error></error>)
      }
        """
      )
    } else {
      // In K2, we are taking composability into account when resolving function references,
      // so trying to resolve `::A` in a context where we expect a non-composable function
      // type fails with an `UNRESOLVED_REFERENCE` error, instead of a
      // `COMPOSABLE_FUNCTION_REFERENCE` error in the plugin.
      doTest(
        """
      import androidx.compose.runtime.Composable

      @Composable fun A() {}
      val aCallable: () -> Unit = <error descr="[COMPOSABLE_FUNCTION_REFERENCE] Function References of @Composable functions are not currently supported" textAttributesKey="ERRORS_ATTRIBUTES"><error descr="[INITIALIZER_TYPE_MISMATCH] Initializer type mismatch: expected 'kotlin.Function0<kotlin.Unit>', actual 'androidx.compose.runtime.internal.KComposableFunction0<kotlin.Unit>'." textAttributesKey="ERRORS_ATTRIBUTES">::A</error></error>
      val bCallable: @Composable () -> Unit = <error descr="[COMPOSABLE_FUNCTION_REFERENCE] Function References of @Composable functions are not currently supported" textAttributesKey="ERRORS_ATTRIBUTES">::A</error>
      val cCallable = <error descr="[COMPOSABLE_FUNCTION_REFERENCE] Function References of @Composable functions are not currently supported" textAttributesKey="ERRORS_ATTRIBUTES">::A</error>
      fun doSomething(fn: () -> Unit) { print(fn) }
      @Composable fun B(content: @Composable () -> Unit) {
          content()
          doSomething(::<error descr="[INAPPLICABLE_CANDIDATE] Inapplicable candidate(s): @Composable() fun A(): Unit" textAttributesKey="ERRORS_ATTRIBUTES">A</error>)
          B(<error descr="[COMPOSABLE_FUNCTION_REFERENCE] Function References of @Composable functions are not currently supported" textAttributesKey="ERRORS_ATTRIBUTES">::A</error>)
      }
        """
      )
    }
  }

  @Test
  fun testNonComposableFunctionReferences() {
    // This code fails for two different reasons in K1 and K2. In K1, the code fails with
    // a TYPE_MISMATCH, since we infer a non-composable function type in a context where a
    // composable function type is expected. In K2, we can promote non-composable function
    // types to composable function types (as this matches the behavior for suspend functions),
    // but we explicitly forbid composable function references.
    if (!KotlinPluginModeProvider.isK2Mode()) {
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
    } else {
      doTest(
        """
        import androidx.compose.runtime.Composable

        fun A() {}
        val aCallable: () -> Unit = ::A
        val bCallable: @Composable () -> Unit = <error descr="[COMPOSABLE_FUNCTION_REFERENCE] Function References of @Composable functions are not currently supported">::A</error>
        val cCallable = ::A
        fun doSomething(fn: () -> Unit) { print(fn) }
        @Composable fun B(content: @Composable () -> Unit) {
            content()
            doSomething(::A)
            B(<error descr="[COMPOSABLE_FUNCTION_REFERENCE] Function References of @Composable functions are not currently supported">::A</error>)
        }
        """
      )
    }
  }

  @Test
  fun testPropertyWithJustGetter() {
    doTest(
      """
          import androidx.compose.runtime.Composable

          <error descr="${wrongAnnotationTargetError("top level property without backing field or delegate")}">@Composable</error>
          val bar: Int get() = 123
        """
    )
  }

  @Test
  fun testPropertyWithGetterAndSetter() {
    doTest(
      """
      import androidx.compose.runtime.Composable

      var <error descr="[COMPOSABLE_VAR] Composable properties are not able to have backing fields">bam2</error>: Int
          @Composable get() { return 123 }
          set(value) { print(value) }

      var <error descr="[COMPOSABLE_VAR] Composable properties are not able to have backing fields">bam3</error>: Int
          @Composable get() { return 123 }
          <error descr="${wrongAnnotationTargetError("setter")}">@Composable</error> set(value) { print(value) }

      var <error descr="[COMPOSABLE_VAR] Composable properties are not able to have backing fields">bam4</error>: Int
          get() { return 123 }
          <error descr="${wrongAnnotationTargetError("setter")}">@Composable</error> set(value) { print(value) }
        """
    )
  }

  @Test
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

  @Test
  fun testSuspendComposable() {
    if (!KotlinPluginModeProvider.isK2Mode()) {
      doTest(
        """
      import androidx.compose.runtime.Composable

      @Composable suspend fun <error descr="[COMPOSABLE_SUSPEND_FUN] Composable function cannot be annotated as suspend">Foo</error>() {}

      fun acceptSuspend(fn: suspend () -> Unit) { print(fn) }
      fun acceptComposableSuspend(fn: <error descr="[COMPOSABLE_SUSPEND_FUN] Composable function cannot be annotated as suspend">@Composable suspend () -> Unit</error>) { print(fn.hashCode()) }

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
    } else {
      // In K2, the frontend forbids function types with multiple kinds, so
      // `@Composable suspend` function types get turned into error types. This is the
      // reason for the additional ARGUMENT_TYPE_MISMATCH errors.
      doTest(
        """
      import androidx.compose.runtime.Composable

      @Composable suspend fun <error descr="[COMPOSABLE_SUSPEND_FUN] Composable function cannot be annotated as suspend">Foo</error>() {}

      fun acceptSuspend(fn: suspend () -> Unit) { print(fn) }
      fun acceptComposableSuspend(fn: <error descr="[AMBIGUOUS_FUNCTION_TYPE_KIND] Multiple function type conversions are prohibited for a single type. Detected type conversions: [suspend, @Composable]">@Composable suspend () -> Unit</error>) { print(fn.hashCode()) }

      val foo: suspend () -> Unit = <error descr="[INITIALIZER_TYPE_MISMATCH] Initializer type mismatch: expected 'kotlin.coroutines.SuspendFunction0<kotlin.Unit>', actual 'androidx.compose.runtime.internal.ComposableFunction0<kotlin.Unit>'.">@Composable {}</error>
      val bar: suspend () -> Unit = {}
      fun Test() {
          val composableLambda = @Composable {}
          acceptSuspend @Composable <error descr="[ARGUMENT_TYPE_MISMATCH] Argument type mismatch: actual type is 'androidx.compose.runtime.internal.ComposableFunction0<kotlin.Unit>', but 'kotlin.coroutines.SuspendFunction0<kotlin.Unit>' was expected.">{}</error>
          acceptComposableSuspend @Composable <error descr="[ARGUMENT_TYPE_MISMATCH] Argument type mismatch: actual type is 'androidx.compose.runtime.internal.ComposableFunction0<kotlin.Unit>', but '@Composable() kotlin.Function0<kotlin.Unit>' was expected.">{}</error>
          acceptComposableSuspend(<error descr="[ARGUMENT_TYPE_MISMATCH] Argument type mismatch: actual type is 'androidx.compose.runtime.internal.ComposableFunction0<kotlin.Unit>', but '@Composable() kotlin.Function0<kotlin.Unit>' was expected.">composableLambda</error>)
          acceptSuspend(<error descr="Expecting ')'"><error descr="Expecting an expression"><</error></error><error descr="Unexpected tokens (use ';' to separate expressions on the same line)">!COMPOSABLE_SUSPEND_FUN, TYPE_MISMATCH!>@Composable suspend fun()</error> { }<error descr="Unexpected tokens (use ';' to separate expressions on the same line)">)</error>
      }
        """
      )
    }
  }

  @Test
  fun testMissingComposableOnOverride() {
    // In K1, we report the `CONFLICTING_OVERLOADS` error on properties as well as property
    // accessors. In K2 we only report the error on property accessors.
    if (!KotlinPluginModeProvider.isK2Mode()) {
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
          <error descr="[CONFLICTING_OVERLOADS] @Composable annotation mismatch with overridden function: public open fun composableFunction(param: Boolean): Boolean defined in com.example.FakeFoo, @Composable public abstract fun composableFunction(param: Boolean): Boolean defined in com.example.Foo">override fun composableFunction(param: Boolean)</error> = true
          <error descr="[CONFLICTING_OVERLOADS] @Composable annotation mismatch with overridden function: @Composable public open fun nonComposableFunction(param: Boolean): Boolean defined in com.example.FakeFoo, public abstract fun nonComposableFunction(param: Boolean): Boolean defined in com.example.Foo">@Composable override fun nonComposableFunction(param: Boolean)</error> = true
          <error descr="[CONFLICTING_OVERLOADS] @Composable annotation mismatch with overridden function: public open val nonComposableProperty: Boolean defined in com.example.FakeFoo, public abstract val nonComposableProperty: Boolean defined in com.example.Foo">override val nonComposableProperty: Boolean</error> <error descr="[CONFLICTING_OVERLOADS] @Composable annotation mismatch with overridden function: @Composable public open fun `<get-nonComposableProperty>`(): Boolean defined in com.example.FakeFoo, public abstract fun `<get-nonComposableProperty>`(): Boolean defined in com.example.Foo">@Composable get()</error> = true
      }

      interface Bar {
          @Composable
          fun composableFunction(param: Boolean): Boolean
          val composableProperty: Boolean @Composable get()<EOLError descr="Expecting function body"></EOLError>
          fun nonComposableFunction(param: Boolean): Boolean
          val nonComposableProperty: Boolean
      }

      object FakeBar : Bar {
          <error descr="[CONFLICTING_OVERLOADS] @Composable annotation mismatch with overridden function: public open fun composableFunction(param: Boolean): Boolean defined in com.example.FakeBar, @Composable public abstract fun composableFunction(param: Boolean): Boolean defined in com.example.Bar">override fun composableFunction(param: Boolean)</error> = true
          <error descr="[CONFLICTING_OVERLOADS] @Composable annotation mismatch with overridden function: public open val composableProperty: Boolean defined in com.example.FakeBar, public abstract val composableProperty: Boolean defined in com.example.Bar">override val composableProperty: Boolean</error> <error descr="[CONFLICTING_OVERLOADS] @Composable annotation mismatch with overridden function: public open fun `<get-composableProperty>`(): Boolean defined in com.example.FakeBar, @Composable public abstract fun `<get-composableProperty>`(): Boolean defined in com.example.Bar">get()</error> = true
          <error descr="[CONFLICTING_OVERLOADS] @Composable annotation mismatch with overridden function: @Composable public open fun nonComposableFunction(param: Boolean): Boolean defined in com.example.FakeBar, public abstract fun nonComposableFunction(param: Boolean): Boolean defined in com.example.Bar">@Composable override fun nonComposableFunction(param: Boolean)</error> = true
          <error descr="[CONFLICTING_OVERLOADS] @Composable annotation mismatch with overridden function: public open val nonComposableProperty: Boolean defined in com.example.FakeBar, public abstract val nonComposableProperty: Boolean defined in com.example.Bar">override val nonComposableProperty: Boolean</error> <error descr="[CONFLICTING_OVERLOADS] @Composable annotation mismatch with overridden function: @Composable public open fun `<get-nonComposableProperty>`(): Boolean defined in com.example.FakeBar, public abstract fun `<get-nonComposableProperty>`(): Boolean defined in com.example.Bar">@Composable get()</error> = true
      }

        """
      )
    } else {
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
          <error descr="[CONFLICTING_OVERLOADS] Conflicting overloads: [fun composableFunction(param: Boolean): Boolean, @Composable() fun composableFunction(param: Boolean): Boolean]">override fun composableFunction(param: Boolean)</error> = true
          <error descr="[CONFLICTING_OVERLOADS] Conflicting overloads: [@Composable() fun nonComposableFunction(param: Boolean): Boolean, fun nonComposableFunction(param: Boolean): Boolean]">@Composable override fun nonComposableFunction(param: Boolean)</error> = true
          override val nonComposableProperty: Boolean get() = true
      }

      interface Bar {
          @Composable
          fun composableFunction(param: Boolean): Boolean
          val composableProperty: Boolean @Composable get()<EOLError descr="Expecting function body"></EOLError>
          fun nonComposableFunction(param: Boolean): Boolean
          val nonComposableProperty: Boolean
      }

      object FakeBar : Bar {
          <error descr="[CONFLICTING_OVERLOADS] Conflicting overloads: [fun composableFunction(param: Boolean): Boolean, @Composable() fun composableFunction(param: Boolean): Boolean]">override fun composableFunction(param: Boolean)</error> = true
          override val composableProperty: Boolean get() = true
          <error descr="[CONFLICTING_OVERLOADS] Conflicting overloads: [@Composable() fun nonComposableFunction(param: Boolean): Boolean, fun nonComposableFunction(param: Boolean): Boolean]">@Composable override fun nonComposableFunction(param: Boolean)</error> = true
          override val nonComposableProperty: Boolean <error descr="[CONFLICTING_OVERLOADS] Conflicting overloads: [@Composable() get(): Boolean, get(): Boolean]">@Composable get()</error> = true
      }

        """
      )
    }
  }

  @Test
  fun testComposableMainFun1() {
    doTest(
      """
            import androidx.compose.runtime.Composable

            @Composable fun <error descr="[COMPOSABLE_FUN_MAIN] Composable main functions are not currently supported">main</error>() {}
        """
    )
  }

  @Test
  fun testComposableMainFun2() {
    doTest(
      """
            import androidx.compose.runtime.Composable

            fun print(message: Any?) {}

            @Composable fun <error descr="[COMPOSABLE_FUN_MAIN] Composable main functions are not currently supported">main</error>(args: Array<String>) {
                print(args)
            }
        """
    )
  }

  @Test
  fun testComposableMainFun3() {
    doTest(
      """
            import androidx.compose.runtime.Composable

            fun print(message: Any?) {}

            class Foo

            @Composable fun main(foo: Foo) {
                print(foo)
            }
        """
    )
  }

  @Test
  fun testInferenceOverComplexConstruct1() {
    doTest(
      """
            import androidx.compose.runtime.Composable
            val composable: @Composable ()->Unit = if(true) { { } } else { { } }
        """
    )
  }

  @Test
  fun testInferenceOverComplexConstruct2() {
    doTest(
      """
            import androidx.compose.runtime.Composable
            @Composable fun foo() { }
            val composable: @Composable ()->Unit = if(true) { { } } else { { foo() } }
        """
    )
  }

  @Test
  fun testInterfaceComposablesWithDefaultParameters() {
    doTest(
      """
            import androidx.compose.runtime.Composable
            interface A {
                @Composable fun foo(x: Int = <error descr="[ABSTRACT_COMPOSABLE_DEFAULT_PARAMETER_VALUE] Overridable Composable functions with default values are not currently supported">0</error>)
            }
        """
    )
  }

  @Test
  fun testAbstractComposablesWithDefaultParameters() {
    doTest(
      """
            import androidx.compose.runtime.Composable
            abstract class A {
                @Composable abstract fun foo(x: Int = <error descr="[ABSTRACT_COMPOSABLE_DEFAULT_PARAMETER_VALUE] Overridable Composable functions with default values are not currently supported">0</error>)
            }
        """
    )
  }

  @Test
  fun testInterfaceComposablesWithoutDefaultParameters() {
    doTest(
      """
            import androidx.compose.runtime.Composable
            interface A {
                @Composable fun foo(x: Int)
            }
        """
    )
  }

  @Test
  fun testAbstractComposablesWithoutDefaultParameters() {
    doTest(
      """
            import androidx.compose.runtime.Composable
            abstract class A {
                @Composable abstract fun foo(x: Int)
            }
        """
    )
  }

  @Test
  fun testOverrideWithoutComposeAnnotation() {
    if (!KotlinPluginModeProvider.isK2Mode()) {
      doTest(
        """
                import androidx.compose.runtime.Composable
                interface Base {
                    fun compose(content: () -> Unit)
                }

                class Impl : Base {
                    <error descr="[CONFLICTING_OVERLOADS] @Composable annotation mismatch with overridden function: value-parameter content: @Composable () -> Unit defined in com.example.Impl.compose, value-parameter content: () -> Unit defined in com.example.Base.compose">override fun compose(content: @Composable () -> Unit)</error> {}
                }
            """
      )
    } else {
      // In K2, the `@Composable` type is part of the function signature, so the `override`
      // does not match the `compose` function in `Base`.
      doTest(
        """
                import androidx.compose.runtime.Composable
                interface Base {
                    fun compose(content: () -> Unit)
                }

                <error descr="[ABSTRACT_MEMBER_NOT_IMPLEMENTED] Class 'Impl' is not abstract and does not implement abstract member 'compose'." textAttributesKey="ERRORS_ATTRIBUTES">class Impl</error> : Base {
                    <error descr="[NOTHING_TO_OVERRIDE] 'compose' overrides nothing." textAttributesKey="ERRORS_ATTRIBUTES">override</error> fun compose(content: @Composable () -> Unit) {}
                }
            """
      )
    }
  }

  @Test
  fun testOverrideComposableLambda() {
    doTest(
      """
                import androidx.compose.runtime.Composable

                class Impl : @Composable () -> Unit {
                    @Composable
                    override fun invoke() {}
                }
            """
    )
  }

  @Test
  fun testTransitiveOverrideComposableLambda() {
    doTest(
      """
                import androidx.compose.runtime.Composable

                interface ComposableFunction : @Composable () -> Unit

                class Impl : ComposableFunction {
                    @Composable
                    override fun invoke() {}
                }
            """
    )
  }

  @Test
  fun testMissingOverrideComposableLambda() {
    val functionDeclarationWithError =
      if (!KotlinPluginModeProvider.isK2Mode())
        "<error descr=\"[CONFLICTING_OVERLOADS] @Composable annotation mismatch with overridden function: public open fun invoke(): Unit defined in com.example.Impl, public abstract operator fun invoke(): Unit defined in kotlin.Function0\" textAttributesKey=\"ERRORS_ATTRIBUTES\">override fun invoke()</error> {}"
      else
        "<error descr=\"[CONFLICTING_OVERLOADS] Conflicting overloads: [fun invoke(): Unit, @Composable() fun invoke(): R]\" textAttributesKey=\"ERRORS_ATTRIBUTES\">override fun invoke()</error> {}"
    doTest(
      """
                import androidx.compose.runtime.Composable

                class Impl : @Composable () -> Unit {
                    $functionDeclarationWithError
                }
            """
    )
  }

  @Test
  fun testWrongOverrideLambda() {
    val functionDeclarationWithError =
      if (!KotlinPluginModeProvider.isK2Mode())
        "<error descr=\"[CONFLICTING_OVERLOADS] @Composable annotation mismatch with overridden function: @Composable public open fun invoke(): Unit defined in com.example.Impl, public abstract operator fun invoke(): Unit defined in kotlin.Function0\" textAttributesKey=\"ERRORS_ATTRIBUTES\">@Composable override fun invoke()</error> {}"
      else
        "<error descr=\"[CONFLICTING_OVERLOADS] Conflicting overloads: [@Composable() fun invoke(): Unit, fun invoke(): R]\" textAttributesKey=\"ERRORS_ATTRIBUTES\">@Composable override fun invoke()</error> {}"
    doTest(
      """
                import androidx.compose.runtime.Composable

                class Impl : () -> Unit {
                    $functionDeclarationWithError
                }
            """
    )
  }

  @Test
  fun testMultipleOverrideLambda() {
    val functionDeclarationWithError =
      if (!KotlinPluginModeProvider.isK2Mode())
        "<error descr=\"[CONFLICTING_OVERLOADS] @Composable annotation mismatch with overridden function: @Composable public open fun invoke(): Unit defined in com.example.Impl, public abstract operator fun invoke(): Unit defined in kotlin.Function0\" textAttributesKey=\"ERRORS_ATTRIBUTES\">@Composable override fun invoke()</error> {}"
      else
        "<error descr=\"[CONFLICTING_OVERLOADS] Conflicting overloads: [@Composable() fun invoke(): Unit, fun invoke(): R]\" textAttributesKey=\"ERRORS_ATTRIBUTES\">@Composable override fun invoke()</error> {}"
    val mixingFunctionalKindsInSupertypes =
      if (!KotlinPluginModeProvider.isK2Mode()) "() -> Unit, @Composable (Int) -> Unit"
      else
        "<error descr=\"[MIXING_FUNCTIONAL_KINDS_IN_SUPERTYPES] Mixing supertypes of different functional kinds ([Function, @Composable]) is not allowed.\" textAttributesKey=\"ERRORS_ATTRIBUTES\">() -> Unit, @Composable (Int) -> Unit</error>"
    doTest(
      """
                import androidx.compose.runtime.Composable

                class Impl : $mixingFunctionalKindsInSupertypes {
                    $functionDeclarationWithError
                    @Composable override fun invoke(p0: Int) {}
                }
            """
    )
  }
}
