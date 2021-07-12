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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningLoaderTest {
  @Test
  fun `check listening`() {
    val classes = mapOf(
      "a.class1" to ByteArray(1),
      "a.class2" to ByteArray(2)
    )
    val staticLoader = StaticLoader(classes)
    val loadedClasses = mutableSetOf<String>()
    val classLoadingDetection = object : DelegatingClassLoader.Loader {
      override fun loadClass(fqcn: String): ByteArray? {
        try {
          return staticLoader.loadClass(fqcn)
        }
        finally {
          loadedClasses.add(fqcn)
        }
      }
    }

    val loader = ListeningLoader(
      classLoadingDetection,
      onBeforeLoad = {
        assertFalse("onBeforeLoad called after the class was loaded", loadedClasses.contains(it))
      },
      onAfterLoad = { fqcn, bytes ->
        assertTrue("onAfterLoad called before the class was loaded", loadedClasses.contains(fqcn))
        assertEquals("the contents do not match the loaded class", classes[fqcn], bytes)
      },
      onNotFound = { assertEquals("not.found.class", it) }
    )
    loader.loadClass("not.found.class")
    loader.loadClass("a.class1")
    loader.loadClass("a.class2")
  }
}