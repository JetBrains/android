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
package com.android.tools.idea.rendering.classloading.loaders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NameRemapperLoaderTest {
  @Test
  fun `check remapping`() {
    // A simple remapper that adds "b." in front of the requested class name
    val loader = NameRemapperLoader(
      StaticLoader(
        "b.a.class1" to ByteArray(1),
        "b.a.class2" to ByteArray(2)
      )
    ) {
      "b.$it"
    }
    assertNull(loader.loadClass(""))
    assertNull(loader.loadClass("b.a.class1"))
    assertEquals(1, loader.loadClass("a.class1")?.size)
    assertEquals(2, loader.loadClass("a.class2")?.size)
  }
}