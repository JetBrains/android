/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.rendering.FontsContractCompatBridge
import java.io.StringWriter
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FontsContractCompatTransformTest {
  private val afterTransformTrace = StringWriter()
  private val beforeTransformTrace = StringWriter()

  @Suppress("unused")
  class FakeFontsContractCompat {
    companion object {
      @JvmStatic
      fun fetchFonts(context: Any?, cancellationSignal: Any?, request: Any?): Any {
        return "original_result"
      }

      @JvmStatic
      fun requestFont(
        context: Any?,
        request: Any?,
        style: Int,
        isBlockingFetch: Boolean,
        timeout: Int,
        handler: Any?,
        callback: Any?,
      ): Any {
        var temp = 100
        if (style > 0) {
          temp += style
        } else {
          temp -= 1
        }
        return "original_request_font_result_$temp"
      }

      @JvmStatic
      fun requestFont(context: Any?, request: Any?, callback: Any?, handler: Any?) {
        var temp = 200
        if (context != null) {
          temp += 50
        }
        println("void_request_font_$temp")
      }
    }
  }

  @Test
  fun testFetchFontsTransformed() {
    val testClassLoader =
      setupTestClassLoaderWithTransformation(
        mapOf("androidx/core/provider/FontsContractCompat" to FakeFontsContractCompat::class.java),
        beforeTransformTrace,
        afterTransformTrace,
      ) { visitor ->
        FontsContractCompatTransform(visitor)
      }

    val transformedClass = testClassLoader.loadClass("androidx/core/provider/FontsContractCompat")
    val method = transformedClass.getMethod("fetchFonts", Any::class.java, Any::class.java, Any::class.java)

    val result = method.invoke(null, null, null, null)
    assertNull("fetchFonts should return null when bridge fails without falling back to original", result)

    val decompiled = afterTransformTrace.toString()
    assertTrue("Decompiled code should reference FontsContractCompatBridge", decompiled.contains("FontsContractCompatBridge"))
  }

  @Test
  fun testRequestFontTransformed() {
    val testClassLoader =
      setupTestClassLoaderWithTransformation(
        mapOf("androidx/core/provider/FontsContractCompat" to FakeFontsContractCompat::class.java),
        beforeTransformTrace,
        afterTransformTrace,
      ) { visitor ->
        FontsContractCompatTransform(visitor)
      }

    val transformedClass = testClassLoader.loadClass("androidx/core/provider/FontsContractCompat")
    val method =
      transformedClass.getMethod(
        "requestFont",
        Any::class.java,
        Any::class.java,
        Int::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
        Any::class.java,
        Any::class.java,
      )

    val result = method.invoke(null, null, null, 0, false, 0, null, null)
    assertNull("requestFont should return null when bridge fails without falling back to original", result)

    val voidMethod = transformedClass.getMethod("requestFont", Any::class.java, Any::class.java, Any::class.java, Any::class.java)
    voidMethod.invoke(null, null, null, null, null)

    val decompiled = afterTransformTrace.toString()
    assertTrue("Decompiled code should reference FontsContractCompatBridge", decompiled.contains("FontsContractCompatBridge"))
  }

  @Test
  fun testComputeMaxsTransformation() {
    val testClassLoader =
      setupTestClassLoaderWithTransformation(
        mapOf("androidx/core/provider/FontsContractCompat" to FakeFontsContractCompat::class.java),
        beforeTransformTrace,
        afterTransformTrace,
        flags = ClassWriter.COMPUTE_MAXS,
      ) { visitor ->
        FontsContractCompatTransform(visitor)
      }

    val transformedClass = testClassLoader.loadClass("androidx/core/provider/FontsContractCompat")
    val method = transformedClass.getMethod("fetchFonts", Any::class.java, Any::class.java, Any::class.java)

    val result = method.invoke(null, null, null, null)
    assertNull("fetchFonts should return null when bridge fails without falling back to original", result)

    val requestFontMethod =
      transformedClass.getMethod(
        "requestFont",
        Any::class.java,
        Any::class.java,
        Int::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
        Any::class.java,
        Any::class.java,
      )
    val requestFontResult = requestFontMethod.invoke(null, null, null, 0, false, 0, null, null)
    assertNull("requestFont should return null when bridge fails without falling back to original", requestFontResult)
  }

  @Test
  fun testParseQuery() {
    val (name1, weight1, italic1) = FontsContractCompatBridge.parseQuery("name=Open+Sans&weight=700&italic=1")
    assertEquals("Open Sans", name1)
    assertEquals(700, weight1)
    assertEquals(true, italic1)

    val (name2, weight2, italic2) = FontsContractCompatBridge.parseQuery("Roboto+Mono")
    assertEquals("Roboto Mono", name2)
    assertEquals(400, weight2)
    assertEquals(false, italic2)
  }
}
