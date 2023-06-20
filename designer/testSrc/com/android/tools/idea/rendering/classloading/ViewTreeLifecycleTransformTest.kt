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
package com.android.tools.idea.rendering.classloading

import com.android.testutils.TestUtils
import com.android.tools.idea.rendering.classloading.loaders.AsmTransformingLoader
import com.android.tools.idea.rendering.classloading.loaders.ClassLoaderLoader
import com.android.tools.idea.rendering.classloading.loaders.DelegatingClassLoader
import org.jetbrains.android.uipreview.createUrlClassLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private const val LOADER_WORKSPACE_PATH = "tools/adt/idea/designer/testData/classloader"
private const val CLASSES_TO_LOAD_JAR_NAME = "viewtreelifecycleowner.jar"

class ViewTreeLifecycleTransformTest {

  @Test
  fun `get_Original returns null, and get returns not null`() {
    // Given a class loading [ViewTreeLifecycleTransform]
    val viewTreeLifecycleOwnerClass = getClassTransformedWithViewTreeLifecycleTransform()

    // When its get method returns a null value
    viewTreeLifecycleOwnerClass.callMethod("setReturnNull", true)

    // Then get_Original() method returns a null value
    assertNull(viewTreeLifecycleOwnerClass.callMethod("get_Original", null))

    // Then get() method returns a not null value
    assertNotNull(viewTreeLifecycleOwnerClass.callMethod("get", null))
  }

  @Test
  fun `get_Original doesn't return null, then get() returns the same value as get_Original`() {
    // Given a class loading [ViewTreeLifecycleTransform]
    val viewTreeLifecycleOwnerClass = getClassTransformedWithViewTreeLifecycleTransform()

    // When its get method returns a not null value
    viewTreeLifecycleOwnerClass.callMethod("setReturnNull", false)

    // Then get() returns a not null value
    val getReturnValue = viewTreeLifecycleOwnerClass.callMethod("get", null)
    assertNotNull(getReturnValue)

    // Then get_Original() returns a not null value
    val getOriginalReturnValue = viewTreeLifecycleOwnerClass.callMethod("get_Original", null)
    assertNotNull(getOriginalReturnValue)

    // Then returned values of both get() and get_Original() are the same.
    assertEquals(getOriginalReturnValue, getReturnValue)
  }

  private fun getClassTransformedWithViewTreeLifecycleTransform() = ViewTreeLifecycleLoader()
    .loadClass("androidx/lifecycle/ViewTreeLifecycleOwner")

  /**
   * Calls the [methodName] method from the class passing null value for every argument
   */
  private fun Class<*>.callMethod(methodName: String, vararg arguments: Any?): Any? = declaredMethods
    .firstOrNull { it.name == methodName }
    ?.apply { isAccessible = true }
    ?.invoke(this, *arguments)

  /**
   * Loads the jar containing all the class to be tested:
   * - androidx.lifecycle.Lifecycle
   * - androidx.lifecycle.LifecycleOwner
   * - androidx.lifecycle.ViewTreeLifecycleOwner
   * - android.view.View
   * - androidx.savedstate.SavedStateRegistry
   * - androidx.savedstate.SavedStateRegistryOwner
   * - androidx.savedstate.ViewTreeSavedStateRegistryOwner
   * - _layoutlib_/_internal_/androidx/lifecycle/FakeSavedStateRegistry.java
   *
   * It is possible to find these classes in: compose-designer/testData/classloader/
   */
  inner class ViewTreeLifecycleLoader : DelegatingClassLoader(
    this.javaClass.classLoader,
    AsmTransformingLoader(
      toClassTransform({ ViewTreeLifecycleTransform(it) }),
      ClassLoaderLoader(
        createUrlClassLoader(
          listOf(
            TestUtils
              .resolveWorkspacePath(LOADER_WORKSPACE_PATH)
              .resolve(CLASSES_TO_LOAD_JAR_NAME)
          )
        )
      ),
      NopClassLocator
    )
  )
}
