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
package com.android.tools.rendering.classloading

import com.android.layoutlib.bridge.impl.RenderAction
import java.io.StringWriter
import org.junit.Assert.assertEquals
import org.junit.Test

class SdkIntReplacerTest {
  /** [StringWriter] that stores the decompiled classes after they've been transformed. */
  private val afterTransformTrace = StringWriter()

  /** [StringWriter] that stores the decompiled classes before they've been transformed. */
  private val beforeTransformTrace = StringWriter()

  @Test
  fun testTransform() {
    assertEquals(33, ResourcesCompat.sdkVersion)
    assertEquals(33, ResourcesCompat.getSdkVersion())

    val testClassLoader =
      setupTestClassLoaderWithTransformation(
        mapOf("androidx/core/content/res/ResourcesCompat" to ResourcesCompat::class.java),
        beforeTransformTrace,
        afterTransformTrace,
      ) { visitor ->
        StaticFieldReplacer(
          visitor,
          "com/android/tools/rendering/classloading/Build\$VERSION",
          "SDK_INT",
          "com/android/layoutlib/bridge/impl/RenderAction",
          "sSimulatedSdk",
        )
      }
    val resourcesCompat = testClassLoader.loadClass("androidx/core/content/res/ResourcesCompat")

    RenderAction.sSimulatedSdk = 28
    val fieldResult = resourcesCompat.fields.firstOrNull { it.name == "sdkVersion" }?.get(null)
    assertEquals(28, fieldResult)

    val methodResult =
      resourcesCompat.methods.firstOrNull { it.name == "getSdkVersion" }?.invoke(null)
    assertEquals(28, methodResult)
  }
}
