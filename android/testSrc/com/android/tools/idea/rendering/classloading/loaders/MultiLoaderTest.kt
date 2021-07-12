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
      "a.class2" to ByteArray(2)
    )
    val static3 = StaticLoader(
      "a.class3" to ByteArray(3)
    )

    val output = StringBuilder()
    val loader = MultiLoader(
      static1.withDebug("static1", output),
      static2.withDebug("static2", output),
      static3.withDebug("static3", output)
    )

    loader.loadClass("not.exist")
    output.clear()

    assertEquals(3, loader.loadClass("a.class3")?.size ?: 0)
    assertEquals("""
      [static1] onNotFound a.class3
      [static2] onNotFound a.class3
      [static3] onAfterLoad a.class3 (3)
    """.trimIndent(), output.toString().trim())
    output.clear()

    assertEquals(1, loader.loadClass("a.class1")?.size ?: 0)
    assertEquals("[static1] onAfterLoad a.class1 (1)", output.toString().trim())
    output.clear()

    assertEquals(2, loader.loadClass("a.class2")?.size ?: 0)
    assertEquals("""
      [static1] onNotFound a.class2
      [static2] onAfterLoad a.class2 (2)
    """.trimIndent(), output.toString().trim())
    output.clear()
  }
}