/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.layoutlib

import com.android.layoutlib.bridge.BridgeConstants
import org.junit.Test
import java.net.URL
import java.util.jar.JarFile
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LayoutPrebuiltTest {
  // Regression test for b/109738602
  @Test
  fun jarContents() {
    val classUrl = BridgeConstants::class.java.getResource(BridgeConstants::class.simpleName + ".class")
    assertEquals("jar", classUrl.protocol)
    val jarUrl = URL(classUrl.path.substringBefore("!"))
    val jarFile = JarFile(jarUrl.file)
    val jarEntryNames = jarFile.entries().asSequence()
      .map { it.name }
      .toSet()

    // Sanity check to make sure the file contains some data
    assertTrue(jarEntryNames.contains("android/R.class"))
    assertTrue(jarEntryNames.contains("android/R\$layout.class"))

    // Check that the jar does not contain classes in sun.** or java.**zs
    assertFalse(jarEntryNames.any {
      it.startsWith("sun/") || it.startsWith("java/")
    })
  }
}