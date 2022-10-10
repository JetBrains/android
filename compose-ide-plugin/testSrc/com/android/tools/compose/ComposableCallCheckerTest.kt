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

import com.android.flags.junit.SetFlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.text.StringUtil
import junit.framework.Assert.assertEquals
import org.jetbrains.android.compose.stubComposeRuntime
import org.jetbrains.android.compose.stubKotlinStdlib
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [ComposeSampleResolutionService]
 */
class ComposableCallCheckerTest {
  @get:Rule
  val androidProject = AndroidProjectRule.inMemory()

  @get:Rule
  val flagRule = SetFlagRule(StudioFlags.COMPOSE_EDITOR_SUPPORT, true)

  @Test
  fun testCfromNC() = doTest(
    """
    import androidx.compose.runtime.*

    @Composable fun C() {}
    fun <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">NC</error>() { <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">C</error>() }

    """
  )

  @Test
  fun testNCfromC() = doTest(
    """
    import androidx.compose.runtime.*

    fun NC() {}
    @Composable fun C() { NC() }
    """
  )

  @Test
  fun testCfromC() = doTest(
    """
        import androidx.compose.runtime.*

        @Composable fun C() {}
        @Composable fun C2() { C() }
    """
  )

  @Test
  fun testCinCLambdaArg() = doTest(
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
  fun testCinInlinedNCLambdaArg() = doTest(
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
  fun testCinLambdaArgOfNC() = doTest(
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
  fun testCinLambdaArgOfC() = doTest(
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
  fun testCinCPropGetter() = doTest(
    """
        import androidx.compose.runtime.*
        @Composable fun C(): Int { return 123 }
        val cProp: Int @Composable get() = C()
    """
  )

  @Test
  fun testCinNCPropGetter() = doTest(
    """
    import androidx.compose.runtime.*
    @Composable fun C(): Int { return 123 }
    val <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">ncProp</error>: Int get() = <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">C</error>()
    """
  )

  @Test
  fun testCinTopLevelInitializer() = doTest(
    """
    import androidx.compose.runtime.*
    @Composable fun C(): Int { return 123 }
    val ncProp: Int = <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">C</error>()
    <error descr="[WRONG_ANNOTATION_TARGET] This annotation is not applicable to target 'top level property with backing field'">@Composable</error> val cProp: Int = <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">C</error>()
    """
  )

  @Test
  fun testCTypeAlias() = doTest(
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
  fun testPreventedCaptureOnInlineLambda() = doTest(
    """
    import androidx.compose.runtime.*

    fun cond(): Boolean = true

    @Composable inline fun A(
        lambda: @DisallowComposableCalls () -> Unit
    ) { lambda() }
    @Composable fun B() {}

    @Composable fun C() {
        A { <error descr="[CAPTURED_COMPOSABLE_INVOCATION] Composable calls are not allowed inside the lambda parameter of inline fun A(lambda: () -> Unit): Unit">B</error>() }
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
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable
          fun Leaf() {}

          fun foo() {
              val myVariable: ()->Unit = <error descr="[TYPE_MISMATCH] Type inference failed. Expected type mismatch: inferred type is @Composable () -> Unit but () -> Unit was expected">@Composable { Leaf() }</error>
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
    doTest(
      """
          import androidx.compose.runtime.*;

          fun foo(v: @Composable ()->Unit) {
              val myVariable: ()->Unit = <error descr="[TYPE_MISMATCH] Type inference failed. Expected type mismatch: inferred type is @Composable () -> Unit but () -> Unit was expected">v</error>
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
    doTest(
      """
          import androidx.compose.runtime.*;

          fun identity(f: ()->Unit): ()->Unit { return f; }

          @Composable
          fun test(f: @Composable ()->Unit) {
              val f2: @Composable ()->Unit = <error descr="[TYPE_MISMATCH] Type inference failed. Expected type mismatch: inferred type is () -> Unit but @Composable () -> Unit was expected">identity (<error descr="[TYPE_MISMATCH] Type inference failed. Expected type mismatch: inferred type is @Composable () -> Unit but () -> Unit was expected">f</error>)</error>;
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

          fun composeInto(l: @Composable ()->Unit) { print(<error descr="[TYPE_MISMATCH] Type inference failed. Expected type mismatch: inferred type is @Composable () -> Unit but Any? was expected">l</error>) }

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
          fun foo(<error descr="[WRONG_ANNOTATION_TARGET] This annotation is not applicable to target 'value parameter'">@Composable</error> bar: ()->Unit) {
              print(bar)
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
              <error descr="[WRONG_ANNOTATION_TARGET] This annotation is not applicable to target 'member property without backing field or delegate'">@Composable</error> val bar get() = 123
          }

          <error descr="[WRONG_ANNOTATION_TARGET] This annotation is not applicable to target 'top level property without backing field or delegate'">@Composable</error> val A.bam get() = 123

          @Composable
          fun App() {
              val a = A()
              a.bar
              a.bam
              <error descr="[UNRESOLVED_REFERENCE] Unresolved reference: with">with</error>(a) {
                  <error descr="[UNRESOLVED_REFERENCE] Unresolved reference: bar">bar</error>
                  <error descr="[UNRESOLVED_REFERENCE] Unresolved reference: bam">bam</error>
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
                <error descr="[WRONG_ANNOTATION_TARGET] This annotation is not applicable to target 'member property without backing field or delegate'">@Composable</error> val <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">c</error> get() = <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">remember</error> { mutableStateOf(4) }
                @Composable fun bar() { Foo() }
                fun <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">foo</error>() {
                  <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">Foo</error>()
                }
              }
              class Bar {
                val <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">b</error> get() =
                <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">remember</error> { mutableStateOf(6) }
                <error descr="[WRONG_ANNOTATION_TARGET] This annotation is not applicable to target 'member property without backing field or delegate'">@Composable</error> val <error descr="[COMPOSABLE_EXPECTED] Functions which invoke @Composable functions must be marked with the @Composable annotation">c</error> get() = <error descr="[COMPOSABLE_INVOCATION] @Composable invocations can only happen from the context of a @Composable function">remember</error> { mutableStateOf(7) }
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
              val <warning descr="[UNUSED_VARIABLE] Variable 'x' is never used">x</warning> = object {
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
  fun testTryCatchReporting001() {
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable fun foo() { }

          @Composable fun bar() {
              <error descr="[ILLEGAL_TRY_CATCH_AROUND_COMPOSABLE] Try catch is not supported around composable function invocations.">try</error> {
                  foo()
              } catch(e: Exception) {
              }
          }
          """
    )
  }

  @Test
  fun testTryCatchReporting002() {
    doTest(
      """
          import androidx.compose.runtime.*;

          fun foo() { }

          @Composable fun bar() {
              try {
                  foo()
              } catch(e: Exception) {
              }
          }
          """
    )
  }

  @Test
  fun testTryCatchReporting003() {
    doTest(
      """
          import androidx.compose.runtime.*;

          @Composable fun foo() { }

          @Composable fun bar() {
              try {
              } catch(e: Exception) {
                  foo()
              } finally {
                  foo()
              }
          }
          """
    )
  }

  @Test
  fun testTryCatchReporting005() {
    doTest(
      """
          import androidx.compose.runtime.*

          var globalContent = @Composable {}
          fun setContent(content: @Composable () -> Unit) {
              globalContent = content
          }
          @Composable fun A() {}

          fun test() {
              try {
                  setContent {
                      A()
                  }
              } finally {
                  print("done")
              }
          }
          """
    )
  }


  @Test
  fun testDisallowComposableCallPropagation() = doTest(
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
  fun testReadOnlyComposablePropagation() = doTest(
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
  fun testComposableCallHighlighting() = doTest(
    """
    import androidx.compose.runtime.*
    fun notC() { }
    @Composable fun C() { }
    @Composable fun C1(<warning descr="[UNUSED_PARAMETER] Parameter 'a' is never used">a</warning>: Int) { }
    @Composable fun C2(<warning descr="[UNUSED_PARAMETER] Parameter 'a' is never used">a</warning>: Int, lambdaC: @Composable () -> Unit) { 
      lambdaC()
    }
    @Composable <warning descr="[NOTHING_TO_INLINE] Expected performance impact from inlining is insignificant. Inlining works best for functions with parameters of functional types">inline</warning> fun InlineC() {}
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
      """.trimIndent(),
      highlights
        .filter { (highlight, _) ->
          highlight.severity == HighlightSeverity.INFORMATION && highlight.forcedTextAttributesKey == ComposableHighlighterExtension.COMPOSABLE_CALL_TEXT_ATTRIBUTES_KEY
        }
        .joinToString("\n") { (highlight, line) -> "${highlight.text}@$line" })
  }

  private fun doTest(expectedText: String,
                     verifyHighlights: ((List<Pair<HighlightInfo, Int>>) -> Unit)? = null): Unit = androidProject.fixture.run {
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
    verifyHighlights?.let {
      it(doHighlighting().map { it to StringUtil.offsetToLineNumber(file.text, it.actualStartOffset) })
    }
  }

  @Before
  fun setUp() {
    (androidProject.fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
  }
}