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
package com.android.tools.idea.npw.module

import com.android.ide.common.repository.AgpVersion
import com.android.sdklib.AndroidMajorVersion
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.npw.module.recipes.compileSdk
import com.android.tools.idea.npw.module.recipes.minSdk
import com.android.tools.idea.npw.module.recipes.targetSdk
import kotlin.test.assertEquals
import org.junit.Test

class SharedMacrosTest {
  @Test
  fun toAndroidFieldVersionOldAgpVersion() {
    val agpVersion = AgpVersion(3, 1, 4)

    assertEquals("minSdkVersion 34", minSdk(AndroidMajorVersion(34), agpVersion))
    assertEquals("targetSdkVersion 34", targetSdk(AndroidMajorVersion(34), agpVersion))
    assertEquals("compileSdkVersion 34", compileSdk(AndroidVersion(34, 0), agpVersion))

    assertEquals("minSdkVersion \"S\"", minSdk(AndroidMajorVersion(30, "S"), agpVersion))
    assertEquals("targetSdkVersion \"S\"", targetSdk(AndroidMajorVersion(30, "S"), agpVersion))
    assertEquals("compileSdkVersion \"S\"", compileSdk(AndroidVersion(30, "S"), agpVersion))

    assertEquals(
      "minSdkVersion \"SomeFutureVersion\"",
      minSdk(AndroidMajorVersion(99, "SomeFutureVersion"), agpVersion),
    )
    assertEquals(
      "targetSdkVersion \"SomeFutureVersion\"",
      targetSdk(AndroidMajorVersion(99, "SomeFutureVersion"), agpVersion),
    )
    assertEquals(
      "compileSdkVersion \"SomeFutureVersion\"",
      compileSdk(AndroidVersion(99, "SomeFutureVersion"), agpVersion),
    )
  }

  @Test
  fun toAndroidFieldVersionNewAgpVersion() {
    val agpVersion = AgpVersion(8, 1, 0)

    assertEquals("minSdk 34", minSdk(AndroidMajorVersion(34), agpVersion))
    assertEquals("targetSdk 34", targetSdk(AndroidMajorVersion(34), agpVersion))
    assertEquals("compileSdk 34", compileSdk(AndroidVersion(34, 0), agpVersion))

    assertEquals("minSdk 36", minSdk(AndroidMajorVersion(36), agpVersion))
    assertEquals("targetSdk 36", targetSdk(AndroidMajorVersion(36), agpVersion))
    assertEquals("compileSdk 36", compileSdk(AndroidVersion(36, 0), agpVersion))

    assertEquals("minSdkPreview \"S\"", minSdk(AndroidMajorVersion(30, "S"), agpVersion))
    assertEquals("targetSdkPreview \"S\"", targetSdk(AndroidMajorVersion(30, "S"), agpVersion))
    assertEquals("compileSdkPreview \"S\"", compileSdk(AndroidVersion(30, "S"), agpVersion))

    assertEquals(
      "minSdkPreview \"SomeFutureVersion\"",
      minSdk(AndroidMajorVersion(99, "SomeFutureVersion"), agpVersion),
    )
    assertEquals(
      "targetSdkPreview \"SomeFutureVersion\"",
      targetSdk(AndroidMajorVersion(99, "SomeFutureVersion"), agpVersion),
    )
    assertEquals(
      "compileSdkPreview \"SomeFutureVersion\"",
      compileSdk(AndroidVersion(99, "SomeFutureVersion"), agpVersion),
    )
  }
}
