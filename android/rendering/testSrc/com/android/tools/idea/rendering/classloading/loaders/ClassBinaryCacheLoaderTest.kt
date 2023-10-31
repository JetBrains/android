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

import com.android.tools.rendering.classloading.ClassBinaryCache
import com.android.tools.rendering.classloading.loaders.StaticLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClassBinaryCacheLoaderTest {
  @Test
  fun `check loading from cache`() {
    val backedMap = mutableMapOf<String, ByteArray>()
    val cache = object: ClassBinaryCache {
      override fun get(fqcn: String, transformationId: String): ByteArray? =
        backedMap["$transformationId:$fqcn"]

      override fun put(fqcn: String, transformationId: String, libraryPath: String, data: ByteArray) {
        backedMap["$transformationId:$fqcn"] = data
      }

      override fun setDependencies(paths: Collection<String>) {
        TODO("Not yet implemented")
      }

    }
    val staticLoader = StaticLoader(
      "a.class1" to ByteArray(1),
      "a.class2" to ByteArray(2)
    )

    val loader = ClassBinaryCacheLoader(
      staticLoader,
      "transformationID",
      cache)

    assertNull(loader.loadClass("not.found.class"))
    assertEquals(1, loader.loadClass("a.class1")?.size ?: 0)
    assertEquals(2, loader.loadClass("a.class2")?.size ?: 0)

    // Add to the cache and ensure the cache gets hit
    cache.put("a.class2", "transformationID", "", ByteArray(4))
    assertEquals(1, loader.loadClass("a.class1")?.size ?: 0)
    // This will come from the cache now
    assertEquals(4, loader.loadClass("a.class2")?.size ?: 0)
    backedMap.clear()
    // Now it will be loaded again from the static loader
    assertEquals(2, loader.loadClass("a.class2")?.size ?: 0)
  }
}