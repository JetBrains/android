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
package com.android.tools.res

import com.android.testutils.TestUtils
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.zip.ZipFile

class FrameworkOverlayTest {

  @Test
  fun testOverlaysPresentInJar() {
    val frameworkResJar = TestUtils.resolveWorkspacePath("prebuilts/studio/layoutlib/data/framework_res.jar")
    ZipFile(frameworkResJar.toFile()).use { zipFile ->
      FrameworkOverlay.entries.forEach {
        val overlayEntry = zipFile.getEntry("overlays/${it.overlayName}/resources.bin")
        assertNotNull(overlayEntry)
      }
    }
  }
}