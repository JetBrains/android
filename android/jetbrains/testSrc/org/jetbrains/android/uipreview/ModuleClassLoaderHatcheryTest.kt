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
package org.jetbrains.android.uipreview

import com.android.tools.idea.rendering.StudioModuleRenderContext
import com.android.tools.idea.rendering.classloading.FirewalledResourcesClassLoader
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.rendering.classloading.toClassTransform
import com.android.tools.rendering.classloading.useWithClassLoader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ModuleClassLoaderHatcheryTest {
  @get:Rule
  val project = AndroidProjectRule.inMemory()

  @Test
  fun `incubates only when needed`() {
    val hatchery = ModuleClassLoaderHatchery(1, 2, parentDisposable = project.testRootDisposable)

    StudioModuleClassLoaderManager.get().getPrivate(
      null, StudioModuleRenderContext.forModule(project.module)).use { reference ->
      val donor = reference.classLoader
      val studioModuleClassLoaderCreationContext = StudioModuleClassLoaderCreationContext.fromClassLoaderOrThrow(donor)

      var requests = 0

      val cloner: (StudioModuleClassLoaderCreationContext) -> StudioModuleClassLoader? = { d ->
        requests++
        d.createClassLoader()
      }

      // Was not requested before => not needed
      assertFalse(hatchery.incubateIfNeeded(studioModuleClassLoaderCreationContext, cloner))
      assertEquals(0, requests)
      // Cannot be created, no information
      assertNull(hatchery.requestClassLoader(
        null, donor.projectClassesTransform, donor.nonProjectClassesTransform))
      // Was requested => will be used
      assertTrue(hatchery.incubateIfNeeded(studioModuleClassLoaderCreationContext, cloner))
      assertEquals(2, requests)

      assertNotNull(hatchery.requestClassLoader(
        null, donor.projectClassesTransform, donor.nonProjectClassesTransform))
      assertEquals(3, requests)
    }
  }

  @Test
  fun `hatchery maintains capacity`() {
    val hatchery = ModuleClassLoaderHatchery(1, 2, parentDisposable = project.testRootDisposable)

    StudioModuleClassLoaderManager.get().getPrivate(
      null, StudioModuleRenderContext.forModule(project.module)).useWithClassLoader { donor ->
      val studioModuleClassLoaderCreationContext = StudioModuleClassLoaderCreationContext.fromClassLoaderOrThrow(donor)

      val projectTransformations = toClassTransform(
        { TestClassVisitorWithId("project-id1") },
      )
      StudioModuleClassLoaderManager.get().getPrivate(
        null, StudioModuleRenderContext.forModule(project.module), projectTransformations).useWithClassLoader { donor2 ->
        val donor2Information = StudioModuleClassLoaderCreationContext.fromClassLoaderOrThrow(donor2)

        val cloner: (StudioModuleClassLoaderCreationContext) -> StudioModuleClassLoader? = { d ->
          d.createClassLoader()
        }
        // Nothing at the beginning, provide donor, check that request is successful
        assertNull(hatchery.requestClassLoader(
          null, donor.projectClassesTransform, donor.nonProjectClassesTransform))
        assertTrue(hatchery.incubateIfNeeded(studioModuleClassLoaderCreationContext, cloner))
        assertNotNull(hatchery.requestClassLoader(
          null, donor.projectClassesTransform, donor.nonProjectClassesTransform))
        // Request a different one, and provide a different donor, check that request is successful
        assertNull(hatchery.requestClassLoader(
          null, donor2.projectClassesTransform, donor2.nonProjectClassesTransform))
        assertTrue(hatchery.incubateIfNeeded(donor2Information, cloner))
        assertNotNull(hatchery.requestClassLoader(
          null, donor2.projectClassesTransform, donor2.nonProjectClassesTransform))
        // Check that due to capacity of 1, the first one is not longer provided
        assertNull(hatchery.requestClassLoader(
          null, donor.projectClassesTransform, donor.nonProjectClassesTransform))
      }
    }
  }

  @Test
  fun `hatchery correctly identifies different parent class loaders`() {
    val hatchery = ModuleClassLoaderHatchery(1, 2, parentDisposable = project.testRootDisposable)
    val parent1 = FirewalledResourcesClassLoader(null)
    StudioModuleClassLoaderManager.get().getPrivate(
      parent1, StudioModuleRenderContext.forModule(project.module)).useWithClassLoader {  donor ->
      val cloner: (StudioModuleClassLoaderCreationContext) -> StudioModuleClassLoader? = { d ->
        d.createClassLoader()
      }
      // Create a request for a new class loader and incubate it
      assertNull(hatchery.requestClassLoader(
        parent1, donor.projectClassesTransform, donor.nonProjectClassesTransform))
      assertTrue(hatchery.incubateIfNeeded(StudioModuleClassLoaderCreationContext.fromClassLoaderOrThrow(donor), cloner))
      // This request has the same Request so it should return a new classloader
      assertNotNull(hatchery.requestClassLoader(
        parent1, donor.projectClassesTransform, donor.nonProjectClassesTransform))
      // This request is using a different parent, we should not have anything available and should return null
      val parent2 = FirewalledResourcesClassLoader(null)
      assertNull(hatchery.requestClassLoader(
        parent2, donor.projectClassesTransform, donor.nonProjectClassesTransform))
    }
  }
}