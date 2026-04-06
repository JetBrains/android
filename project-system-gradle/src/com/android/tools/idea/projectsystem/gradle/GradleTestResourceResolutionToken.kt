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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.TestResourceResolutionToken
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProductionAndroidModule
import com.intellij.openapi.module.Module

class GradleTestResourceResolutionToken : TestResourceResolutionToken<GradleProjectSystem>, GradleToken {

  override fun computeAdditionalLocalResourceDependencies(moduleSystem: AndroidModuleSystem): List<Module> {
    val dependencies = mutableListOf<Module>()

    // For Gradle projects, test modules (both screenshot and instrumentation tests) need to
    // explicitly include resources from their project dependencies. This ensures that resources
    // defined in library modules used by the test are correctly resolved in the IDE.
    if (moduleSystem.module.isScreenshotTestModule() || moduleSystem.module.isAndroidTestModule()) {
      val holderModule = moduleSystem.getHolderModule()
      dependencies.addAll(
        moduleSystem.getAndroidTestDirectResourceModuleDependencies().filter { it.getModuleSystem().getHolderModule() != holderModule }
      )
    }

    // Screenshot tests render UI components from the production module and thus require a
    // unified resource view that includes the production module's resources to allow proper
    // rendering of Previews and resolution of R classes.
    if (moduleSystem.module.isScreenshotTestModule()) {
      val prodModule = moduleSystem.getProductionAndroidModule()
      if (prodModule != null) {
        dependencies.add(prodModule)
      }
    }
    return dependencies
  }

  override fun computeTestResourceLibraryScope(moduleSystem: AndroidModuleSystem): DependencyScopeType? {
    return when {
      moduleSystem.module.isScreenshotTestModule() -> DependencyScopeType.SCREENSHOT_TEST
      moduleSystem.module.isAndroidTestModule() -> DependencyScopeType.ANDROID_TEST
      else -> null
    }
  }
}
