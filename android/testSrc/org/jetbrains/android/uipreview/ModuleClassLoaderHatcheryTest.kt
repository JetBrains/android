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

import com.android.tools.idea.rendering.classloading.FirewalledResourcesClassLoader
import com.android.tools.idea.rendering.classloading.toClassTransform
import com.android.tools.idea.testing.AndroidProjectRule
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
    val hatchery = ModuleClassLoaderHatchery(1, 2)

    val donor = StudioModuleClassLoaderManager.get().getPrivate(
        null, ModuleRenderContext.forModule(project.module), this@ModuleClassLoaderHatcheryTest)

    var requests = 0

    val cloner: (ModuleClassLoader) -> ModuleClassLoader? = { d ->
      requests++
      StudioModuleClassLoaderManager.get().createCopy(d)
    }

    // Was not requested before => not needed
    assertFalse(hatchery.incubateIfNeeded(donor, cloner))
    assertEquals(0, requests)
    // Cannot be created, no information
    assertNull(hatchery.requestClassLoader(
      null, donor.projectClassesTransform, donor.nonProjectClassesTransform))
    // Was requested => will be used
    assertTrue(hatchery.incubateIfNeeded(donor, cloner))
    assertEquals(2, requests)

    assertNotNull(hatchery.requestClassLoader(
      null, donor.projectClassesTransform, donor.nonProjectClassesTransform))
    assertEquals(3, requests)

    StudioModuleClassLoaderManager.get().release(donor, this@ModuleClassLoaderHatcheryTest)
  }

  @Test
  fun `hatchery maintains capacity`() {
    val hatchery = ModuleClassLoaderHatchery(1, 2)

    val donor = StudioModuleClassLoaderManager.get().getPrivate(
      null, ModuleRenderContext.forModule(project.module), this@ModuleClassLoaderHatcheryTest)

    val projectTransformations = toClassTransform(
      { TestClassVisitorWithId("project-id1") },
    )
    val donor2 = StudioModuleClassLoaderManager.get().getPrivate(
      null, ModuleRenderContext.forModule(project.module), this@ModuleClassLoaderHatcheryTest, projectTransformations)

    val cloner = StudioModuleClassLoaderManager.get()::createCopy

    // Nothing at the beginning, provide donor, check that request is successful
    assertNull(hatchery.requestClassLoader(
      null, donor.projectClassesTransform, donor.nonProjectClassesTransform))
    assertTrue(hatchery.incubateIfNeeded(donor, cloner))
    assertNotNull(hatchery.requestClassLoader(
      null, donor.projectClassesTransform, donor.nonProjectClassesTransform))
    // Request a different one, and provide a different donor, check that request is successful
    assertNull(hatchery.requestClassLoader(
      null, donor2.projectClassesTransform, donor2.nonProjectClassesTransform))
    assertTrue(hatchery.incubateIfNeeded(donor2, cloner))
    assertNotNull(hatchery.requestClassLoader(
      null, donor2.projectClassesTransform, donor2.nonProjectClassesTransform))
    // Check that due to capacity of 1, the first one is not longer provided
    assertNull(hatchery.requestClassLoader(
      null, donor.projectClassesTransform, donor.nonProjectClassesTransform))

    StudioModuleClassLoaderManager.get().release(donor2, this@ModuleClassLoaderHatcheryTest)
    StudioModuleClassLoaderManager.get().release(donor, this@ModuleClassLoaderHatcheryTest)
  }

  @Test
  fun `hatchery correctly identifies different parent class loaders`() {
    val hatchery = ModuleClassLoaderHatchery(1, 2)
    val parent1 = FirewalledResourcesClassLoader(null)
    val donor = StudioModuleClassLoaderManager.get().getPrivate(
      parent1, ModuleRenderContext.forModule(project.module), this@ModuleClassLoaderHatcheryTest)
    val cloner = StudioModuleClassLoaderManager.get()::createCopy
    // Create a request for a new class loader and incubate it
    assertNull(hatchery.requestClassLoader(
      parent1, donor.projectClassesTransform, donor.nonProjectClassesTransform))
    assertTrue(hatchery.incubateIfNeeded(donor, cloner))
    // This request has the same Request so it should return a new classloader
    assertNotNull(hatchery.requestClassLoader(
      parent1, donor.projectClassesTransform, donor.nonProjectClassesTransform))
    // This request is using a different parent, we should not have anything available and should return null
    val parent2 = FirewalledResourcesClassLoader(null)
    assertNull(hatchery.requestClassLoader(
      parent2, donor.projectClassesTransform, donor.nonProjectClassesTransform))

    StudioModuleClassLoaderManager.get().release(donor, this@ModuleClassLoaderHatcheryTest)
  }
}