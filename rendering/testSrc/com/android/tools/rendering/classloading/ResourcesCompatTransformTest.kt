/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.util.TypedValue
import java.io.StringWriter
import java.lang.RuntimeException
import org.junit.Assert.assertThrows
import org.junit.Test

class ResourcesCompatTransformTest {

  /** [StringWriter] that stores the decompiled classes after they've been transformed. */
  private val afterTransformTrace = StringWriter()

  /** [StringWriter] that stores the decompiled classes before they've been transformed. */
  private val beforeTransformTrace = StringWriter()

  @Test
  fun testTransform() {
    val value = TypedValue()
    value.string = "project-path/res/font/font.ttf"

    assertThrows(RuntimeException::class.java) {
      ResourcesCompat.loadFont(null, null, value, 0, 0, null, null, false, false)
    }

    val testClassLoader =
      setupTestClassLoaderWithTransformation(
        mapOf(
          "androidx/core/content/res/ResourcesCompat\$FontCallback" to
            ResourcesCompat.FontCallback::class.java,
          "androidx/core/content/res/ResourcesCompat" to ResourcesCompat::class.java,
        ),
        beforeTransformTrace,
        afterTransformTrace,
      ) { visitor ->
        ResourcesCompatTransform(visitor)
      }
    testClassLoader.loadClass("androidx/core/content/res/ResourcesCompat\$FontCallback")
    val resourcesCompat = testClassLoader.loadClass("androidx/core/content/res/ResourcesCompat")
    resourcesCompat.methods
      .firstOrNull { it.name == "loadFont" }
      ?.invoke(null, null, null, value, 0, 0, null, null, false, false)
  }
}
