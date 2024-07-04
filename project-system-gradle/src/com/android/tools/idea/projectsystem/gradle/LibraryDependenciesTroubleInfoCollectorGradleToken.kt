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
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.LibraryDependenciesTroubleInfoCollectorToken
import com.android.tools.idea.projectsystem.isAndroidTestModule
import com.android.tools.idea.projectsystem.isScreenshotTestModule
import com.android.tools.idea.projectsystem.isTestFixturesModule
import com.android.tools.idea.projectsystem.isUnitTestModule
import com.intellij.openapi.module.Module

class LibraryDependenciesTroubleInfoCollectorGradleToken : LibraryDependenciesTroubleInfoCollectorToken<GradleModuleSystem>, GradleToken {
  override fun getDependencies(moduleSystem: GradleModuleSystem, module: Module): Collection<ExternalAndroidLibrary> =
    moduleSystem.getAndroidLibraryDependencies(getScopeType(module))

  override fun getInfoString(module: Module): String =
    """ isAndroidTest=${module.isAndroidTestModule()} isUnitTest=${module.isUnitTestModule()}
  scopeType=${getScopeType(module)}"""

  private fun getScopeType(module: Module) = when {
      module.isAndroidTestModule() -> DependencyScopeType.ANDROID_TEST
      module.isUnitTestModule() -> DependencyScopeType.UNIT_TEST
      module.isTestFixturesModule() -> DependencyScopeType.TEST_FIXTURES
      module.isScreenshotTestModule() -> DependencyScopeType.SCREENSHOT_TEST
      else -> DependencyScopeType.MAIN
    }
}