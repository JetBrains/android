/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.idea.rendering.classloading.FakeSavedStateRegistryClassDump
import org.junit.Assert.assertNotNull
import org.junit.Test

class FakeSavedStateRegistryLoaderTest {

  private val expectedClassContent = FakeSavedStateRegistryClassDump.lifecycleClassDump

  @Test
  fun `FakeSavedStateRegistry is loaded with the correct content`() {
    // Given a map of classes with an empty FakeSavedStateRegistry.
    val classesToLoad = mapOf(
      "should.skip.ThisClass1" to ByteArray(size = 4),
      "_layoutlib_._internal_.androidx.lifecycle.FakeSavedStateRegistry" to ByteArray(size = 0),
      "should.skip.ThisClass2" to ByteArray(size = 5),
      "should.skip.ThisClass3" to ByteArray(size = 42),
    )

    // Given a delegate containing the classes.
    val loadedClasses = mutableSetOf<String>()
    val staticLoader = StaticLoader(classesToLoad)
    val classDetectorDelegate = object : DelegatingClassLoader.Loader {
      override fun loadClass(fqcn: String): ByteArray? {
        try {
          return staticLoader.loadClass(fqcn)
        }
        finally {
          loadedClasses.add(fqcn)
        }
      }
    }

    // When FakeSavedStateRegistryLoader loads the classes from the delegate.
    val loader = FakeSavedStateRegistryLoader(classDetectorDelegate)
    val loadedClassesContent = classesToLoad.map { (name, _) -> loader.loadClass(name) }

    // Then one of the content of the FakeSavedStateRegistry is not empty, and contains the expected content.
    assertNotNull(loadedClassesContent.singleOrNull { it.contentEquals(expectedClassContent) })
  }
}