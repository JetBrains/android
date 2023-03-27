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
package com.android.tools.idea.rendering.classloading

import com.android.tools.idea.rendering.classloading.loaders.DelegatingClassLoader
import com.android.tools.idea.rendering.classloading.loaders.StaticLoader
import org.junit.Assert.fail
import org.junit.Test

class A1

class A2

class B1

class B2

class FilteringClassLoaderTest {
  private val parentClassLoader =
    DelegatingClassLoader(
      null,
      StaticLoader(
        A1::class.java.name to loadClassBytes(A1::class.java),
        A2::class.java.name to loadClassBytes(A2::class.java),
        B1::class.java.name to loadClassBytes(B1::class.java),
        B2::class.java.name to loadClassBytes(B2::class.java),
        com.android.tools.idea.rendering.classloading.prefix.A1::class.java.name to loadClassBytes(com.android.tools.idea.rendering.classloading.prefix.A1::class.java),
        com.android.tools.idea.rendering.classloading.prefix.A2::class.java.name to loadClassBytes(com.android.tools.idea.rendering.classloading.prefix.A2::class.java),
      )
    )

  @Test
  fun `test some classes are filtered`() {
    val allow1ClassLoader =
      FilteringClassLoader(parentClassLoader) {
        // Only allow classes ending in 1
        it.endsWith("1")
      }
    val allow2ClassLoader =
      FilteringClassLoader(parentClassLoader) {
        // Only allow classes ending in 1
        it.endsWith("2")
      }

    allow1ClassLoader.loadClass(A1::class.java.name)
    allow1ClassLoader.loadClass(B1::class.java.name)
    allow1ClassLoader.loadClass(com.android.tools.idea.rendering.classloading.prefix.A1::class.java.name)
    allow2ClassLoader.loadClass(A2::class.java.name)
    allow2ClassLoader.loadClass(B2::class.java.name)
    allow2ClassLoader.loadClass(com.android.tools.idea.rendering.classloading.prefix.A2::class.java.name)

    // The following will not be found
    listOf(
        A2::class.java.name,
        B2::class.java.name,
        com.android.tools.idea.rendering.classloading.prefix.A2::class.java.name,
      )
      .forEach {
        try {
          allow1ClassLoader.loadClass(it)
          fail("ClassNotFoundException expected for '$it'")
        } catch (_: ClassNotFoundException) {}
      }

    listOf(
        A1::class.java.name,
        B1::class.java.name,
        com.android.tools.idea.rendering.classloading.prefix.A1::class.java.name,
      )
      .forEach {
        try {
          allow2ClassLoader.loadClass(it)
          fail("ClassNotFoundException expected for '$it'")
        } catch (_: ClassNotFoundException) {}
      }
  }

  @Test
  fun `test prefix filtering disallow`() {
    val filteringClassLoader =
      FilteringClassLoader.disallowedPrefixes(
        parentClassLoader,
        listOf("com.android.tools.idea.rendering.classloading.prefix.", "androidx.test.")
      )

    filteringClassLoader.loadClass(A1::class.java.name)
    filteringClassLoader.loadClass(B1::class.java.name)

    // The following will not be found
    listOf(
        com.android.tools.idea.rendering.classloading.prefix.A1::class.java.name,
        com.android.tools.idea.rendering.classloading.prefix.A2::class.java.name,
      )
      .forEach {
        try {
          filteringClassLoader.loadClass(it)
          fail("ClassNotFoundException expected for '$it'")
        } catch (_: ClassNotFoundException) {}
      }
  }

  @Test
  fun `test prefix filtering allow`() {
    val filteringClassLoader =
      FilteringClassLoader.allowedPrefixes(
        parentClassLoader,
        listOf("com.android.tools.idea.rendering.classloading.prefix.")
      )

    filteringClassLoader.loadClass(com.android.tools.idea.rendering.classloading.prefix.A1::class.java.name)
    filteringClassLoader.loadClass(com.android.tools.idea.rendering.classloading.prefix.A2::class.java.name)

    // The following will not be found
    listOf(
        A1::class.java.name,
        A2::class.java.name,
      )
      .forEach {
        try {
          filteringClassLoader.loadClass(it)
          fail("ClassNotFoundException expected for '$it'")
        } catch (_: ClassNotFoundException) {}
      }
  }
}
