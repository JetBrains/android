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

import com.android.tools.rendering.classloading.codeexecution.A
import com.android.tools.rendering.classloading.codeexecution.B
import com.android.tools.rendering.classloading.codeexecution.C
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CodeExecutionTrackerTransformTest {
  @Before
  fun setUp() {
    ClassesTracker.clear("")
  }

  @Test
  fun testTracksOnlyUsedMethods() {
    val testClassLoader =
      setupTestClassLoaderWithTransformation(
        mapOf("com/example/A" to A::class.java, "com/example/B" to B::class.java)
      ) { visitor ->
        CodeExecutionTrackerTransform(visitor, "")
      }

    val classA = testClassLoader.loadClass("com/example/A")
    val classB = testClassLoader.loadClass("com/example/B")
    assertEquals(emptySet<String>(), ClassesTracker.getClasses(""))

    val intBMethod = classB.declaredMethods.first { it.name == "intB" }
    intBMethod.isAccessible = true
    assertEquals(1, intBMethod.invoke(null))

    assertEquals(1, ClassesTracker.getClasses("").size)
    assertEquals("com/example/B", ClassesTracker.getClasses("").first())

    val delegateToAMethod = classB.getMethod("delegateToA")
    assertEquals(0, delegateToAMethod.invoke(null))

    assertEquals(2, ClassesTracker.getClasses("").size)
    assertEquals(setOf("com/example/A", "com/example/B"), ClassesTracker.getClasses(""))

    val instanceA = classA.constructors.first().newInstance()
    val strBofAMethod = classB.declaredMethods.first { it.name == "strBofA" }

    ClassesTracker.clear("")

    strBofAMethod.invoke(null, instanceA)

    assertEquals(setOf("com/example/B"), ClassesTracker.getClasses(""))
  }

  @Test
  fun testTracksOnlyUsedFields() {
    val testClassLoader =
      setupTestClassLoaderWithTransformation(
        mapOf(
          "com/example/A" to A::class.java,
          "com/example/B" to B::class.java,
          "com/example/C" to C::class.java,
        )
      ) { visitor ->
        CodeExecutionTrackerTransform(visitor, "")
      }

    val classA = testClassLoader.loadClass("com/example/A")
    val classC = testClassLoader.loadClass("com/example/C")
    val classB = testClassLoader.loadClass("com/example/B")
    assertEquals(emptySet<String>(), ClassesTracker.getClasses(""))

    val instanceC = classC.constructors.first().newInstance()
    val callCMethod = classB.declaredMethods.first { it.name == "callC" }

    assertEquals(1, ClassesTracker.getClasses("").size)
    assertEquals("com/example/C", ClassesTracker.getClasses("").first())

    ClassesTracker.clear("")
    assertEquals(emptySet<String>(), ClassesTracker.getClasses(""))

    callCMethod.invoke(null, instanceC)
    assertEquals(2, ClassesTracker.getClasses("").size)
    assertEquals(setOf("com/example/B", "com/example/C"), ClassesTracker.getClasses(""))
  }
}
