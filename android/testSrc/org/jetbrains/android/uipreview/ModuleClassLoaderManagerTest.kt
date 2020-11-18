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

import com.android.tools.idea.rendering.classloading.ClassVisitorUniqueIdProvider
import com.android.tools.idea.rendering.classloading.toClassTransform
import com.android.tools.idea.testing.AndroidProjectRule
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertNotNull

private class TestClassVisitorWithId(val id: String): ClassVisitor(Opcodes.ASM7, null), ClassVisitorUniqueIdProvider {
  override val uniqueId: String = id
}

class ModuleClassLoaderManagerTest {
  @get:Rule
  val project = AndroidProjectRule.inMemory()

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
      moduleClassLoader = ModuleClassLoaderManager.get()
        .getShared(null, project.module, this, projectTransformations, nonProjectTransformations)
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
      val newClassLoader = ModuleClassLoaderManager.get()
        .getShared(null, project.module, this, projectTransformations, nonProjectTransformations)
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
      val newClassLoader = ModuleClassLoaderManager.get()
        .getShared(null, project.module, this, projectTransformations, nonProjectTransformations)
      assertNotNull(newClassLoader)
      assertNotEquals(newClassLoader, moduleClassLoader)
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
      val newClassLoader = ModuleClassLoaderManager.get()
        .getShared(null, project.module, this, projectTransformations, nonProjectTransformations)
      assertNotNull(newClassLoader)
      assertNotEquals(newClassLoader, moduleClassLoader)
      moduleClassLoader = newClassLoader
    }
  }
}