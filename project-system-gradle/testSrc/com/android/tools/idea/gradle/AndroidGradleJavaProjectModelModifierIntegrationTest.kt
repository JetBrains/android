/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.roots.JavaProjectModelModificationService
import com.intellij.openapi.roots.impl.ModuleOrderEnumerator
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.junit.Test
import org.junit.runners.Parameterized
import java.util.concurrent.TimeUnit

class AndroidGradleJavaProjectModelModifierIntegrationTest : GradleImportingTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun testAddDependencyToProjectWithNoAndroid() {
    importProject("apply plugin: 'java'")

    val modificationService = JavaProjectModelModificationService.getInstance(myProject)
    val module = ModuleManager.getInstance(myProject).findModuleByName("project.test")!!
    val dependencyPromise = modificationService.addDependency(module, ExternalLibraryDescriptor("junit", "junit", "4.13", null, "4.13"))
    dependencyPromise.blockingGet(90, TimeUnit.SECONDS)
    assertTrue(dependencyPromise.isSucceeded)

    val hasAddedDependency = checkModuleHasLibrary(module, "Gradle: junit:junit:4.13")
    assertTrue(hasAddedDependency)
  }

  private fun checkModuleHasLibrary(module: Module, libraryName: String): Boolean {
    var hasAddedDependency = false
    ModuleOrderEnumerator.orderEntries(module).forEachLibrary { lib ->
      if (lib.name == libraryName) hasAddedDependency = true
      return@forEachLibrary !hasAddedDependency
    }
    return hasAddedDependency
  }

  companion object {
    /**
     * It's sufficient to run the test against one gradle version
     */
    @Parameterized.Parameters(name = "with Gradle-{0}")
    @JvmStatic
    fun tests(): Collection<Array<out String>> = arrayListOf(arrayOf(GradleImportingTestCase.BASE_GRADLE_VERSION))
  }
}