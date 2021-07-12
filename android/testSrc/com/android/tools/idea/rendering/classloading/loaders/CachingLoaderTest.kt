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
import org.junit.Assert.assertTrue
import org.junit.Test

class CachingLoaderTest {
  private class LoadDetectionLoader(private val delegate: DelegatingClassLoader.Loader) : DelegatingClassLoader.Loader {
    val loaded = mutableListOf<String>()
    val notFound = mutableListOf<String>()
    val loadedString: String
      get() = loaded.joinToString("\n")
    val notFoundString: String
      get() = notFound.joinToString("\n")

    override fun loadClass(fqcn: String): ByteArray? {
      val bytes = delegate.loadClass(fqcn)
      if (bytes != null) loaded.add(fqcn) else notFound.add(fqcn)
      return bytes
    }
  }

  @Test
  fun `check cache hits and misses`() {
    val class1Contents = ByteArray(0)
    val class2Contents = ByteArray(0)
    val loaderWithHits = LoadDetectionLoader(
      StaticLoader(
        "c.class1" to class1Contents,
        "c.class2" to class2Contents
      )
    )

    val loader = CachingLoader(loaderWithHits)

    assertNull(loader.loadClass("missing.a"))
    assertNull(loader.loadClass("c.class3"))
    assertTrue(loaderWithHits.loaded.isEmpty())
    assertEquals("""
      missing.a
      c.class3
    """.trimIndent(), loaderWithHits.notFoundString)
    loaderWithHits.notFound.clear()

    assertEquals(class1Contents, loader.loadClass("c.class1"))
    assertEquals(class1Contents, loader.loadClass("c.class1"))
    assertEquals(class2Contents, loader.loadClass("c.class2"))
    assertEquals(class2Contents, loader.loadClass("c.class2"))
    assertTrue(loaderWithHits.notFound.isEmpty())
    assertEquals("""
      c.class1
      c.class2
    """.trimIndent(), loaderWithHits.loadedString)
    loaderWithHits.loaded.clear()

    // Check single class invalidation
    loader.invalidate("c.class1")
    assertEquals(class1Contents, loader.loadClass("c.class1"))
    assertEquals(class2Contents, loader.loadClass("c.class2"))
    assertEquals("""
      c.class1
    """.trimIndent(), loaderWithHits.loadedString)
    loaderWithHits.loaded.clear()

    loader.invalidateAll()
    assertEquals(class1Contents, loader.loadClass("c.class1"))
    assertEquals(class2Contents, loader.loadClass("c.class2"))
    assertEquals("""
      c.class1
      c.class2
    """.trimIndent(), loaderWithHits.loadedString)
  }

  @Test
  fun `test size eviction`() {
    val class1Contents = ByteArray(10)
    val class2Contents = ByteArray(50)
    val loaderWithHits = LoadDetectionLoader(
      StaticLoader(
        "c.class1" to class1Contents,
        "c.class2" to class2Contents
      )
    )

    val loader = CachingLoader(loaderWithHits, maxSizeInBytes = 50)
    assertEquals(class1Contents, loader.loadClass("c.class1"))
    assertEquals(class2Contents, loader.loadClass("c.class2"))
    loaderWithHits.loaded.clear()
    assertEquals(class1Contents, loader.loadClass("c.class1"))
    assertEquals(class2Contents, loader.loadClass("c.class2"))
    assertTrue(loaderWithHits.loadedString.isNotEmpty())
  }
}