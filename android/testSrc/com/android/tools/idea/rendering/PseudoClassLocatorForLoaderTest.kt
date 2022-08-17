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
package com.android.tools.idea.rendering

import com.android.tools.idea.rendering.classloading.FilteringClassLoader
import com.android.tools.idea.rendering.classloading.FirewalledResourcesClassLoader
import com.android.tools.idea.rendering.classloading.loaders.NopLoader
import org.jetbrains.android.uipreview.PseudoClassLocatorForLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

internal data class ClassLoaderWithDescription(val classLoader: ClassLoader, val description: String) {
  override fun toString(): String = description
}

@RunWith(Parameterized::class)
internal class PseudoClassLocatorForLoaderTest(classLoaderWithDescription: ClassLoaderWithDescription) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}")
    val parentClassLoaders = listOf(
      ClassLoaderWithDescription(
        FilteringClassLoader(PseudoClassLocatorForLoaderTest::class.java.classLoader) {
          throw IllegalAccessError("$it should not have been loaded, only accessed via resources.")
        },
        "Class loader that will fail if findClass is invoked, only allowing resource loading."),
      ClassLoaderWithDescription(
        FirewalledResourcesClassLoader(PseudoClassLocatorForLoaderTest::class.java.classLoader),
        "Class loader that will not allow access resources and will force the PseudoClassLocatorForLoader to fall back to loading the Class<?>z.")
    )
  }

  private val parentClassLoader = classLoaderWithDescription.classLoader

  @Test
  fun `system classes are found`() {
    val pseudoClassLocator = PseudoClassLocatorForLoader(NopLoader, parentClassLoader)

    pseudoClassLocator.locatePseudoClass("java.lang.Object").let {
      assertEquals("java.lang.Object", it.name)
      assertEquals("java.lang.Object", it.superName)
      assertTrue(it.interfaces.isEmpty())
    }
    pseudoClassLocator.locatePseudoClass("java.lang.Integer").let {
      assertEquals("java.lang.Integer", it.name)
      assertEquals("java.lang.Number", it.superName)
      assertTrue(it.interfaces.contains("java.lang.Comparable"))
    }
    pseudoClassLocator.locatePseudoClass("java.util.ArrayList").let {
      assertEquals("java.util.ArrayList", it.name)
      assertEquals("java.util.AbstractList", it.superName)
      assertEquals("java.io.Serializable,java.lang.Cloneable,java.util.List,java.util.RandomAccess",
                   it.interfaces.sorted().joinToString(","))
    }
  }
}