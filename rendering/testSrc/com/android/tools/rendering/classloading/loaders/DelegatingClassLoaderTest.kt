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
package com.android.tools.rendering.classloading.loaders

import com.android.tools.rendering.classloading.loadClassBytes
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test

private class TestClass

internal class DelegatingClassLoaderTest {
  @Test
  fun `check loader successfully finds class`() {
    val classLoader =
      DelegatingClassLoader(
        null,
        StaticLoader(TestClass::class.java.canonicalName to loadClassBytes(TestClass::class.java)),
      )

    assertNotNull(classLoader.loadClass(TestClass::class.java.canonicalName))

    // Check that a nonexistent class throws ClassNotFound
    try {
      classLoader.loadClass("class.do.not.exist")
      fail("ClassNotFoundException expected")
    } catch (_: ClassNotFoundException) {}

    try {
      classLoader.loadClass("")
      fail("ClassNotFoundException expected")
    } catch (_: ClassNotFoundException) {}
  }

  @Test
  fun `check class renaming`() {
    val classLoader =
      DelegatingClassLoader(
        null,
        StaticLoader("private.Test" to loadClassBytes(TestClass::class.java)),
      )

    assertNotNull(classLoader.loadClass("private.Test"))

    // Check that a nonexistent class throws ClassNotFound
    try {
      classLoader.loadClass("class.do.not.exist")
      fail("ClassNotFoundException expected")
    } catch (_: ClassNotFoundException) {}

    try {
      classLoader.loadClass("")
      fail("ClassNotFoundException expected")
    } catch (_: ClassNotFoundException) {}
  }
}
