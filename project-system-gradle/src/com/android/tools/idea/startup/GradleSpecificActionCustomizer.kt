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
package com.android.tools.idea.startup

import com.android.tools.idea.actions.AndroidActionGroupRemover
import com.android.tools.idea.actions.AndroidOpenFileAction
import com.android.tools.idea.actions.CreateLibraryFromFilesAction
import com.android.tools.idea.gradle.actions.AndroidTemplateProjectStructureAction
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.startup.Actions.hideAction
import com.android.tools.idea.startup.Actions.replaceAction
import com.intellij.ide.projectView.actions.MarkRootGroup
import com.intellij.ide.projectView.impl.MoveModuleToGroupTopLevel
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Anchor
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer
import java.util.ArrayDeque
import java.util.Deque

class GradleSpecificActionCustomizer : ActionConfigurationCustomizer {
  override fun customize(actionManager: ActionManager) {
    setUpNewProjectActions(actionManager)
    setUpWelcomeScreenActions(actionManager)
    disableUnsupportedAction(actionManager)
    replaceProjectPopupActions(actionManager)
    setUpMakeActions(actionManager)
    setUpGradleViewToolbarActions(actionManager)
  }

  companion object {
    private fun disableUnsupportedAction(actionManager: ActionManager) {
      actionManager.unregisterAction("LoadUnloadModules") // private LoadUnloadModulesActionKt.ACTION_ID
    }

    // The original actions will be visible only on plain IDEA projects.
    private fun setUpMakeActions(actionManager: ActionManager) {
      // 'Build' > 'Make Project' action
      hideAction(actionManager, "CompileDirty") { it.isFromGradleProject() }

      // 'Build' > 'Make Modules' action
      hideAction(actionManager, IdeActions.ACTION_MAKE_MODULE) { it.isFromGradleProject() }

      // 'Build' > 'Rebuild' action
      hideAction(actionManager, IdeActions.ACTION_COMPILE_PROJECT) { it.isFromGradleProject() }

      // 'Build' > 'Compile Modules' action
      hideAction(actionManager, IdeActions.ACTION_COMPILE) { it.isFromGradleProject() }

      // Additional 'Build' action from com.jetbrains.cidr.execution.build.CidrBuildTargetAction
      hideAction(actionManager, "Build") { it.isFromGradleProject() }
      hideAction(actionManager, "Groovy.CheckResources.Rebuild")
      hideAction(actionManager, "Groovy.CheckResources.Make")
      hideAction(actionManager, "Groovy.CheckResources")
      hideAction(actionManager, "CompileFile") { it.isFromGradleProject() }
    }

    private fun setUpGradleViewToolbarActions(actionManager: ActionManager) {
      hideAction(actionManager, "ExternalSystem.RefreshAllProjects")
      hideAction(actionManager, "ExternalSystem.SelectProjectDataToImport")
      hideAction(actionManager, "ExternalSystem.DetachProject")
      hideAction(actionManager, "ExternalSystem.AttachProject")
    }

    private fun setUpNewProjectActions(actionManager: ActionManager) {
      // Unregister IntelliJ's version of the project actions and manually register our own.
      replaceAction(actionManager, "OpenFile", AndroidOpenFileAction())
      replaceAction(actionManager, "CreateLibraryFromFile", CreateLibraryFromFilesAction())

      hideAction(actionManager, "AddFrameworkSupport")
      hideAction(actionManager, "BuildArtifact")
    }

    private fun setUpWelcomeScreenActions(actionManager: ActionManager) {
      // Update the Welcome Screen actions
      replaceAction(actionManager, "WelcomeScreen.OpenProject", AndroidOpenFileAction("Open"))
      replaceAction(
        actionManager,
        "WelcomeScreen.Configure.ProjectStructure",
        AndroidTemplateProjectStructureAction("Default Project Structure...")
      )
      replaceAction(
        actionManager,
        "TemplateProjectStructure",
        AndroidTemplateProjectStructureAction("Default Project Structure...")
      )
    }

    private fun replaceProjectPopupActions(actionManager: ActionManager) {
      val stack: Deque<Pair<DefaultActionGroup?, AnAction>> =
        ArrayDeque<Pair<DefaultActionGroup?, AnAction>>()
      stack.add((null as DefaultActionGroup?) to (actionManager.getAction("ProjectViewPopupMenu") ?: return))
      while (!stack.isEmpty()) {
        val (parent, action) = stack.pop()
        if (action is DefaultActionGroup) {
          for (child in action.childActionsOrStubs) {
            stack.push(action to child)
          }
        }

        if (action is MoveModuleToGroupTopLevel) {
          parent?.remove(action, actionManager)
          parent?.add(
            AndroidActionGroupRemover(action as ActionGroup, "Move Module to Group"),
            Constraints(Anchor.AFTER, "OpenModuleSettings"), actionManager
          )
        } else if (action is MarkRootGroup) {
          parent?.remove(action, actionManager)
          parent?.add(
            AndroidActionGroupRemover(action as ActionGroup, "Mark Directory As"),
            Constraints(Anchor.AFTER, "OpenModuleSettings"), actionManager
          )
        }
      }
    }
  }
}

private fun AnActionEvent.isFromGradleProject(): Boolean = this.project?.getProjectSystem() is GradleProjectSystem
