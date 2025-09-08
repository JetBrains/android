/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

import com.android.ddmlib.internal.FakeAdbTestRule
import com.android.tools.idea.run.deployment.liveedit.analysis.createKtFile
import com.android.tools.idea.run.deployment.liveedit.analysis.disableLiveEdit
import com.android.tools.idea.run.deployment.liveedit.analysis.enableLiveEdit
import com.android.tools.idea.run.deployment.liveedit.analysis.postDeploymentStateCompile
import com.android.tools.idea.testing.AndroidProjectRule
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * This class acts as a canonical source-of-truth as to what Kotlin/Compose language features we
 * treat as support classes (new instances are proxy objects, minimal restrictions on changes/diffs)
 * and which classes we treat as normal classes.
 */
@RunWith(JUnit4::class)
class SupportClassTest {
  private var projectRule = AndroidProjectRule.inMemory().withKotlin()

  // We don't need ADB in these tests. However, disableLiveEdit() or endableLiveEdit() does trigger calls to the AdbDebugBridge
  // so not having that available causes a NullPointerException when we call it.
  private val fakeAdb: FakeAdbTestRule = FakeAdbTestRule("30")
  @get:Rule
  val chain = RuleChain.outerRule(projectRule).around(fakeAdb)

  @Before
  fun setUp() {
    setUpComposeInProjectFixture(projectRule)
    disableLiveEdit()

    // Create mocks for the kotlin.jvm to avoid having to bring in the whole dependency
    projectRule.fixture.configureByText("JvmName.kt", "package kotlin.jvm\n" +
                                                      "@Target(AnnotationTarget.FILE)\n" +
                                                      "public annotation class JvmName(val name: String)\n")

    projectRule.fixture.configureByText("JvmMultifileClass.kt", "package kotlin.jvm\n" +
                                                                "@Target(AnnotationTarget.FILE)\n" +
                                                                "public annotation class JvmMultifileClass()")
  }

  @After
  fun tearDown() {
    enableLiveEdit()
  }

  @Test
  fun `lambdas are support classes`() {
    val file = projectRule.createKtFile("File.kt", """
      val x = { i: Int -> i + 1 }
      class A {
        val y = { a: String, b: String -> a + b }
      }
      fun f() {
        val z = {
          val z1 = { "hello" }
          z1()
        }
      }
    """)
    val output = projectRule.postDeploymentStateCompile(file)
    Assert.assertEquals(2, output.classesMap.size)
    Assert.assertNotNull(output.classesMap["A"])
    Assert.assertNotNull(output.classesMap["FileKt"])

    Assert.assertEquals(4, output.supportClassesMap.size)
    Assert.assertNotNull(output.supportClassesMap["A\$y$1"])
    Assert.assertNotNull(output.supportClassesMap["FileKt\$f\$z$1"])
    Assert.assertNotNull(output.supportClassesMap["FileKt\$f\$z$1\$z1$1"])
    Assert.assertNotNull(output.supportClassesMap["FileKt\$x$1"])
  }

  @Test
  fun `SAM classes are support classes`() {
    val file = projectRule.createKtFile("File.kt", """
      fun interface I {
        fun f(): Int
      } 
      fun g(): Int {
        val x = I { 100 }
        return x.f()
      }
    """)
    val output = projectRule.postDeploymentStateCompile(file)
    Assert.assertEquals(2, output.classesMap.size)
    Assert.assertNotNull(output.classesMap["I"])
    Assert.assertNotNull(output.classesMap["FileKt"])
    Assert.assertEquals(1, output.supportClassesMap.size)
    Assert.assertNotNull(output.supportClassesMap["FileKt\$g\$x$1"])
  }

  @Test
  fun `generic SAM classes are support classes`() {
    val file = projectRule.createKtFile("File.kt", """
      fun interface Observer<T> {
        fun onChanged(value: T)
      }

      class Watchable<T> {
        fun <T> callObserver(value: T, o: Observer<T>) {
          o.onChanged(value)
        }
      }

      fun main() {
        val x = Watchable<String>()
        x.callObserver("hello") { println(it) }
      }
    """)
    val output = projectRule.postDeploymentStateCompile(file)
    Assert.assertEquals(3, output.classesMap.size)
    Assert.assertNotNull(output.classesMap["Observer"])
    Assert.assertNotNull(output.classesMap["Watchable"])
    Assert.assertNotNull(output.classesMap["FileKt"])
    Assert.assertEquals(1, output.supportClassesMap.size)
    Assert.assertNotNull(output.supportClassesMap["FileKt\$main$1"])
  }

  @Test
  fun `anonymous functions are support classes`() {
    val file = projectRule.createKtFile("File.kt", """
      val x = fun(s: String): Int { return s.length }
      fun f() {
        val y = fun(): Int { return 0 }
      }
    """)
    val output = projectRule.postDeploymentStateCompile(file)
    Assert.assertEquals(1, output.classesMap.size)
    Assert.assertNotNull(output.classesMap["FileKt"])
    Assert.assertEquals(2, output.supportClassesMap.size)
    Assert.assertNotNull(output.supportClassesMap["FileKt\$x$1"])
    Assert.assertNotNull(output.supportClassesMap["FileKt\$f\$y$1"])

    // Much like lambdas, anonymous functions are just function literals and produce similar
    // compiler output: https://kotlinlang.org/docs/lambdas.html#instantiating-a-function-type
    val x = output.irClasses.single { it.name == "FileKt\$x$1" }
    val y = output.irClasses.single { it.name == "FileKt\$f\$y$1" }
    Assert.assertEquals("kotlin/jvm/internal/Lambda", x.superName)
    Assert.assertEquals("kotlin/jvm/internal/Lambda", y.superName)
    Assert.assertEquals("kotlin/jvm/functions/Function1", x.interfaces.single())
    Assert.assertEquals("kotlin/jvm/functions/Function0", y.interfaces.single())
  }

  // This simply means that users can add/remove implementations from interfaces without being
  // flagged for adding/removing a method. Realistically, this isn't useful until we support adding
  // or removing classes/methods, but it's correct behavior since we *can* support this as-is.
  @Test
  fun `interface default implementations are support classes`() {
    val file = projectRule.createKtFile("File.kt", """
      interface I {
        fun a()
        fun b() {}
      }
    """)
    val output = projectRule.postDeploymentStateCompile(file)
    Assert.assertEquals(1, output.classesMap.size)
    Assert.assertNotNull(output.classesMap["I"])
    Assert.assertEquals(1, output.supportClassesMap.size)
    Assert.assertNotNull(output.supportClassesMap["I\$DefaultImpls"])

  }

  @Test
  fun `Compose lambdas and singletons are support classes`() {
    val file = projectRule.createKtFile("File.kt", """
      import androidx.compose.runtime.Composable
      @Composable
      fun f(arg: Int) {}

      @Composable
      fun g(arg: @Composable () -> Unit) {
        arg()
      }

      @Composable
      fun h(arg: Int) {
        g {
          f(0)
        }
        g {
          f(arg)
        }
      }""")
    val output = projectRule.postDeploymentStateCompile(file)
    Assert.assertEquals(1, output.classesMap.size)
    Assert.assertNotNull(output.classesMap["FileKt"])
    Assert.assertEquals(6, output.supportClassesMap.size)
    Assert.assertNotNull(output.supportClassesMap["ComposableSingletons\$FileKt"])

    // @Composable singleton lambda for { f(0) }
    Assert.assertNotNull(output.supportClassesMap["ComposableSingletons\$FileKt\$lambda$626702009$1"])

    // @Composable lambda for { f(arg) }
    Assert.assertNotNull(output.supportClassesMap["FileKt\$h$1"])

    // Restart lambdas for f(), g(), h() [h restart lambda is treated as 2nd lambda in h()]
    Assert.assertNotNull(output.supportClassesMap["FileKt\$f$1"])
    Assert.assertNotNull(output.supportClassesMap["FileKt\$g$1"])
    Assert.assertNotNull(output.supportClassesMap["FileKt\$h$2"])
  }

  @Test
  fun `facade classes are NOT support classes`() {
    val file = projectRule.createKtFile("File.kt", """
      fun f() = 0
    """)
    val output = projectRule.postDeploymentStateCompile(file)
    Assert.assertEquals(1, output.classesMap.size)
    Assert.assertNotNull(output.classesMap["FileKt"])
    Assert.assertTrue(output.supportClassesMap.isEmpty())
  }

  @Test
  fun `nested classes are NOT support classes`() {
    val file = projectRule.createKtFile("File.kt", """
      class A {
        class B() {
          class C() {}
        }
      }
    """)
    val output = projectRule.postDeploymentStateCompile(file)
    Assert.assertEquals(3, output.classesMap.size)
    Assert.assertNotNull(output.classesMap["A"])
    Assert.assertNotNull(output.classesMap["A\$B"])
    Assert.assertNotNull(output.classesMap["A\$B\$C"])
    Assert.assertTrue(output.supportClassesMap.isEmpty())
  }

  @Test
  fun `objects are NOT support classes`() {
    val file = projectRule.createKtFile("File.kt", """
      object A {
        object B {
          object C {}
        }
        class D {
          object E {}
        }
      }
    """)
    val output = projectRule.postDeploymentStateCompile(file)
    Assert.assertEquals(5, output.classesMap.size)
    Assert.assertNotNull(output.classesMap["A"])
    Assert.assertNotNull(output.classesMap["A\$B"])
    Assert.assertNotNull(output.classesMap["A\$B\$C"])
    Assert.assertNotNull(output.classesMap["A\$D"])
    Assert.assertNotNull(output.classesMap["A\$D\$E"])
    Assert.assertTrue(output.supportClassesMap.isEmpty())
  }

  @Test
  fun `object literals are NOT support classes`() {
    val file = projectRule.createKtFile("File.kt", """
      interface I
      val x = object: I {}
    """)
    val output = projectRule.postDeploymentStateCompile(file)
    Assert.assertEquals(3, output.classesMap.size)
    Assert.assertNotNull(output.classesMap["FileKt"])
    Assert.assertNotNull(output.classesMap["I"])
    Assert.assertNotNull(output.classesMap["FileKt\$x$1"])
    Assert.assertTrue(output.supportClassesMap.isEmpty())
  }

  @Test
  fun `companion objects are NOT support classes`() {
    val file = projectRule.createKtFile("File.kt", """
      class A {
        companion object {
          fun f() {}
        }
      }
    """)
    val output = projectRule.postDeploymentStateCompile(file)
    Assert.assertEquals(2, output.classesMap.size)
    Assert.assertNotNull(output.classesMap["A"])
    Assert.assertNotNull(output.classesMap["A\$Companion"])
    Assert.assertTrue(output.supportClassesMap.isEmpty())
  }

  @Test
  fun `local classes are NOT support classes`() {
    val file = projectRule.createKtFile("File.kt", """
        class A() {
          fun f() {
            class B {}
          }
        }
        fun g() {
          class C {}
        }
      """)
    val output = projectRule.postDeploymentStateCompile(file)
    Assert.assertEquals(4, output.classesMap.size)
    Assert.assertNotNull(output.classesMap["A"])
    Assert.assertNotNull(output.classesMap["A\$f\$B"])
    Assert.assertNotNull(output.classesMap["FileKt"])
    Assert.assertNotNull(output.classesMap["FileKt\$g\$C"])
    Assert.assertTrue(output.supportClassesMap.isEmpty())
  }
}
