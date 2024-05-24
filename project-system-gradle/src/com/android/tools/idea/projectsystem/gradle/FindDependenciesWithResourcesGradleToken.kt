/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle

import com.android.projectmodel.ExternalAndroidLibrary
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.FindDependenciesWithResourcesToken
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.isAndroidTestModule
import com.android.tools.idea.projectsystem.isMainModule
import com.android.tools.idea.projectsystem.isScreenshotTestModule
import com.intellij.openapi.module.Module

class FindDependenciesWithResourcesGradleToken : FindDependenciesWithResourcesToken<GradleModuleSystem>, GradleToken {
  override fun findDependencies(moduleSystem: GradleModuleSystem, module: Module): Collection<ExternalAndroidLibrary> {
    val dependencyScope = when {
      moduleSystem.isRClassTransitive -> DependencyScopeType.MAIN
      module.isMainModule() -> DependencyScopeType.MAIN
      module.isUnitTestModule() -> DependencyScopeType.UNIT_TEST
      module.isAndroidTestModule() -> DependencyScopeType.ANDROID_TEST
      module.isTestFixturesModule() -> DependencyScopeType.TEST_FIXTURES
      module.isScreenshotTestModule() -> DependencyScopeType.SCREENSHOT_TEST
      else -> DependencyScopeType.MAIN
    }
    return moduleSystem.getAndroidLibraryDependencies(dependencyScope)
  }
}