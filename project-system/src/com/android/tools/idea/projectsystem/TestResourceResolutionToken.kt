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
package com.android.tools.idea.projectsystem

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module

/**
 * A token that allows project systems to provide additional resource dependencies and scopes for test modules during resource resolution.
 *
 * This is particularly useful for features like screenshot testing where a test module needs to resolve and render resources from its
 * production counterpart.
 */
interface TestResourceResolutionToken<P : AndroidProjectSystem> : Token {
  /**
   * Returns a list of additional [Module]s whose local resources should be included when resolving resources for the given [moduleSystem].
   *
   * Build system implementations should return any modules that are required for the test module to render or resolve correctly, but are
   * not part of the standard production dependency graph. For example, in Gradle screenshot testing, this would include the production
   * module being tested.
   */
  fun computeAdditionalLocalResourceDependencies(moduleSystem: AndroidModuleSystem): List<Module>

  /**
   * Returns the [DependencyScopeType] that should be used when fetching Android library dependencies (AARs) for the given [moduleSystem]
   * during test resource resolution.
   *
   * Build system implementations should return the scope that corresponds to the test libraries for this module (e.g.,
   * [DependencyScopeType.ANDROID_TEST] or [DependencyScopeType.SCREENSHOT_TEST]).
   *
   * Returns null if the default scope should be used.
   */
  fun computeTestResourceLibraryScope(moduleSystem: AndroidModuleSystem): DependencyScopeType?

  companion object {
    @JvmStatic
    val EP_NAME =
      ExtensionPointName<TestResourceResolutionToken<AndroidProjectSystem>>(
        "com.android.tools.idea.projectsystem.testResourceResolutionToken"
      )

    /**
     * Returns a list of additional [Module]s whose local resources should be included when resolving resources for the given
     * [moduleSystem], by querying the [TestResourceResolutionToken].
     */
    @JvmStatic
    fun getAdditionalLocalResourceDependencies(moduleSystem: AndroidModuleSystem): List<Module> {
      return moduleSystem.module.project
        .getProjectSystem()
        .getTokenOrNull(EP_NAME)
        ?.computeAdditionalLocalResourceDependencies(moduleSystem) ?: emptyList()
    }

    /**
     * Returns the [DependencyScopeType] that should be used when fetching Android library dependencies (AARs) for the given [moduleSystem]
     * during test resource resolution, by querying the [TestResourceResolutionToken].
     *
     * Defaults to [DependencyScopeType.ANDROID_TEST] if no token is found.
     */
    @JvmStatic
    fun getTestResourceLibraryScope(moduleSystem: AndroidModuleSystem): DependencyScopeType {
      return moduleSystem.module.project.getProjectSystem().getTokenOrNull(EP_NAME)?.computeTestResourceLibraryScope(moduleSystem)
        ?: DependencyScopeType.ANDROID_TEST
    }
  }
}
