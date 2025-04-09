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
import com.android.tools.idea.npw.module.recipes.toAndroidFieldVersion
import kotlin.test.assertEquals
import org.junit.Test

class SharedMacrosTest {
  @Test
  fun toAndroidFieldVersionOldAgpVersion() {
    val agpVersion = AgpVersion(3, 1, 4)

    assertEquals("minSdkVersion 34", toAndroidFieldVersion("minSdk", "34", agpVersion))
    assertEquals("targetSdkVersion 34", toAndroidFieldVersion("targetSdk", "34", agpVersion))
    assertEquals("compileSdkVersion 34", toAndroidFieldVersion("compileSdk", "34", agpVersion))

    assertEquals("minSdkVersion \"S\"", toAndroidFieldVersion("minSdk", "S", agpVersion))
    assertEquals("targetSdkVersion \"S\"", toAndroidFieldVersion("targetSdk", "S", agpVersion))
    assertEquals("compileSdkVersion \"S\"", toAndroidFieldVersion("compileSdk", "S", agpVersion))

    assertEquals(
      "minSdkVersion \"SomeFutureVersion\"",
      toAndroidFieldVersion("minSdk", "SomeFutureVersion", agpVersion),
    )
    assertEquals(
      "targetSdkVersion \"SomeFutureVersion\"",
      toAndroidFieldVersion("targetSdk", "SomeFutureVersion", agpVersion),
    )
    assertEquals(
      "compileSdkVersion \"SomeFutureVersion\"",
      toAndroidFieldVersion("compileSdk", "SomeFutureVersion", agpVersion),
    )
  }

  @Test
  fun toAndroidFieldVersionNewAgpVersion() {
    val agpVersion = AgpVersion(8, 1, 0)

    assertEquals("minSdk 34", toAndroidFieldVersion("minSdk", "34", agpVersion))
    assertEquals("targetSdk 34", toAndroidFieldVersion("targetSdk", "34", agpVersion))
    assertEquals("compileSdk 34", toAndroidFieldVersion("compileSdk", "34", agpVersion))

    assertEquals("minSdk 36", toAndroidFieldVersion("minSdk", "36.0", agpVersion))
    assertEquals("targetSdk 36", toAndroidFieldVersion("targetSdk", "36.0", agpVersion))
    assertEquals("compileSdk 36", toAndroidFieldVersion("compileSdk", "36.0", agpVersion))

    assertEquals("minSdkPreview \"S\"", toAndroidFieldVersion("minSdk", "S", agpVersion))
    assertEquals("targetSdkPreview \"S\"", toAndroidFieldVersion("targetSdk", "S", agpVersion))
    assertEquals("compileSdkPreview \"S\"", toAndroidFieldVersion("compileSdk", "S", agpVersion))

    assertEquals(
      "minSdkPreview \"SomeFutureVersion\"",
      toAndroidFieldVersion("minSdk", "SomeFutureVersion", agpVersion),
    )
    assertEquals(
      "targetSdkPreview \"SomeFutureVersion\"",
      toAndroidFieldVersion("targetSdk", "SomeFutureVersion", agpVersion),
    )
    assertEquals(
      "compileSdkPreview \"SomeFutureVersion\"",
      toAndroidFieldVersion("compileSdk", "SomeFutureVersion", agpVersion),
    )
  }
}
