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

import com.android.tools.compose.COMPOSABLE_CALL_TEXT_TYPE
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.testFramework.ExpectedHighlightingData
import junit.framework.Assert.assertEquals
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Tests for [ComposableFunctionCallChecker] and [ComposablePropertyAccessExpressionChecker]. */
class ComposableCheckerTests : AbstractComposeDiagnosticsTest() {

  @Test
  fun testCfromNC() =
    doTest(
      """
    import androidx.compose.runtime.*

    @Composable fun C() {}
    fun <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">NC</error>() { <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">C</error>() }

    """
    )

  @Test
  fun testNCfromC() =
    doTest(
      """
    import androidx.compose.runtime.*

    fun NC() {}
    @Composable fun C() { NC() }
    """
    )

  @Test
  fun testCfromC() =
    doTest(
      """
        import androidx.compose.runtime.*

        @Composable fun C() {}
        @Composable fun C2() { C() }
    """
    )

  @Test
  fun testCinCLambdaArg() =
    doTest(
      """
    import androidx.compose.runtime.*
    @Composable fun C() { }
    @Composable fun C2(lambda: @Composable () -> Unit) { lambda() }
    @Composable fun C3() {
        C2 {
            C()
        }
    }
    """
    )

  @Test
  fun testCinInlinedNCLambdaArg() =
    doTest(
      """
    import androidx.compose.runtime.*
    @Composable fun C() { }
    inline fun InlineNC(lambda: () -> Unit) { lambda() }
    @Composable fun C3() {
        InlineNC {
            C()
        }
    }
    """
    )

  @Test
  fun testCinNoinlineNCLambdaArg() =
    doTest(
      """
        import androidx.compose.runtime.*
        @Composable fun C() { }
        <warning descr="$nothingToInline">inline</warning> fun NoinlineNC(noinline lambda: () -> Unit) { lambda() }
        @Composable fun C3() {
            NoinlineNC {
                <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">C</error>()
            }
        }
    """
    )

  @Test
  fun testCinCrossinlineNCLambdaArg() =
    doTest(
      """
        import androidx.compose.runtime.*
        @Composable fun C() { }
        inline fun CrossinlineNC(crossinline lambda: () -> Unit) { lambda() }
        @Composable fun C3() {
            CrossinlineNC {
                <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">C</error>()
            }
        }
    """
    )

  @Test
  fun testCinNestedInlinedNCLambdaArg() =
    doTest(
      """
        import androidx.compose.runtime.*
        @Composable fun C() { }
        inline fun InlineNC(lambda: () -> Unit) { lambda() }
        @Composable fun C3() {
            InlineNC {
                InlineNC {
                    C()
                }
            }
        }
    """
    )

  @Test
  fun testCinLambdaArgOfNC() =
    doTest(
      """
    import androidx.compose.runtime.*
    @Composable fun C() { }
    fun NC(lambda: () -> Unit) { lambda() }
    @Composable fun C3() {
        NC {
            <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">C</error>()
        }
    }
    """
    )

  @Test
  fun testCinLambdaArgOfC() =
    doTest(
      """
    import androidx.compose.runtime.*
    @Composable fun C() { }
    @Composable fun C2(lambda: () -> Unit) { lambda() }
    @Composable fun C3() {
        C2 {
            <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">C</error>()
        }
    }
    """
    )

  @Test
  fun testCinCPropGetter() =
    doTest(
      """
        import androidx.compose.runtime.*
        @Composable fun C(): Int { return 123 }
        val cProp: Int @Composable get() = C()
    """
    )

  @Test
  fun testCinNCPropGetter() =
    doTest(
      """
    import androidx.compose.runtime.*
    @Composable fun C(): Int { return 123 }
    val <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">ncProp</error>: Int get() = <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">C</error>()
    """
    )

  @Test
  fun testCinTopLevelInitializer() =
    doTest(
      """
    import androidx.compose.runtime.*
    @Composable fun C(): Int { return 123 }
    val ncProp: Int = <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">C</error>()
    <error descr="${wrongAnnotationTargetError("top level property with backing field")}">@Composable</error> val cProp: Int = <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">C</error>()
    """
    )

  @Test
  fun testCTypeAlias() =
    doTest(
      """
    import androidx.compose.runtime.*
    typealias Content = @Composable () -> Unit
    @Composable fun C() {}
    @Composable fun C2(content: Content) { content() }
    @Composable fun C3() {
        val inner: Content = { C() }
        C2 { C() }
        C2 { inner() }
    }
    """
    )

  @Test
  fun testCfromComposableFunInterface() =
    doTest(
      """
        import androidx.compose.runtime.Composable

        fun interface A { @Composable fun f() }
        @Composable fun B() { A { B() } }
    """
    )

  @Test
  fun testGenericComposableInference1() {
    assumeTrue(KotlinPluginModeProvider.isK2Mode())
    doTest(
      """
        import androidx.compose.runtime.Composable

        fun <T> identity(value: T): T = value

        // We should infer `ComposableFunction0<Unit>` for `T`
        val cl = identity(@Composable {})
        val l: () -> Unit = <error descr="[INITIALIZER_TYPE_MISMATCH] Initializer type mismatch: expected 'kotlin.Function0<kotlin.Unit>', actual 'androidx.compose.runtime.internal.ComposableFunction0<kotlin.Unit>'." textAttributesKey="ERRORS_ATTRIBUTES">cl</error>
        """
    )
  }

  @Test
  fun testGenericComposableInference2() {
    assumeTrue(KotlinPluginModeProvider.isK2Mode())
    doTest(
      """
        import androidx.compose.runtime.Composable

        @Composable fun A() {}
        fun <T> identity(value: T): T = value

        // Explicitly instantiate `T` with `ComposableFunction0<Unit>`
        val cl = identity<@Composable () -> Unit> { A() }
        val l: () -> Unit = <error descr="[INITIALIZER_TYPE_MISMATCH] Initializer type mismatch: expected 'kotlin.Function0<kotlin.Unit>', actual '@Composable() androidx.compose.runtime.internal.ComposableFunction0<kotlin.Unit>'." textAttributesKey="ERRORS_ATTRIBUTES">cl</error>
        """
    )
  }

  @Test
  fun testGenericComposableInference3() {
    assumeTrue(KotlinPluginModeProvider.isK2Mode())
    doTest(
      """
        import androidx.compose.runtime.Composable

        @Composable fun A() {}
        fun <T> identity(value: T): T = value

        // We should infer `T` as `ComposableFunction0<Unit>` from the context and then
        // infer that the argument to `identity` is a composable lambda.
        val cl: @Composable () -> Unit = identity { A() }
        """
    )
  }

  @Test
  fun testGenericComposableInference4() {
    assumeTrue(KotlinPluginModeProvider.isK2Mode())
    doTest(
      """
        import androidx.compose.runtime.Composable

        fun <T> identity(value: T): T = value

        // We should infer `T` as `Function0<Unit>` from the context and
        // reject the lambda which is explicitly typed as `ComposableFunction...`.
        val cl: () -> Unit = identity(@Composable <error descr="[ARGUMENT_TYPE_MISMATCH] Argument type mismatch: actual type is 'kotlin.Function0<ERROR CLASS: Unknown return lambda parameter type>', but 'androidx.compose.runtime.internal.ComposableFunction0<ERROR CLASS: Unknown return lambda parameter type>' was expected." textAttributesKey="ERRORS_ATTRIBUTES">{}</error>)
        """
    )
  }

  @Test
  fun testGenericComposableInference5() {
    assumeTrue(KotlinPluginModeProvider.isK2Mode())
    doTest(
      """
        import androidx.compose.runtime.Composable

        fun <T> identity(value: T): T = value

        // We should infer `Function0<Unit>` for `T`
        val lambda = identity<() -> Unit>(@Composable <error descr="[ARGUMENT_TYPE_MISMATCH] Argument type mismatch: actual type is 'kotlin.Function0<ERROR CLASS: Unknown return lambda parameter type>', but 'androidx.compose.runtime.internal.ComposableFunction0<ERROR CLASS: Unknown return lambda parameter type>' was expected." textAttributesKey="ERRORS_ATTRIBUTES">{}</error>)
        """
    )
  }

  @Test
  fun testCfromAnnotatedComposableFunInterface() =
    doTest(
      """
        import androidx.compose.runtime.Composable

        fun interface A { @Composable fun f() }
        @Composable fun B() {
          val f = @Composable { B() }
          A(f)
        }
    """
    )

  @Test
  fun testCfromComposableFunInterfaceArgument() =
    doTest(
      """
        import androidx.compose.runtime.Composable

        fun interface A { @Composable fun f() }

        @Composable fun B(a: (A) -> Unit) { a { B(a) } }
    """
    )

  @Test
  fun testCfromComposableTypeAliasFunInterface() =
    doTest(
      """
        import androidx.compose.runtime.Composable

        fun interface A { @Composable fun f() }
        typealias B = A

        @Composable fun C() { A { C() } }
    """
    )

  @Test
  fun testCfromNonComposableFunInterface() =
    doTest(
      """
        import androidx.compose.runtime.Composable

        fun interface A { fun f() }
        @Composable fun B() {
          A {
            <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">B</error>()
          }
        }
    """
    )

  @Test
  fun testCfromNonComposableFunInterfaceArgument() =
    doTest(
      """
        import androidx.compose.runtime.Composable

        fun interface A { fun f() }

        @Composable fun B(a: (A) -> Unit) {
          a {
            <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">B</error>(a)
          }
        }
    """
    )

  @Test
  fun testPreventedCaptureOnInlineLambda() =
    doTest(
      """
    import androidx.compose.runtime.*

    fun cond(): Boolean = true

    @Composable inline fun A(
        lambda: @DisallowComposableCalls () -> Unit
    ) { lambda() }
    @Composable fun B() {}

    @Composable fun C() {
        A { <error descr="[CAPTURED_COMPOSABLE_INVOCATION] Composable calls are not allowed inside the lambda parameter of ${if(KotlinPluginModeProvider.isK2Mode()) "A" else "inline fun A(lambda: () -> Unit): Unit"}">B</error>() }
    }
    """
    )

  @Test
  fun testComposableReporting001() {
    doTest(
      """
              import androidx.compose.runtime.*;

              @Composable
              fun Leaf() {}

              @Composable
              fun myStatelessFunctionalComponent() {
                  Leaf()
              }

              @Composable
              fun foo() {
                  myStatelessFunctionalComponent()
              }
          """
    )
  }

  @Test
  fun testComposableReporting002() {
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable
          fun Leaf() {}

          val myLambda1 = @Composable { Leaf() }
          val myLambda2: @Composable ()->Unit = { Leaf() }
          """
    )
  }

  @Test
  fun testComposableReporting006() {
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable
          fun Leaf() {}

          @Composable
          fun foo() {
              val bar = @Composable {
                  Leaf()
              }
              bar()
          }
          """
    )
  }

  @Test
  fun testComposableReporting007() {
    doTest(
      """
          import androidx.compose.runtime.*;

          fun <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">foo</error>(children: @Composable ()->Unit) {
              <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">children</error>()
          }
          """
    )
  }

  @Test
  fun testComposableReporting008() {
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable fun Leaf() {}

          fun <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">foo</error>() {
              val bar: @Composable ()->Unit = @Composable {
                  Leaf()
              }
              <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">bar</error>()
          }
          """
    )
  }

  @Test
  fun testComposableReporting009() {
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable fun Leaf() {}

          @Composable
          fun myStatelessFunctionalComponent() {
              Leaf()
          }

          fun <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">noise</error>() {
              <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">myStatelessFunctionalComponent</error>()
          }
          """
    )
  }

  @Test
  fun testComposableReporting017() {
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable fun Leaf() {}

          @Composable
          fun Foo(children: ()->Unit) {
              children()
          }

          @Composable
          fun test() {
              Foo { <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">Leaf</error>() }
          }
          """
    )
  }

  @Test
  fun testComposableReporting018() {
    val errorDescription =
      if (!KotlinPluginModeProvider.isK2Mode()) {
        "[TYPE_MISMATCH] Type inference failed. Expected type mismatch: inferred type is @Composable () -> Unit but () -> Unit was expected"
      } else {
        "[INITIALIZER_TYPE_MISMATCH] Initializer type mismatch: expected 'kotlin.Function0<kotlin.Unit>', actual 'androidx.compose.runtime.internal.ComposableFunction0<kotlin.Unit>'."
      }
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable
          fun Leaf() {}

          fun foo() {
              val myVariable: ()->Unit = <error descr="$errorDescription">@Composable { Leaf() }</error>
              print(myVariable)
          }
          """
    )
  }

  @Test
  fun testComposableReporting022() {
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable
          fun Leaf() {}

          fun <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">foo</error>() {
              val myList = listOf(1,2,3,4,5)
              myList.forEach { value: Int ->
                  <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">Leaf</error>()
                  print(value)
              }
          }
          """
    )
  }

  @Test
  fun testComposableReporting023() {
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable
          fun Leaf() {}

          @Composable
          fun foo() {
              val myList = listOf(1,2,3,4,5)
              myList.forEach { value: Int ->
                  Leaf()
                  print(value)
              }
          }
          """
    )
  }

  @Test
  fun testComposableReporting024() {
    doTest(
      """
          import androidx.compose.runtime.*

          var x: (@Composable () -> Unit)? = null

          class Foo
          fun Foo.setContent(content: @Composable () -> Unit) {
              x = content
          }

          @Composable
          fun Leaf() {}

          fun Example(foo: Foo) {
              foo.setContent { Leaf() }
          }
          """
    )
  }

  @Test
  fun testComposableReporting024x() {
    doTest(
      """
          import androidx.compose.runtime.*

          var x: (@Composable () -> Unit)? = null

          fun <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">Example</error>(content: @Composable () -> Unit) {
              x = content
              <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">content</error>()
          }
          """
    )
  }

  @Test
  fun testComposableReporting025() {
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable
          fun Leaf() {}

          @Composable
          fun foo() {
              listOf(1,2,3,4,5).forEach { Leaf() }
          }
          """
    )
  }

  @Test
  fun testComposableReporting026() {
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable
          fun Leaf() {}

          @Composable
          fun Group(content: @Composable () -> Unit) { content() }

          @Composable
          fun foo() {
              Group {
                  Leaf()
              }
          }
          """
    )
  }

  @Test
  fun testComposableReporting027() {
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable
          fun Leaf() {}

          @Composable
          fun Group(content: @Composable () -> Unit) { content() }

          @Composable
          fun foo() {
              Group {
                  listOf(1,2,3).forEach {
                      Leaf()
                  }
              }
          }
          """
    )
  }

  @Test
  fun testComposableReporting028() {
    val errorDescription =
      if (!KotlinPluginModeProvider.isK2Mode()) {
        "[TYPE_MISMATCH] Type inference failed. Expected type mismatch: inferred type is @Composable () -> Unit but () -> Unit was expected"
      } else {
        "[INITIALIZER_TYPE_MISMATCH] Initializer type mismatch: expected 'kotlin.Function0<kotlin.Unit>', actual '@Composable() androidx.compose.runtime.internal.ComposableFunction0<kotlin.Unit>'."
      }
    doTest(
      """
          import androidx.compose.runtime.*;

          fun foo(v: @Composable ()->Unit) {
              val myVariable: ()->Unit = <error descr="$errorDescription">v</error>
              myVariable()
          }
          """
    )
  }

  @Test
  fun testComposableReporting030() {
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable
          fun foo() {
              val myVariable: @Composable ()->Unit = {};
              myVariable()
          }
          """
    )
  }

  @Test
  fun testComposableReporting032() {
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable
          fun MyComposable(children: @Composable ()->Unit) { children() }

          @Composable
          fun Leaf() {}

          @Composable
          fun foo() {
              MyComposable { Leaf() }
          }
          """
    )
  }

  @Test
  fun testComposableReporting033() {
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable
          fun MyComposable(children: @Composable ()->Unit) { children() }

          @Composable
          fun Leaf() {}

          @Composable
          fun foo() {
              MyComposable(children={ Leaf() })
          }
          """
    )
  }

  @Test
  fun testComposableReporting034() {
    val initializerTypeMismatchMessage =
      if (!KotlinPluginModeProvider.isK2Mode()) {
        "[TYPE_MISMATCH] Type inference failed. Expected type mismatch: inferred type is () -> Unit but @Composable () -> Unit was expected"
      } else {
        "[INITIALIZER_TYPE_MISMATCH] Initializer type mismatch: expected '@Composable() androidx.compose.runtime.internal.ComposableFunction0<kotlin.Unit>', actual 'kotlin.Function0<kotlin.Unit>'."
      }
    val argumentTypeMismatchMessage =
      if (!KotlinPluginModeProvider.isK2Mode()) {
        "[TYPE_MISMATCH] Type inference failed. Expected type mismatch: inferred type is @Composable () -> Unit but () -> Unit was expected"
      } else {
        "[ARGUMENT_TYPE_MISMATCH] Argument type mismatch: actual type is '@Composable() androidx.compose.runtime.internal.ComposableFunction0<kotlin.Unit>', but 'kotlin.Function0<kotlin.Unit>' was expected."
      }
    doTest(
      """
        import androidx.compose.runtime.*;

        fun identity(f: ()->Unit): ()->Unit { return f; }

        @Composable
        fun test(f: @Composable ()->Unit) {
            val f2: @Composable ()->Unit = <error descr="$initializerTypeMismatchMessage">identity (<error descr="$argumentTypeMismatchMessage">f</error>)</error>;
            f2()
        }
        """
    )
  }

  @Test
  fun testComposableReporting035() {
    doTest(
      """
          import androidx.compose.runtime.*

          @Composable
          fun Foo(x: String) {
              @Composable operator fun String.invoke() {}
              x()
          }
          """
    )
  }

  @Test
  fun testComposableReporting039() {
    doTest(
      """
          import androidx.compose.runtime.*

          fun composeInto(l: @Composable ()->Unit) { print(l) }

          fun Foo() {
              composeInto {
                  Baz()
              }
          }

          fun Bar() {
              Foo()
          }
          @Composable fun Baz() {}
          """
    )
  }

  @Test
  fun testComposableReporting041() {
    doTest(
      """
          import androidx.compose.runtime.*

          typealias COMPOSABLE_UNIT_LAMBDA = @Composable () -> Unit

          @Composable
          fun ComposeWrapperComposable(children: COMPOSABLE_UNIT_LAMBDA) {
              MyComposeWrapper {
                  children()
              }
          }

          @Composable fun MyComposeWrapper(children: COMPOSABLE_UNIT_LAMBDA) {
              print(children.hashCode())
          }
          """
    )
  }

  @Test
  fun testComposableReporting043() {
    doTest(
      """
          import androidx.compose.runtime.*

          @Composable
          fun FancyButton() {}

          fun <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">Noise</error>() {
              <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">FancyButton</error>()
          }
          """
    )
  }

  @Test
  fun testComposableReporting044() {
    doTest(
      """
              import androidx.compose.runtime.*

              typealias UNIT_LAMBDA = () -> Unit

              @Composable
              fun FancyButton() {}

              @Composable
              fun Noise() {
                  FancyButton()
              }
          """
    )
  }

  @Test
  fun testComposableReporting045() {
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable
          fun foo() {
              val bar = @Composable {}
              bar()
          }
          """
    )
  }

  @Test
  fun testComposableReporting048() {
    // Type inference for nullable @Composable lambdas, with a nullable default value
    doTest(
      """
          import androidx.compose.runtime.*

          val lambda: @Composable (() -> Unit)? = null

          @Composable
          fun Foo() {
              Bar()
              Bar(lambda)
              Bar(null)
              Bar {}
          }

          @Composable
          fun Bar(child: @Composable (() -> Unit)? = null) {
              child?.invoke()
          }
          """
    )
  }

  @Test
  fun testComposableReporting049() {
    doTest(
      """
          import androidx.compose.runtime.*
          fun foo(<error descr="[WRONG_ANNOTATION_TARGET] This annotation is not applicable to target 'value parameter'${
        if (KotlinPluginModeProvider.isK2Mode()) "." else ""
      }">@Composable</error> bar: ()->Unit) {
              println(bar)
          }
          """
    )
  }

  @Test
  fun testComposableReporting050() {
    doTest(
      """
          import androidx.compose.runtime.*;

          val foo: Int
              @Composable get() = 123

          fun <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">App</error>() {
              <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">foo</error>
          }
          """
    )
  }

  @Test
  fun testComposableReporting051() {
    doTest(
      """
        import androidx.compose.runtime.*;

        class A {
            <error descr="${wrongAnnotationTargetError("member property without backing field or delegate")}">@Composable</error> val bar get() = 123
        }

        <error descr="${wrongAnnotationTargetError("top level property without backing field or delegate")}">@Composable</error> val A.bam get() = 123

        @Composable
        fun App() {
            val a = A()
            a.bar
            a.bam
            with(a) {
                bar
                bam
            }
        }
        """
    )
  }

  @Test
  fun testComposableReporting052() {
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable fun Foo() {}

          val <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">bam</error>: Int get() {
              <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">Foo</error>()
              return 123
          }
          """
    )
  }

  @Test
  fun testComposableReporting053() {
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable fun foo(): Int = 123

          fun <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">App</error>() {
              val x = <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">foo</error>()
              print(x)
          }
          """
    )
  }

  @Test
  fun testComposableReporting054() {
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable fun Foo() {}

          val <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">y</error>: Any get() =
          <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">remember</error> { mutableStateOf(1) }

          fun App() {
              val x = object {
                val <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">a</error> get() =
                <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">remember</error> { mutableStateOf(2) }
                <error descr="${wrongAnnotationTargetError("member property without backing field or delegate")}">@Composable</error> val <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">c</error> get() = <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">remember</error> { mutableStateOf(4) }
                @Composable fun bar() { Foo() }
                fun <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">foo</error>() {
                  <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">Foo</error>()
                }
              }
              class Bar {
                val <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">b</error> get() =
                <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">remember</error> { mutableStateOf(6) }
                <error descr="${wrongAnnotationTargetError("member property without backing field or delegate")}">@Composable</error> val <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">c</error> get() = <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">remember</error> { mutableStateOf(7) }
              }
              fun <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">Bam</error>() {
                  <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">Foo</error>()
              }
              @Composable fun Boo() {
                  Foo()
              }
              print(x)
          }
          """
    )
  }

  @Test
  fun testComposableReporting055() {
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable fun Foo() {}

          @Composable fun App() {
              val x = object {
                val <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">a</error> get() = <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">remember</error> { mutableStateOf(2) }
                val c @Composable get() = remember { mutableStateOf(4) }
                fun <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">foo</error>() {
                  <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">Foo</error>()
                }
                @Composable fun bar() { Foo() }
              }
              class Bar {
                val <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">b</error> get() = <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">remember</error> { mutableStateOf(6) }
                val c @Composable get() = remember { mutableStateOf(7) }
              }
              fun <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">Bam</error>() {
                  <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">Foo</error>()
              }
              @Composable fun Boo() {
                  Foo()
              }
          }
          """
    )
  }

  @Test
  fun testComposableReporting057() {
    doTest(
      """
          import androidx.compose.runtime.*

          @Composable fun App() {
              val x = object {
                val b = remember { mutableStateOf(3) }
              }
              class Bar {
                val a = <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">remember</error> { mutableStateOf(5) }
              }
              print(x)
          }
          """
    )
  }

  @Test
  fun testDisallowComposableCallPropagation() =
    doTest(
      """
        import androidx.compose.runtime.*
        class Foo
        @Composable inline fun a(block1: @DisallowComposableCalls () -> Foo): Foo {
            return block1()
        }
        @Composable inline fun b(<error descr="[MISSING_DISALLOW_COMPOSABLE_CALLS_ANNOTATION] Parameter block2 cannot be inlined inside of lambda argument block1 of a without also being annotated with @DisallowComposableCalls">block2: () -> Foo</error>): Foo {
          return a { block2() }
        }
        @Composable inline fun c(block2: @DisallowComposableCalls () -> Foo): Foo {
          return a { block2() }
        }
    """
    )

  @Test
  fun testDisallowComposableCallPropagationWithInvoke() {
    // The frontend distinguishes between implicit and explicit invokes, which is why this test
    // fails in K1.
    assumeTrue(KotlinPluginModeProvider.isK2Mode())
    doTest(
      """
            import androidx.compose.runtime.*
            class Foo
            @Composable inline fun a(block1: @DisallowComposableCalls () -> Foo): Foo {
                return block1()
            }
            @Composable inline fun b(<error descr="[MISSING_DISALLOW_COMPOSABLE_CALLS_ANNOTATION] Parameter block2 cannot be inlined inside of lambda argument block1 of a without also being annotated with @DisallowComposableCalls" textAttributesKey="ERRORS_ATTRIBUTES">block2: () -> Foo</error>): Foo {
              return a { block2.invoke() }
            }
            @Composable inline fun c(block2: @DisallowComposableCalls () -> Foo): Foo {
              return a { block2.invoke() }
            }
        """
    )
  }

  @Test
  fun testComposableLambdaToAll() =
    doTest(
      """
        import androidx.compose.runtime.*

        fun foo() {
            val lambda = @Composable { }
            println(lambda)  // println accepts Any, verify no type mismatach.
        }
    """
    )

  @Test
  fun testReadOnlyComposablePropagation() =
    doTest(
      """
        import androidx.compose.runtime.*

        @Composable @ReadOnlyComposable
        fun readOnly(): Int = 10
        val readonlyVal: Int
            @Composable @ReadOnlyComposable get() = 10

        @Composable
        fun normal(): Int = 10
        val normalVal: Int
            @Composable get() = 10

        @Composable
        fun test1() {
            print(readOnly())
            print(readonlyVal)
        }

        @Composable @ReadOnlyComposable
        fun test2() {
            print(readOnly())
            print(readonlyVal)
        }

        @Composable
        fun test3() {
            print(readOnly())
            print(normal())
            print(readonlyVal)
            print(normalVal)
        }

        @Composable @ReadOnlyComposable
        fun test4() {
            print(readOnly())
            print(<error descr="[NONREADONLY_CALL_IN_READONLY_COMPOSABLE] Composables marked with @ReadOnlyComposable can only call other @ReadOnlyComposable composables">normal</error>())
            print(readonlyVal)
            print(<error descr="[NONREADONLY_CALL_IN_READONLY_COMPOSABLE] Composables marked with @ReadOnlyComposable can only call other @ReadOnlyComposable composables">normalVal</error>)
        }

        val test5: Int
            @Composable
            get() {
                print(readOnly())
                print(readonlyVal)
                return 10
            }

        val test6: Int
            @Composable @ReadOnlyComposable
            get() {
                print(readOnly())
                print(readonlyVal)
                return 10
            }

        val test7: Int
            @Composable
            get() {
                print(readOnly())
                print(normal())
                print(readonlyVal)
                print(normalVal)
                return 10
            }

        val test8: Int
            @Composable @ReadOnlyComposable
            get() {
                print(readOnly())
                print(<error descr="[NONREADONLY_CALL_IN_READONLY_COMPOSABLE] Composables marked with @ReadOnlyComposable can only call other @ReadOnlyComposable composables">normal</error>())
                print(readonlyVal)
                print(<error descr="[NONREADONLY_CALL_IN_READONLY_COMPOSABLE] Composables marked with @ReadOnlyComposable can only call other @ReadOnlyComposable composables">normalVal</error>)
                return 10
            }
    """
    )

  @Test
  fun testNothingAsAValidComposableFunctionBody() =
    doTest(
      """
        import androidx.compose.runtime.*

        val test1: @Composable () -> Unit = TODO()

        @Composable
        fun Test2(): Unit = TODO()

        @Composable
        fun Wrapper(content: @Composable () -> Unit) = content()

        @Composable
        fun Test3() {
            Wrapper {
                TODO()
            }
        }
    """
    )

  @Test
  fun testComposableValueOperator() {
    val testCode =
      """
      import androidx.compose.runtime.Composable
      import kotlin.reflect.KProperty

      class Foo
      class FooDelegate {
          @Composable
          operator fun getValue(thisObj: Any?, property: KProperty<*>) {}
          @Composable
          operator fun <error descr="[COMPOSE_INVALID_DELEGATE] Composable setValue operator is not currently supported." textAttributesKey="ERRORS_ATTRIBUTES">setValue</error>(thisObj: Any?, property: KProperty<*>, value: Any) {}
      }
      @Composable operator fun Foo.getValue(thisObj: Any?, property: KProperty<*>) {}
      @Composable operator fun Foo.<error descr="[COMPOSE_INVALID_DELEGATE] Composable setValue operator is not currently supported." textAttributesKey="ERRORS_ATTRIBUTES">setValue</error>(thisObj: Any?, property: KProperty<*>, value: Any) {}

      fun <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation" textAttributesKey="ERRORS_ATTRIBUTES"><error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation" textAttributesKey="ERRORS_ATTRIBUTES"><error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation" textAttributesKey="ERRORS_ATTRIBUTES"><error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation" textAttributesKey="ERRORS_ATTRIBUTES">nonComposable</error></error></error></error>() {
          val fooValue = Foo()
          val foo by fooValue
          val fooDelegate by FooDelegate()
          var mutableFoo by <error descr="[COMPOSE_INVALID_DELEGATE] Composable setValue operator is not currently supported." textAttributesKey="ERRORS_ATTRIBUTES"><error descr="[COMPOSE_INVALID_DELEGATE] Composable setValue operator is not currently supported." textAttributesKey="ERRORS_ATTRIBUTES">fooValue</error></error>
          val bar = Bar()

          println(<error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function" textAttributesKey="ERRORS_ATTRIBUTES">foo</error>)
          println(<error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function" textAttributesKey="ERRORS_ATTRIBUTES">fooDelegate</error>)
          println(bar.<error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function" textAttributesKey="ERRORS_ATTRIBUTES">foo</error>)

          <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function" textAttributesKey="ERRORS_ATTRIBUTES">mutableFoo</error> = Unit
      }

      @Composable
      fun TestComposable() {
          val fooValue = Foo()
          val foo by fooValue
          val fooDelegate by FooDelegate()
          val bar = Bar()

          println(foo)
          println(fooDelegate)
          println(bar.foo)
      }

      class Bar {
          val <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation" textAttributesKey="ERRORS_ATTRIBUTES">foo</error> by Foo()

          @get:Composable
          val foo2 by Foo()
      }
      """
    if (KotlinPluginModeProvider.isK2Mode()) {
      // As explained in https://b.corp.google.com/issues/301340542#comment12, we have duplicated
      // errors because we have 3 different reasons to report the diagnostic. Technically, it is
      // not wrong, but it can be inconvenient to users.
      ExpectedHighlightingData.expectedDuplicatedHighlighting { doTest(testCode) }
    } else {
      doTest(testCode)
    }
  }

  @Test
  fun testComposableFunInterfaceInNonComposableFunction() {
    doTest(
      """
                import androidx.compose.runtime.Composable

                fun interface FunInterfaceWithComposable {
                    @Composable fun content()
                }

                fun Test1() {
                    val funInterfaceWithComposable = FunInterfaceWithComposable {
                        TODO()
                    }
                    println(funInterfaceWithComposable) // use it to avoid UNUSED warning
                }

                fun <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation" textAttributesKey="ERRORS_ATTRIBUTES">Test2</error>() {
                    val funInterfaceWithComposable = FunInterfaceWithComposable {
                        TODO()
                    }
                    funInterfaceWithComposable.<error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function" textAttributesKey="ERRORS_ATTRIBUTES">content</error>()
                }
            """
    )
  }

  @Test
  fun testComposableCallHighlighting() =
    doTest(
      """
    import androidx.compose.runtime.*
    fun notC() { }
    @Composable fun C() { }
    @Composable fun C1(a: Int) { }
    @Composable fun C2(a: Int, lambdaC: @Composable () -> Unit) {
      lambdaC()
    }
    @Composable <warning descr="$nothingToInline">inline</warning> fun InlineC() {}
    inline fun InlineNC(lambda: () -> Unit) { lambda() }

    @Composable fun C3() {
        InlineNC {
            C()
            C1(1)
        }
        C1(2)
    }

    @Composable fun C4() {
        C1(3)
        notC()
        C2(3) {
          notC()
          InlineC()
        }
    }
    """
    ) { highlights ->
      assertEquals(
        """
        lambdaC@8
        C@15
        C1@16
        C1@18
        C1@22
        C2@24
        InlineC@26
      """
          .trimIndent(),
        highlights
          .filter { (highlight, _) -> highlight.type == COMPOSABLE_CALL_TEXT_TYPE }
          .joinToString("\n") { (highlight, line) -> "${highlight.text}@$line" },
      )
    }

  @Test
  fun testComposableCallInsideNonComposableNonInlinedLambda() {
    val errorMessage =
      if (!KotlinPluginModeProvider.isK2Mode()) {
        "<</error><error descr=\"[DEBUG] Reference is not resolved to anything, but is not marked unresolved\">!</error><error descr=\"[UNRESOLVED_REFERENCE] Unresolved reference: COMPOSABLE_INVOCATION\">"
      } else {
        "<</error>!<error descr=\"[UNRESOLVED_REFERENCE] Unresolved reference 'COMPOSABLE_INVOCATION'.\">"
      }
    doTest(
      """
    import androidx.compose.runtime.Composable

    @Composable fun ComposableFunction() {}

    fun functionThatTakesALambda(content: () -> Unit) { content() }

    fun NonComposableFunction() {
        functionThatTakesALambda {
            <error descr="Expecting an element">${errorMessage}COMPOSABLE_INVOCATION</error><error descr="Unexpected tokens (use ';' to separate expressions on the same line)">!>ComposableFunction<!>()</error>  // invocation
        }
    }
    """
    )
  }

  @Before
  fun setUp() {
    (androidProject.fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
  }
}
