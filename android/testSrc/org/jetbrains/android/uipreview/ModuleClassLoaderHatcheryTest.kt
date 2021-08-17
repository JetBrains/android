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

import com.android.tools.idea.flags.StudioFlags.COMPOSE_CLASSLOADERS_PRELOADING
import com.android.tools.idea.rendering.classloading.toClassTransform
import com.android.tools.idea.testing.AndroidProjectRule
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ModuleClassLoaderHatcheryTest {
  @get:Rule
  val project = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    // Disabling internal ModuleClassLoaderManagerHatchery
    COMPOSE_CLASSLOADERS_PRELOADING.override(false)
  }

  @After
  fun tearDown() {
    COMPOSE_CLASSLOADERS_PRELOADING.clearOverride()
  }

  @Test
  fun `incubates only when needed`() {
    val hatchery = ModuleClassLoaderHatchery(1, 2)

    val donor = ModuleClassLoaderManager.get().getPrivate(
        null, ModuleRenderContext.forModule(project.module), this@ModuleClassLoaderHatcheryTest)

    var requests = 0

    val cloner: (ModuleClassLoader) -> ModuleClassLoader? = { d ->
      requests++
      ModuleClassLoaderManager.get().createCopy(d)
    }

    // Was not requested before => not needed
    assertFalse(hatchery.incubateIfNeeded(donor, cloner))
    assertEquals(0, requests)
    // Cannot be created, no information
    assertNull(hatchery.requestClassLoader(
      null, donor.projectClassesTransformationProvider, donor.nonProjectClassesTransformationProvider))
    // Was requested => will be used
    assertTrue(hatchery.incubateIfNeeded(donor, cloner))
    assertEquals(2, requests)

    assertNotNull(hatchery.requestClassLoader(
      null, donor.projectClassesTransformationProvider, donor.nonProjectClassesTransformationProvider))
    assertEquals(3, requests)

    ModuleClassLoaderManager.get().release(donor, this@ModuleClassLoaderHatcheryTest)
  }

  @Test
  fun `hatchery maintains capacity`() {
    val hatchery = ModuleClassLoaderHatchery(1, 2)

    val donor = ModuleClassLoaderManager.get().getPrivate(
      null, ModuleRenderContext.forModule(project.module), this@ModuleClassLoaderHatcheryTest)

    val projectTransformations = toClassTransform(
      { TestClassVisitorWithId("project-id1") },
    )
    val donor2 = ModuleClassLoaderManager.get().getPrivate(
      null, ModuleRenderContext.forModule(project.module), this@ModuleClassLoaderHatcheryTest, projectTransformations)

    val cloner = ModuleClassLoaderManager.get()::createCopy

    // Nothing at the beginning, provide donor, check that request is successful
    assertNull(hatchery.requestClassLoader(
      null, donor.projectClassesTransformationProvider, donor.nonProjectClassesTransformationProvider))
    assertTrue(hatchery.incubateIfNeeded(donor, cloner))
    assertNotNull(hatchery.requestClassLoader(
      null, donor.projectClassesTransformationProvider, donor.nonProjectClassesTransformationProvider))
    // Request a different one, and provide a different donor, check that request is successful
    assertNull(hatchery.requestClassLoader(
      null, donor2.projectClassesTransformationProvider, donor2.nonProjectClassesTransformationProvider))
    assertTrue(hatchery.incubateIfNeeded(donor2, cloner))
    assertNotNull(hatchery.requestClassLoader(
      null, donor2.projectClassesTransformationProvider, donor2.nonProjectClassesTransformationProvider))
    // Check that due to capacity of 1, the first one is not longer provided
    assertNull(hatchery.requestClassLoader(
      null, donor.projectClassesTransformationProvider, donor.nonProjectClassesTransformationProvider))

    ModuleClassLoaderManager.get().release(donor2, this@ModuleClassLoaderHatcheryTest)
    ModuleClassLoaderManager.get().release(donor, this@ModuleClassLoaderHatcheryTest)
  }
}