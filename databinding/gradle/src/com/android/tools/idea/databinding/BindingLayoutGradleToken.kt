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
package com.android.tools.idea.databinding

import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.projectsystem.isAndroidTestModule
import com.intellij.openapi.module.Module

private class BindingLayoutGradleToken : BindingLayoutToken<GradleProjectSystem>, GradleToken {
  override fun isTestModule(projectSystem: GradleProjectSystem, module: Module) =
    module.isAndroidTestModule()

  override fun additionalModulesForLightBindingScope(
    projectSystem: GradleProjectSystem,
    module: Module,
  ): List<Module> =
    when {
      module.isAndroidTestModule() -> listOf(module.getMainModule())
      else -> emptyList()
    }
}
