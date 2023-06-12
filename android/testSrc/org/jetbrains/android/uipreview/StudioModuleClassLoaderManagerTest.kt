/*
 * Copyright (C) 2020 The Android Open Source Project
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
package org.jetbrains.android.uipreview

import com.android.tools.idea.rendering.classloading.toClassTransform
import com.android.tools.idea.testing.AndroidProjectRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertNotNull

class StudioModuleClassLoaderManagerTest {
  @get:Rule
  val project = AndroidProjectRule.inMemory()

  @After
  fun testDown() {
    StudioModuleClassLoaderManager.get().setCaptureClassLoadingDiagnostics(false)
    if (StudioModuleClassLoaderManager.get().hasAllocatedSharedClassLoaders()) {
      fail("Class loaders were not released correctly by the tests")
    }
  }

  @Test
  fun `shared class loader gets invalidated for different transformations`() {
    var moduleClassLoader: ModuleClassLoader? = null
    run {
      val projectTransformations = toClassTransform(
        { TestClassVisitorWithId("project-id1") },
        { TestClassVisitorWithId("project-id2") }
      )
      val nonProjectTransformations = toClassTransform(
        { TestClassVisitorWithId("non-project-id1") },
        { TestClassVisitorWithId("non-project-id2") }
      )
      moduleClassLoader = StudioModuleClassLoaderManager.get()
        .getShared(null, ModuleRenderContext.forModule(project.module), this@StudioModuleClassLoaderManagerTest, projectTransformations, nonProjectTransformations)
      assertNotNull(moduleClassLoader)
    }

    run {
      // Same transformations, no new class loader.
      val projectTransformations = toClassTransform(
        { TestClassVisitorWithId("project-id1") },
        { TestClassVisitorWithId("project-id2") }
      )
      val nonProjectTransformations = toClassTransform(
        { TestClassVisitorWithId("non-project-id1") },
        { TestClassVisitorWithId("non-project-id2") }
      )
      val newClassLoader = StudioModuleClassLoaderManager.get()
        .getShared(null, ModuleRenderContext.forModule(project.module), this@StudioModuleClassLoaderManagerTest, projectTransformations, nonProjectTransformations)
      assertEquals("No changes into the transformations. Same class loader was expected", newClassLoader, moduleClassLoader)
    }

    run {
      // Remove project transformation, it should generate a new one.
      val projectTransformations = toClassTransform(
        { TestClassVisitorWithId("project-id1") }
      )
      val nonProjectTransformations = toClassTransform(
        { TestClassVisitorWithId("non-project-id1") },
        { TestClassVisitorWithId("non-project-id2") }
      )
      val newClassLoader = StudioModuleClassLoaderManager.get()
        .getShared(null, ModuleRenderContext.forModule(project.module), this@StudioModuleClassLoaderManagerTest, projectTransformations, nonProjectTransformations)
      assertNotNull(newClassLoader)
      assertNotEquals(newClassLoader, moduleClassLoader)
      StudioModuleClassLoaderManager.get().release(moduleClassLoader!!, this@StudioModuleClassLoaderManagerTest)
      moduleClassLoader = newClassLoader
    }

    run {
      // Remove non-project transformation, it should generate a new one.
      val projectTransformations = toClassTransform(
        { TestClassVisitorWithId("project-id1") }
      )
      val nonProjectTransformations = toClassTransform(
        { TestClassVisitorWithId("non-project-id1") }
      )
      val newClassLoader = StudioModuleClassLoaderManager.get()
        .getShared(null, ModuleRenderContext.forModule(project.module), this, projectTransformations, nonProjectTransformations)
      assertNotNull(newClassLoader)
      assertNotEquals(newClassLoader, moduleClassLoader)
      StudioModuleClassLoaderManager.get().release(moduleClassLoader!!, this@StudioModuleClassLoaderManagerTest)
      StudioModuleClassLoaderManager.get().release(newClassLoader, this@StudioModuleClassLoaderManagerTest)
    }
  }

  @Test
  fun `ensure stats are not activated accidentally`() {
    run {
      val sharedClassLoader = StudioModuleClassLoaderManager.get().getShared(null, ModuleRenderContext.forModule(project.module), this@StudioModuleClassLoaderManagerTest)
      assertTrue(sharedClassLoader.stats is NopModuleClassLoadedDiagnostics)
      val privateClassLoader = StudioModuleClassLoaderManager.get().getPrivate(null, ModuleRenderContext.forModule(project.module), this@StudioModuleClassLoaderManagerTest)
      assertTrue(privateClassLoader.stats is NopModuleClassLoadedDiagnostics)
      StudioModuleClassLoaderManager.get().release(privateClassLoader, this@StudioModuleClassLoaderManagerTest)
    }

    StudioModuleClassLoaderManager.get().setCaptureClassLoadingDiagnostics(true)
    // Destroying hatchery so that getPrivate returns freshly-created ModuleClassLoader that respects diagnostics settings change
    project.module.getUserData(HATCHERY)?.destroy()

    run {
      // The shared class loader will be reused so, even though we are reactivating the diagnostics,
      // it should be not using them.
      val sharedClassLoader = StudioModuleClassLoaderManager.get().getShared(null, ModuleRenderContext.forModule(project.module), this@StudioModuleClassLoaderManagerTest)
      assertTrue(sharedClassLoader.stats is NopModuleClassLoadedDiagnostics)
      val privateClassLoader = StudioModuleClassLoaderManager.get().getPrivate(null, ModuleRenderContext.forModule(project.module), this@StudioModuleClassLoaderManagerTest)
      assertFalse(privateClassLoader.stats is NopModuleClassLoadedDiagnostics)
      StudioModuleClassLoaderManager.get().release(sharedClassLoader, this@StudioModuleClassLoaderManagerTest)
      StudioModuleClassLoaderManager.get().release(privateClassLoader, this@StudioModuleClassLoaderManagerTest)
    }
  }
}