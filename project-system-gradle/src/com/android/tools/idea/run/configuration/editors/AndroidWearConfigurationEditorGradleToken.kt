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
package com.android.tools.idea.run.configuration.editors

import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.gradle.getMainModule
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.projectsystem.gradle.isHolderModule
import com.intellij.openapi.module.Module
import com.intellij.psi.search.GlobalSearchScope

class AndroidWearConfigurationEditorGradleToken : AndroidWearConfigurationEditorToken<GradleProjectSystem>, GradleToken {
  override fun isModuleAccepted(projectSystem: GradleProjectSystem, module: Module): Boolean =
    module.isHolderModule() && super.isModuleAccepted(projectSystem, module)
  override fun getComponentSearchScope(projectSystem: GradleProjectSystem, module: Module): GlobalSearchScope =
    module.getMainModule().getModuleSystem().getResolveScope(ScopeType.MAIN)
}