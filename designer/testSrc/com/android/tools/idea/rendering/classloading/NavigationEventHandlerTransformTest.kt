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
package com.android.tools.idea.rendering.classloading

import com.android.testutils.TestUtils
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.rendering.classloading.NopClassLocator
import com.android.tools.rendering.classloading.loaders.AsmTransformingLoader
import com.android.tools.rendering.classloading.loaders.ClassLoaderLoader
import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader
import com.android.tools.rendering.classloading.toClassTransform
import org.jetbrains.android.uipreview.createUrlClassLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private const val LOADER_WORKSPACE_PATH = "tools/adt/idea/designer/testData/classloader"
private const val CLASSES_TO_LOAD_JAR_NAME = "localnavigationevent.jar"

/**
 * Tests for [NavigationEventHandlerTransform].
 *
 * NOTE:
 * - The test loads `localnavigationevent.jar` from test data. The source for this JAR (`NavigationEventHandler.android.kt`) is located in
 *   the testData files and it contains all the Classes needed to correctly run [NavigationEventHandlerTransform].
 * - The `localnavigationevent.jar` is created by the `generate_localnavigationevent_jar.sh` script.
 */
class NavigationEventHandlerTransformTest {

  private lateinit var navigationEventHandlerClass: Class<*>
  private lateinit var fakeComposer: Any

  @Before
  fun setUp() {
    StudioFlags.COMPOSE_INTERACTIVE_PREVIEW_PREDICTIVE_BACK.override(true)
    val loader = NavigationEventHandlerLoader()
    navigationEventHandlerClass = loader.loadClass("androidx.navigationevent.compose.NavigationEventHandler_androidKt")
    fakeComposer = loader.loadClass("androidx.compose.runtime.FakeComposer").getDeclaredConstructor().newInstance()
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_INTERACTIVE_PREVIEW_PREDICTIVE_BACK.clearOverride()
  }

  @Test
  fun `isInspectionMode_Original doesn't exist when flag is off`() {
    StudioFlags.COMPOSE_INTERACTIVE_PREVIEW_PREDICTIVE_BACK.override(false)
    val loader = NavigationEventHandlerLoader()
    val navigationEventHandlerClass = loader.loadClass("androidx.navigationevent.compose.NavigationEventHandler_androidKt")
    assertNull(navigationEventHandlerClass.declaredMethods.firstOrNull { it.name == "isInspectionMode_Original" })
    StudioFlags.COMPOSE_INTERACTIVE_PREVIEW_PREDICTIVE_BACK.clearOverride()
  }

  @Test
  fun `isInspectionMode returns false after transformation`() {
    // Original implementation in test data returns true
    val isInspectionModeMethod = navigationEventHandlerClass.declaredMethods.first { it.name == "isInspectionMode" }
    val result = isInspectionModeMethod.invoke(null, fakeComposer, 0)
    assertEquals(false, result)
  }

  @Test
  fun `transform is not applied to other classes`() {
    val loader = NavigationEventHandlerLoader()
    // LocalNavigationEventDispatcherOwner is a different class in the same jar
    val navigationEventHandlerClass = loader.loadClass("androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner")
    // It should NOT have isInspectionMode_Original renamed or added
    assertNull(navigationEventHandlerClass.declaredMethods.firstOrNull { it.name == "isInspectionMode_Original" })
  }

  @Test
  fun `transform is not applied to other methods`() {
    val loader = NavigationEventHandlerLoader()
    val navigationEventHandlerClass = loader.loadClass("androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner")
    assertNotNull(navigationEventHandlerClass.declaredMethods.firstOrNull { it.name == "getCurrent" })
    assertNull(navigationEventHandlerClass.declaredMethods.firstOrNull { it.name == "getCurrent_Original" })
  }

  inner class NavigationEventHandlerLoader :
    DelegatingClassLoader(
      object : ClassLoader(this.javaClass.classLoader) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
          if (name.startsWith("androidx.compose.runtime")) {
            throw ClassNotFoundException(name)
          }
          return super.loadClass(name, resolve)
        }
      },
      AsmTransformingLoader(
        toClassTransform({ NavigationEventHandlerTransform(it) }),
        ClassLoaderLoader(
          createUrlClassLoader(listOf(TestUtils.resolveWorkspacePath(LOADER_WORKSPACE_PATH).resolve(CLASSES_TO_LOAD_JAR_NAME)))
        ),
        NopClassLocator,
      ),
    )
}
