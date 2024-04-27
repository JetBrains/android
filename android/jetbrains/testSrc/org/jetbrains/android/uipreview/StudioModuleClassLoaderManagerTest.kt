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

import com.android.tools.idea.rendering.StudioModuleRenderContext
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.rendering.classloading.ModuleClassLoader
import com.android.tools.rendering.classloading.ModuleClassLoaderManager
import com.android.tools.rendering.classloading.NopModuleClassLoadedDiagnostics
import com.android.tools.rendering.classloading.toClassTransform
import com.android.tools.rendering.classloading.useWithClassLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StudioModuleClassLoaderManagerTest {
  @get:Rule
  val project = AndroidProjectRule.inMemory()

  @After
  fun testDown() {
    val moduleClassLoader =  ModuleClassLoaderManager.get() as StudioModuleClassLoaderManager
    StudioModuleClassLoaderManager.setCaptureClassLoadingDiagnostics(false)
    moduleClassLoader.assertNoClassLoadersHeld()
  }

  @Test
  fun `shared class loader gets invalidated for different transformations`() {
    var moduleClassLoaderReference: ModuleClassLoaderManager.Reference<*>? = null
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
      moduleClassLoaderReference = ModuleClassLoaderManager.get()
        .getShared(null, StudioModuleRenderContext.forModule(project.module), projectTransformations, nonProjectTransformations)
      moduleClassLoader = moduleClassLoaderReference!!.classLoader
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
      ModuleClassLoaderManager.get()
        .getShared(null, StudioModuleRenderContext.forModule(project.module), projectTransformations, nonProjectTransformations).useWithClassLoader {
          assertEquals("No changes into the transformations. Same class loader was expected", it, moduleClassLoader)
        }
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
      ModuleClassLoaderManager.get()
        .getShared(null, StudioModuleRenderContext.forModule(project.module),  projectTransformations, nonProjectTransformations).also { newReference ->
          assertNotEquals(newReference.classLoader, moduleClassLoader)
          ModuleClassLoaderManager.get().release(moduleClassLoaderReference!!)
          moduleClassLoaderReference = newReference
        }
    }

    run {
      // Remove non-project transformation, it should generate a new one.
      val projectTransformations = toClassTransform(
        { TestClassVisitorWithId("project-id1") }
      )
      val nonProjectTransformations = toClassTransform(
        { TestClassVisitorWithId("non-project-id1") }
      )
      val newClassLoader = ModuleClassLoaderManager.get()
        .getShared(null, StudioModuleRenderContext.forModule(project.module), projectTransformations, nonProjectTransformations).useWithClassLoader { newClassLoader ->
          assertNotEquals(newClassLoader, moduleClassLoader)
        }
      ModuleClassLoaderManager.get().release(moduleClassLoaderReference!!)
    }
  }

  @Test
  fun `ensure stats are not activated accidentally`() {
    ModuleClassLoaderManager.get().getShared(null, StudioModuleRenderContext.forModule(project.module)).use { sharedClassLoaderReference ->
      run {
        assertTrue(sharedClassLoaderReference.classLoader.stats is NopModuleClassLoadedDiagnostics)
        ModuleClassLoaderManager.get().getPrivate(null, StudioModuleRenderContext.forModule(project.module)).useWithClassLoader { privateClassLoader ->
          assertTrue(privateClassLoader.stats is NopModuleClassLoadedDiagnostics)
        }
      }

      StudioModuleClassLoaderManager.setCaptureClassLoadingDiagnostics(true)
      // Destroying hatchery so that getPrivate returns freshly-created ModuleClassLoader that respects diagnostics settings change
      project.module.getUserData(HATCHERY)?.destroy()

      run {
        // The shared class loader will be reused so, even though we are reactivating the diagnostics,
        // it should be not using them.
        ModuleClassLoaderManager.get().getShared(null, StudioModuleRenderContext.forModule(project.module)).useWithClassLoader { sharedClassLoader ->
          assertTrue(sharedClassLoader.stats is NopModuleClassLoadedDiagnostics)
        }
        ModuleClassLoaderManager.get().getPrivate(null, StudioModuleRenderContext.forModule(project.module)).useWithClassLoader { privateClassLoader ->
          assertFalse(privateClassLoader.stats is NopModuleClassLoadedDiagnostics)
        }
      }
    }
  }
}