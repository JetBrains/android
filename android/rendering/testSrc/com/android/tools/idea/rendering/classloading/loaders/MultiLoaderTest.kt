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

import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader
import com.android.tools.rendering.classloading.loaders.MultiLoader
import com.android.tools.rendering.classloading.loaders.StaticLoader
import org.junit.Assert.assertEquals
import org.junit.Test

private fun DelegatingClassLoader.Loader.withDebug(prefix: String, out: StringBuilder) =
  ListeningLoader(this,
                  onAfterLoad = { fqcn, bytes -> out.append("[$prefix] onAfterLoad $fqcn (${bytes.size})\n") },
                  onNotFound = { out.append("[$prefix] onNotFound $it\n") })

class MultiLoaderTest {
  @Test
  fun `load from multiple loads`() {
    val static1 = StaticLoader(
      "a.class1" to ByteArray(1)
    )
    val static2 = StaticLoader(
      "b.class2" to ByteArray(2)
    )
    val static3 = StaticLoader(
      "c.class3" to ByteArray(3)
    )

    val output = StringBuilder()
    val loaders =
      listOf(
        MultiLoader(
          static1.withDebug("static1", output),
          static2.withDebug("static2", output),
          static3.withDebug("static3", output)
        )
      )

    loaders.forEach { loader ->
      loader.loadClass("not.exist")
      output.clear()

      assertEquals(3, loader.loadClass("c.class3")?.size ?: 0)
      assertEquals("""
      [static1] onNotFound c.class3
      [static2] onNotFound c.class3
      [static3] onAfterLoad c.class3 (3)
    """.trimIndent(), output.toString().trim())
      output.clear()

      assertEquals(1, loader.loadClass("a.class1")?.size ?: 0)
      assertEquals("[static1] onAfterLoad a.class1 (1)", output.toString().trim())
      output.clear()

      assertEquals(2, loader.loadClass("b.class2")?.size ?: 0)
      assertEquals("""
      [static1] onNotFound b.class2
      [static2] onAfterLoad b.class2 (2)
    """.trimIndent(), output.toString().trim())
      output.clear()
    }
  }

  @Test
  fun `verify loader with affinity and without match `() {
    val static1 = StaticLoader(
      "a.class1" to ByteArray(1)
    )
    val static2 = StaticLoader(
      "b.class2" to ByteArray(2)
    )
    val static3 = StaticLoader(
      "c.class3" to ByteArray(3)
    )

    val output = StringBuilder()
    val loader = MultiLoaderWithAffinity(
      static1.withDebug("static1", output),
      static2.withDebug("static2", output),
      static3.withDebug("static3", output)
    )

    loader.loadClass("not.exist")
    output.clear()

    assertEquals(3, loader.loadClass("c.class3")?.size ?: 0)
    assertEquals("""
      [static1] onNotFound c.class3
      [static2] onNotFound c.class3
      [static3] onAfterLoad c.class3 (3)
    """.trimIndent(), output.toString().trim())
    output.clear()

    assertEquals(1, loader.loadClass("a.class1")?.size ?: 0)
    assertEquals("[static1] onAfterLoad a.class1 (1)", output.toString().trim())
    output.clear()

    assertEquals(2, loader.loadClass("b.class2")?.size ?: 0)
    assertEquals("""
      [static1] onNotFound b.class2
      [static2] onAfterLoad b.class2 (2)
    """.trimIndent(), output.toString().trim())
    output.clear()
  }

  @Test
  fun `ensure affinity sticks after the first load`() {
    val static1 = StaticLoader(
      "a.class1" to ByteArray(1)
    )
    val static2 = StaticLoader(
      "b.class2" to ByteArray(2)
    )
    val static3 = StaticLoader(
      "c.class3" to ByteArray(3)
    )

    val output = StringBuilder()
    val loader = MultiLoaderWithAffinity(
      static1.withDebug("static1", output),
      static2.withDebug("static2", output),
      static3.withDebug("static3", output)
    )

    loader.loadClass("not.exist")
    output.clear()

    assertEquals(3, loader.loadClass("c.class3")?.size ?: 0)
    assertEquals("""
      [static1] onNotFound c.class3
      [static2] onNotFound c.class3
      [static3] onAfterLoad c.class3 (3)
    """.trimIndent(), output.toString().trim())
    output.clear()

    assertEquals(3, loader.loadClass("c.class3")?.size ?: 0)
    // The second attempt will directly use the right class loader
    assertEquals("[static3] onAfterLoad c.class3 (3)".trimIndent(), output.toString().trim())
  }

  @Test
  fun `cache keys are limited`() {
    val static1 = StaticLoader(
      "a.b.c.class1" to ByteArray(1),
      "d.e.f.class2" to ByteArray(2),
      "g.h.i.class3" to ByteArray(3)
    )

    val loader = MultiLoaderWithAffinity(listOf(static1))
    val loaderWithCacheLimit = MultiLoaderWithAffinity(listOf(static1), 3)
    listOf(loader, loaderWithCacheLimit).forEach {
      assertEquals(1, it.loadClass("a.b.c.class1")?.size ?: 0)
      assertEquals(2, it.loadClass("d.e.f.class2")?.size ?: 0)
      assertEquals(3, it.loadClass("g.h.i.class3")?.size ?: 0)
    }
    assertEquals(9, loader.cacheSize)
    assertEquals(3, loaderWithCacheLimit.cacheSize)
  }

  @Test
  fun `affine cache does not create unnecessary keys`() {
    val static1 = StaticLoader(
      "a.b.class1" to ByteArray(1),
      "a.b.c.class1" to ByteArray(2),
    )

    val loader = MultiLoaderWithAffinity(listOf(static1))
    assertEquals(1, loader.loadClass("a.b.class1")?.size ?: 0)
    assertEquals(2, loader.loadClass("a.b.c.class1")?.size ?: 0)

    // The checks above should only create two keys in the index since the second lookup will be already found.
    // The first lookup will create `a` and `a.b` as keys in the index. The second lookup, because the class is found, will
    // not create `a.b.c`.
    assertEquals(2, loader.cacheSize)
  }

  @Test
  fun `check package prefix generation`() {
    assertEquals(
      """
        a.b
        a
      """.trimIndent(),
      findAllPackagePrefixes("a.b.C").joinToString("\n")
    )
    assertEquals(
      """
        a.b
        a
      """.trimIndent(),
      findAllPackagePrefixes("a.b.C${'$'}A").joinToString("\n")
    )

    assertEquals("", findAllPackagePrefixes("A").joinToString("\n"))
    assertEquals("", findAllPackagePrefixes("").joinToString("\n"))
  }
}