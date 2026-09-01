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

import java.io.StringWriter
import java.lang.reflect.InvocationTargetException
import org.junit.Assert.assertThrows
import org.junit.Test

class GapWorkerTransformTest {
  private val afterTransformTrace = StringWriter()
  private val beforeTransformTrace = StringWriter()

  @Test
  fun testAndroidxGapWorkerTransform() {
    val unTransformed = GapWorker()
    assertThrows(RuntimeException::class.java) { unTransformed.add(Any()) }
    assertThrows(RuntimeException::class.java) { unTransformed.postFromTraversal(Any(), 0, 0) }
    assertThrows(RuntimeException::class.java) { unTransformed.otherMethod() }

    val testClassLoader =
      setupTestClassLoaderWithTransformation(
        mapOf("androidx/recyclerview/widget/GapWorker" to GapWorker::class.java),
        beforeTransformTrace,
        afterTransformTrace,
      ) { visitor ->
        GapWorkerTransform(visitor)
      }
    val gapWorkerClass = testClassLoader.loadClass("androidx/recyclerview/widget/GapWorker")
    val gapWorkerInstance = gapWorkerClass.getDeclaredConstructor().newInstance()

    // add and postFromTraversal should be no-ops and not throw
    gapWorkerClass.getMethod("add", Any::class.java).invoke(gapWorkerInstance, Any())
    gapWorkerClass
      .getMethod("postFromTraversal", Any::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
      .invoke(gapWorkerInstance, Any(), 0, 0)

    // otherMethod should not be transformed and should throw RuntimeException
    val exception =
      assertThrows(InvocationTargetException::class.java) {
        gapWorkerClass.getMethod("otherMethod").invoke(gapWorkerInstance)
      }
    assert(exception.targetException is RuntimeException)
  }

  @Test
  fun testOtherClassNotTransformed() {
    val testClassLoader =
      setupTestClassLoaderWithTransformation(
        mapOf("com/example/GapWorker" to GapWorker::class.java),
        beforeTransformTrace,
        afterTransformTrace,
      ) { visitor ->
        GapWorkerTransform(visitor)
      }
    val gapWorkerClass = testClassLoader.loadClass("com/example/GapWorker")
    val gapWorkerInstance = gapWorkerClass.getDeclaredConstructor().newInstance()

    // Methods in non-GapWorker class should not be transformed
    assertThrows(InvocationTargetException::class.java) {
      gapWorkerClass.getMethod("add", Any::class.java).invoke(gapWorkerInstance, Any())
    }
  }
}
