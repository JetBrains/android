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

package com.android.tools.idea.npw.actions

import com.android.tools.idea.projectsystem.gradle.buildNamePrefixedGradleProjectPath
import com.android.tools.idea.projectsystem.gradle.getBuildAndRelativeGradleProjectPath
import com.intellij.ide.projectView.impl.ModuleGroup.ARRAY_DATA_KEY
import com.intellij.openapi.actionSystem.ActionPlaces.MAIN_MENU
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys.MODULE_CONTEXT_ARRAY
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys

class AndroidNewModuleInGroupAction : AndroidNewModuleAction("Module", "Adds a new module to the project", null) {
  override fun update(e: AnActionEvent) {
    super.update(e)

    if (!e.presentation.isVisible) {
      return  // Nothing to do, if above call to parent update() has disable the action
    }

    val moduleGroups = e.getData(ARRAY_DATA_KEY)
    val modules = e.getData(MODULE_CONTEXT_ARRAY)
    // Note: Hide if shown from the main menu, we already have "New Module...", otherwise assume right click on Project View.
    e.presentation.isVisible = e.place != MAIN_MENU && (!moduleGroups.isNullOrEmpty() || !modules.isNullOrEmpty())
  }

  override fun getModulePath(e: AnActionEvent): String {
    val module = PlatformCoreDataKeys.MODULE.getData(e.dataContext) ?: return ":"

    return module.getBuildAndRelativeGradleProjectPath()?.buildNamePrefixedGradleProjectPath() ?: ":"
  }
}
