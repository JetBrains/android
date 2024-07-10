/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.rendering.classloading

import com.android.tools.rendering.classloading.test.sandboxing.MethodInterceptTest
import java.io.StringWriter
import kotlin.reflect.jvm.javaMethod
import org.bouncycastle.asn1.x500.style.RFC4519Style.owner
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.objectweb.asm.Type

class DoNotIntercept {
  fun noArgsInstanceMethod() {
    println("DoNotIntercept#noArgsInstanceMethod")
  }

  companion object {
    @JvmStatic
    fun noArgsStaticMethod() {
      println("DoNotIntercept#noArgsStaticMethod")
    }
  }
}

interface CallerInterface {
  fun testInstanceNoArgsCall()

  fun testStaticNoArgsCall()

  fun testInstanceArgsCall()

  fun testStaticArgsCall()
}

class Caller() : CallerInterface {
  override fun testInstanceNoArgsCall() {
    DoNotIntercept().noArgsInstanceMethod()
    MethodInterceptTest().noArgsInstanceMethod()
  }

  override fun testStaticNoArgsCall() {
    DoNotIntercept.noArgsStaticMethod()
    MethodInterceptTest.noArgsStaticMethod()
  }

  override fun testInstanceArgsCall() {
    // The method just contains a local variable and a try catch to exercise a bit more the complex
    // frame handling and ensuring that the intercept does not break the stackmap.
    val preexistingLocal = "String.Arg".replace(".", "")
    try {
      MethodInterceptTest().instanceMethod(123, 98765L, preexistingLocal)
    } catch (e: ClassNotFoundException) {
      println(preexistingLocal)
    }
    println("Intercept")
  }

  override fun testStaticArgsCall() {
    // The method just contains a local variable and a try catch to exercise a bit more the complex
    // frame handling and ensuring that the intercept does not break the stackmap.
    val preexistingLocal = "String.Arg".replace(".", "")
    try {
      MethodInterceptTest.staticMethod(123, 98765L, preexistingLocal)
    } catch (e: ClassNotFoundException) {
      call(e)
    }
  }

  companion object {
    fun call(t: Throwable) {
      t.printStackTrace()
    }
  }
}

class MethodInterceptTransformTest {
  /** [StringWriter] that stores the decompiled classes after they've been transformed. */
  private val afterTransformTrace = StringWriter()

  /** [StringWriter] that stores the decompiled classes before they've been transformed. */
  private val beforeTransformTrace = StringWriter()

  // This will log to the stdout logging information that might be useful debugging failures.
  // The logging only happens if the test fails.
  @get:Rule
  val onFailureRule =
    object : TestWatcher() {
      override fun failed(e: Throwable?, description: Description?) {
        super.failed(e, description)

        println("\n---- Classes before transformation ----")
        println(beforeTransformTrace)
        println("\n---- Classes after transformation ----")
        println(afterTransformTrace)
      }
    }

  @Before
  fun setUp() {
    Trampoline.callLog.clear()
  }

  @Test
  fun `check instance intercept`() {
    val testClassLoader =
      setupTestClassLoaderWithTransformation(
        mapOf("Test" to Caller::class.java),
        beforeTransformTrace,
        afterTransformTrace,
      ) { visitor ->
        MethodInterceptTransform(
          visitor,
          virtualTrampolineMethod = Trampoline::invoke.javaMethod!!,
          staticTrampolineMethod = Trampoline::invokeStatic.javaMethod!!,
          shouldIntercept = { className, _ ->
            className == Type.getInternalName(MethodInterceptTest::class.java)
          },
        )
      }

    val methodIntercept =
      testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as CallerInterface
    methodIntercept.testInstanceNoArgsCall()

    assertEquals(
      "VIRTUAL MethodInterceptTest@1 noArgsInstanceMethod null",
      Trampoline.callLog.toString().trim(),
    )

    Trampoline.callLog.clear()

    methodIntercept.testInstanceArgsCall()
    assertEquals(
      "VIRTUAL MethodInterceptTest@1 instanceMethod 123,98765,StringArg",
      Trampoline.callLog.toString().trim(),
    )
  }

  @Test
  fun `check static intercept`() {
    val testClassLoader =
      setupTestClassLoaderWithTransformation(
        mapOf("Test" to Caller::class.java),
        beforeTransformTrace,
        afterTransformTrace,
      ) { visitor ->
        MethodInterceptTransform(
          visitor,
          virtualTrampolineMethod = Trampoline::invoke.javaMethod!!,
          staticTrampolineMethod = Trampoline::invokeStatic.javaMethod!!,
          shouldIntercept = { className, _ ->
            className.startsWith(Type.getInternalName(MethodInterceptTest::class.java))
          },
        )
      }

    val methodIntercept =
      testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as CallerInterface
    methodIntercept.testStaticNoArgsCall()

    assertEquals(
      "STATIC com/android/tools/rendering/classloading/test/sandboxing/MethodInterceptTest noArgsStaticMethod null",
      Trampoline.callLog.toString().trim(),
    )

    Trampoline.callLog.clear()

    methodIntercept.testStaticArgsCall()
    assertEquals(
      "STATIC com/android/tools/rendering/classloading/test/sandboxing/MethodInterceptTest staticMethod 123,98765,StringArg",
      Trampoline.callLog.toString().trim(),
    )
  }

  @Test
  fun `check broken trampoline`() {
    try {
      setupTestClassLoaderWithTransformation(
        mapOf("Test" to Caller::class.java),
        beforeTransformTrace,
        afterTransformTrace,
      ) { visitor ->
        MethodInterceptTransform(
          visitor,
          virtualTrampolineMethod = BrokenTrampoline::invoke.javaMethod!!,
          staticTrampolineMethod = Trampoline::invokeStatic.javaMethod!!,
          shouldIntercept = { className, _ ->
            className.startsWith(Type.getInternalName(MethodInterceptTest::class.java))
          },
        )
      }
    } catch (e: AssertionError) {
      assertEquals(
        "Virtual trampoline method descriptor must be (Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)V but was (Ljava/lang/String;[Ljava/lang/Object;)V",
        e.message,
      )
    }

    try {
      setupTestClassLoaderWithTransformation(
        mapOf("Test" to Caller::class.java),
        beforeTransformTrace,
        afterTransformTrace,
      ) { visitor ->
        MethodInterceptTransform(
          visitor,
          virtualTrampolineMethod = Trampoline::invoke.javaMethod!!,
          staticTrampolineMethod = BrokenTrampoline::invokeStatic.javaMethod!!,
          shouldIntercept = { className, _ ->
            className.startsWith(Type.getInternalName(MethodInterceptTest::class.java))
          },
        )
      }
    } catch (e: AssertionError) {
      assertEquals(
        "Static trampoline method descriptor must be (Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)V but was (Ljava/lang/String;Ljava/lang/String;)V",
        e.message,
      )
    }
  }

  object Trampoline {
    val callLog = StringBuilder()

    @JvmStatic
    fun invoke(owner: Any, method: String, params: Array<Any>?): Unit {
      callLog.appendLine("VIRTUAL $owner $method ${params?.joinToString(",")}")
    }

    @JvmStatic
    fun invokeStatic(ownerClass: String, method: String, params: Array<Any>?): Unit {
      callLog.appendLine("STATIC $ownerClass $method ${params?.joinToString(",")}")
    }
  }

  /** A trampoline with the wrong signatures. */
  object BrokenTrampoline {
    val callLog = StringBuilder()

    @JvmStatic fun invoke(method: String, params: Array<Any>?): Unit = error("Broken trampoline")

    @JvmStatic
    fun invokeStatic(ownerClass: String, method: String): Unit = error("Broken trampoline")
  }
}
