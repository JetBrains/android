/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.idea.rendering.classloading.FakeNavigationEventDispatcherOwnerDump
import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader
import com.android.tools.rendering.classloading.loaders.StaticLoader
import kotlin.test.assertNotEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

private const val CLASS_NAME_1 = "should.skip.ThisClass1"
private const val CLASS_NAME_2 = "should.skip.ThisClass2"
private const val CLASS_NAME_3 = "should.skip.ThisClass3"
private const val FAKE_NAVIGATION_EVENT_DISPATCHER_OWNER = "androidx.navigationevent.compose.FakeNavigationEventDispatcherOwner"

class FakeNavigationEventDispatcherOwnerLoaderTest {

  private val expectedClassContent = FakeNavigationEventDispatcherOwnerDump.getClassDumpByteArray

  @Test
  fun `FakeNavigationEventDispatcherOwner is loaded with the correct content`() {
    val thisClass1Bytes = ByteArray(size = 4)
    val thisClass2Bytes = ByteArray(size = 5)
    val thisClass3Bytes = ByteArray(size = 42)
    val fakeNavigationEventDispatcherOwnerBytes = ByteArray(size = 0)

    // Given a map of classes with an empty FakeNavigationEventDispatcherOwner.
    val classesToLoad =
      mapOf(
        CLASS_NAME_1 to thisClass1Bytes,
        FAKE_NAVIGATION_EVENT_DISPATCHER_OWNER to fakeNavigationEventDispatcherOwnerBytes,
        CLASS_NAME_2 to thisClass2Bytes,
        CLASS_NAME_3 to thisClass3Bytes,
      )

    // Given a delegate containing the classes.
    val loadedClasses = mutableSetOf<String>()
    val staticLoader = StaticLoader(classesToLoad)
    val classDetectorDelegate =
      object : DelegatingClassLoader.Loader {
        override fun loadClass(fqcn: String): ByteArray? {
          try {
            return staticLoader.loadClass(fqcn)
          } finally {
            loadedClasses.add(fqcn)
          }
        }
      }

    // When FakeNavigationEventDispatcherOwnerLoader loads the classes from the delegate.
    val loader = FakeNavigationEventDispatcherOwnerLoader(classDetectorDelegate)
    val loadedClassesContent = classesToLoad.keys.associateWith { name -> loader.loadClass(name) }

    // Then one of the content of the FakeNavigationEventDispatcherOwner is not empty, and contains
    // the expected content.
    assertArrayEquals(expectedClassContent, loadedClassesContent[FAKE_NAVIGATION_EVENT_DISPATCHER_OWNER])
    assertArrayNotEquals(fakeNavigationEventDispatcherOwnerBytes, loadedClassesContent[FAKE_NAVIGATION_EVENT_DISPATCHER_OWNER])

    // Verify other classes are loaded correctly
    assertArrayEquals(thisClass1Bytes, loadedClassesContent[CLASS_NAME_1])
    assertArrayEquals(thisClass2Bytes, loadedClassesContent[CLASS_NAME_2])
    assertArrayEquals(thisClass3Bytes, loadedClassesContent[CLASS_NAME_3])
  }

  private fun assertArrayNotEquals(expected: ByteArray?, actual: ByteArray?) {
    assertNotEquals(expected?.contentToString(), actual?.contentToString())
  }
}
