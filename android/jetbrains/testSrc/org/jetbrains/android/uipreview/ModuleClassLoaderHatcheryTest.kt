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
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.rendering.classloading.ClassTransform
import com.android.tools.rendering.classloading.FirewalledResourcesClassLoader
import com.android.tools.rendering.classloading.toClassTransform
import com.android.tools.rendering.classloading.useWithClassLoader
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ModuleClassLoaderHatcheryTest {
  @get:Rule val project = AndroidProjectRule.inMemory()

  @Test
  fun `incubates only when needed`() {
    val hatchery = ModuleClassLoaderHatchery(1, 2, parentDisposable = project.testRootDisposable)

    StudioModuleClassLoaderManager.get().getPrivate(null, StudioModuleRenderContext.forModule(project.module)).use { reference ->
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
      assertNull(hatchery.requestClassLoader(null, donor.projectClassesTransform, donor.nonProjectClassesTransform))
      // Was requested => will be used
      assertTrue(hatchery.incubateIfNeeded(studioModuleClassLoaderCreationContext, cloner))
      assertEquals(2, requests)

      assertNotNull(hatchery.requestClassLoader(null, donor.projectClassesTransform, donor.nonProjectClassesTransform))
      assertEquals(3, requests)
    }
  }

  @Test
  fun `hatchery maintains capacity`() {
    val hatchery = ModuleClassLoaderHatchery(1, 2, parentDisposable = project.testRootDisposable)

    StudioModuleClassLoaderManager.get().getPrivate(null, StudioModuleRenderContext.forModule(project.module)).useWithClassLoader { donor ->
      val studioModuleClassLoaderCreationContext = StudioModuleClassLoaderCreationContext.fromClassLoaderOrThrow(donor)

      val projectTransformations = toClassTransform({ TestClassVisitorWithId("project-id1") })
      StudioModuleClassLoaderManager.get()
        .getPrivate(null, StudioModuleRenderContext.forModule(project.module), projectTransformations)
        .useWithClassLoader { donor2 ->
          val donor2Information = StudioModuleClassLoaderCreationContext.fromClassLoaderOrThrow(donor2)

          val cloner: (StudioModuleClassLoaderCreationContext) -> StudioModuleClassLoader? = { d -> d.createClassLoader() }
          // Nothing at the beginning, provide donor, check that request is successful
          assertNull(hatchery.requestClassLoader(null, donor.projectClassesTransform, donor.nonProjectClassesTransform))
          assertTrue(hatchery.incubateIfNeeded(studioModuleClassLoaderCreationContext, cloner))
          assertNotNull(hatchery.requestClassLoader(null, donor.projectClassesTransform, donor.nonProjectClassesTransform))
          // Request a different one, and provide a different donor, check that request is successful
          assertNull(hatchery.requestClassLoader(null, donor2.projectClassesTransform, donor2.nonProjectClassesTransform))
          assertTrue(hatchery.incubateIfNeeded(donor2Information, cloner))
          assertNotNull(hatchery.requestClassLoader(null, donor2.projectClassesTransform, donor2.nonProjectClassesTransform))
          // Check that due to capacity of 1, the first one is not longer provided
          assertNull(hatchery.requestClassLoader(null, donor.projectClassesTransform, donor.nonProjectClassesTransform))
        }
    }
  }

  @Test
  fun `hatchery correctly identifies different parent class loaders`() {
    val hatchery = ModuleClassLoaderHatchery(1, 2, parentDisposable = project.testRootDisposable)
    val parent1 = FirewalledResourcesClassLoader(null)
    StudioModuleClassLoaderManager.get().getPrivate(parent1, StudioModuleRenderContext.forModule(project.module)).useWithClassLoader { donor
      ->
      val cloner: (StudioModuleClassLoaderCreationContext) -> StudioModuleClassLoader? = { d -> d.createClassLoader() }
      // Create a request for a new class loader and incubate it
      assertNull(hatchery.requestClassLoader(parent1, donor.projectClassesTransform, donor.nonProjectClassesTransform))
      assertTrue(hatchery.incubateIfNeeded(StudioModuleClassLoaderCreationContext.fromClassLoaderOrThrow(donor), cloner))
      // This request has the same Request so it should return a new classloader
      assertNotNull(hatchery.requestClassLoader(parent1, donor.projectClassesTransform, donor.nonProjectClassesTransform))
      // This request is using a different parent, we should not have anything available and should return null
      val parent2 = FirewalledResourcesClassLoader(null)
      assertNull(hatchery.requestClassLoader(parent2, donor.projectClassesTransform, donor.nonProjectClassesTransform))
    }
  }

  @Test
  fun `hatchery does not mix null and non-null parent requests`() {
    val hatchery = ModuleClassLoaderHatchery(1, 1, parentDisposable = project.testRootDisposable)
    val parent = FirewalledResourcesClassLoader(null)

    StudioModuleClassLoaderManager.get().getPrivate(parent, StudioModuleRenderContext.forModule(project.module)).useWithClassLoader { donor
      ->
      val creationContext = StudioModuleClassLoaderCreationContext.fromClassLoaderOrThrow(donor)
      val cloner: (StudioModuleClassLoaderCreationContext) -> StudioModuleClassLoader? = { d -> d.createClassLoader() }

      // 1. Request with null parent
      assertNull(hatchery.requestClassLoader(null, donor.projectClassesTransform, donor.nonProjectClassesTransform))

      // 2. Try to incubate with a donor that has a non-null parent.
      // Under buggy Request.equals contract, it would match and return true.
      // Under correct contract, it should return false because they have different parent class loaders.
      assertFalse(hatchery.incubateIfNeeded(creationContext, cloner))
    }
  }

  @Test
  fun `clutch retrieval handles GCed classloader gracefully`() {
    val hatchery = ModuleClassLoaderHatchery(capacity = 1, copies = 2, parentDisposable = project.testRootDisposable)

    StudioModuleClassLoaderManager.get().getPrivate(null, StudioModuleRenderContext.forModule(project.module)).useWithClassLoader { donor ->
      val creationContext = StudioModuleClassLoaderCreationContext.fromClassLoaderOrThrow(donor)
      val cloner: (StudioModuleClassLoaderCreationContext) -> StudioModuleClassLoader? = { d -> d.createClassLoader() }

      // 1. Record the request
      assertNull(hatchery.requestClassLoader(null, donor.projectClassesTransform, donor.nonProjectClassesTransform))

      // 2. Incubate a clutch with 2 copies
      assertTrue(hatchery.incubateIfNeeded(creationContext, cloner))

      // 3. Clear/dispose the first copy's classloader to simulate GC
      hatchery.disposeFirstEggForTesting()

      // 4. Request classloader. Under buggy code, sequence terminates and returns null.
      // Under correct code, the second copy is successfully retrieved and returned.
      val retrieved = hatchery.requestClassLoader(null, donor.projectClassesTransform, donor.nonProjectClassesTransform)
      assertNotNull(retrieved)
    }
  }

  @Test
  fun `hatchery limits the number of pending requests`() {
    val hatchery = ModuleClassLoaderHatchery(capacity = 1, copies = 1, maxRequestsSize = 5, parentDisposable = project.testRootDisposable)

    // Request many unique configurations
    repeat(50) { i ->
      val projectTransform = toClassTransform({ TestClassVisitorWithId("project-$i") })
      hatchery.requestClassLoader(null, projectTransform, ClassTransform.identity)
    }

    // Verify that the size of pending requests is bounded to the configured limit
    assertEquals(5, hatchery.getRequestsSizeForTesting())
  }

  @Test
  fun `clutch fallback compatibility check during background preloading`() {
    val commands = mutableListOf<Runnable>()
    val delayedExecutor = Executor { command -> commands.add(command) }

    val hatchery =
      ModuleClassLoaderHatchery(capacity = 1, copies = 1, executor = delayedExecutor, parentDisposable = project.testRootDisposable)

    StudioModuleClassLoaderManager.get().getPrivate(null, StudioModuleRenderContext.forModule(project.module)).useWithClassLoader { donor ->
      val creationContext = StudioModuleClassLoaderCreationContext.fromClassLoaderOrThrow(donor)
      val cloner: (StudioModuleClassLoaderCreationContext) -> StudioModuleClassLoader? = { d -> d.createClassLoader() }

      // 1. Record the request
      assertNull(hatchery.requestClassLoader(null, donor.projectClassesTransform, donor.nonProjectClassesTransform))

      // 2. Incubate. This will submit the preloading command to our delayedExecutor, so eggs queue remains empty!
      assertTrue(hatchery.incubateIfNeeded(creationContext, cloner))

      // 3. Request classloader again. Since eggs queue is empty, isCompatible should fall back to donor metadata check
      // and find it compatible, but return null since no copies are fully prepared yet.
      // We verify that it does not add a new request by incubating again with the same donor.
      // If it recorded a new request, incubateIfNeeded would return true. If it found it compatible (and didn't record), it returns false.
      assertFalse(hatchery.incubateIfNeeded(creationContext, cloner))
    }
  }
}
