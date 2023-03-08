/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions

import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.SplitButtonAction

/**
 * Action used to select between different types of make in the project: [MakeGradleProjectAction] and [MakeGradleModuleAction].
 */
class MakeTypeSelectionGroupAction : SplitButtonAction(AllMakeActionsGroup()) {

  override fun update(e: AnActionEvent) { // Extract the modules from this update action, as it always gets the right dataContext
    val project = e.project
    val modulesFromContext: List<String> = if (project == null) emptyList()
    else GradleProjectInfo.getInstance(project).getModulesToBuildFromSelection(e.dataContext).map { it.name }

    (actionGroup as AllMakeActionsGroup).setModulesFromContext(modulesFromContext)
    super.update(e)
  }

  internal class AllMakeActionsGroup : DefaultActionGroup() {
    private val makeModule = MakeGradleModuleActionFromGroupAction()
    private val children: Array<AnAction> = arrayOf(makeModule, MakeGradleProjectAction())

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      return children
    }

    /**
     * Sets the list of modules to build for the [makeModule] action. This is needed as the [SplitButtonAction] has logic which
     * triggers another [AnActionEvent] update when the split menu is shown, and [AnActionEvent.getDataContext] contains no module info
     * in that case. Because of that, the parent action group (i.e. [MakeTypeSelectionGroupAction]) sets the list of modules to build.
     */
    internal fun setModulesFromContext(selectedModules: List<String>) {
      makeModule.setModuleNamesExternally(selectedModules)
    }
  }
}
