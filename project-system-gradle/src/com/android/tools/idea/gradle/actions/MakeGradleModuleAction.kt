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
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker.Companion.getInstance
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.ProjectSyncModificationTracker
import com.android.tools.idea.projectsystem.androidProjectType
import com.android.tools.idea.projectsystem.isMainModule
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import icons.StudioIcons
import org.jetbrains.kotlin.idea.base.util.isGradleModule

/** Action added to the "Build" menu. If the new UI is used, it is also added to the toolbar. */
class MakeGradleModuleAction : AbstractMakeGradleModuleAction()

/** If the new UI is not used, this action is added to the toolbar in the split button. */
class MakeGradleModuleActionFromGroupAction : AbstractMakeGradleModuleAction() {

  private var moduleNamesToBuildFromGroupAction: List<String>? = null

  internal fun setModuleNamesExternally(value: List<String>) {
    moduleNamesToBuildFromGroupAction = value
  }

  override fun getModuleNamesToBuild(e: AnActionEvent, project: Project): List<String> {
    return moduleNamesToBuildFromGroupAction ?: emptyList()
  }

  override fun getModulesToBuild(e: AnActionEvent, project: Project): Array<Module> {
    val moduleManager = ModuleManager.getInstance(project)
    return moduleNamesToBuildFromGroupAction?.let {
      it.mapNotNull { name -> moduleManager.findModuleByName(name) }.toTypedArray()
    } ?: emptyArray()
  }
}

abstract class AbstractMakeGradleModuleAction :
  AndroidStudioGradleAction("Make Module(s)", "Build selected modules", StudioIcons.Shell.Toolbar.BUILD_MODULE) {

  private var previouslySelectedModules: List<String> = emptyList()

  open fun getModuleNamesToBuild(e: AnActionEvent, project: Project): List<String> = extractModuleNames(e, project)

  open fun getModulesToBuild(e: AnActionEvent, project: Project): Array<Module> =
    GradleProjectInfo.getInstance(project).getModulesToBuildFromSelection(e.dataContext)

  final override fun doUpdate(e: AnActionEvent, project: Project) {
    val modules = getModuleNamesToBuild(e, project).ifEmpty {  getPreviouslySelectedNamesIfValid(project) }
    previouslySelectedModules = modules

    updatePresentation(e, project, modules)
  }

  final override fun doPerform(e: AnActionEvent, project: Project) {
    val modules = getModulesToBuild(e, project).ifEmpty { getPreviouslySelectedModulesIfValid(project) }
    previouslySelectedModules = modules.map { it.name }

    getInstance(project).assemble(modules, TestCompileType.ALL)
  }

  private fun getPreviouslySelectedNamesIfValid(project: Project): List<String> =
    getPreviouslySelectedModulesIfValid(project).map { it.name }

  /**
   * Returns previously selected modules, if entire selection is still valid (module exists and it is not disposed).
   * In cases when focus is lost (e.g. by opening tools window), we should try to maintain the previous modules selection.
   *
   * In case there is no previous selection, we choose [getDefaultModuleToBuild].
   */
  private fun getPreviouslySelectedModulesIfValid(project: Project): Array<Module> {
    val moduleManager = ModuleManager.getInstance(project)
    val selectedModules = arrayOfNulls<Module>(previouslySelectedModules.size)
    var id = 0

    val allValid = previouslySelectedModules.all {
      val selectedModule = moduleManager.findModuleByName(it)
      if (selectedModule == null || selectedModule.isDisposed) {
        false
      }
      else {
        selectedModules[id++] = selectedModule
        true
      }
    }

    return if (allValid && selectedModules.isNotEmpty()) {
      @Suppress("UNCHECKED_CAST")
      selectedModules as Array<Module>
    }
    else {
      getDefaultModuleToBuild(project)?.let { arrayOf(it) } ?: arrayOf()
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  /**
   *  When the project is opened, there will not be selected modules until user trigger a focus action.
   *  In order not to have this action disabled, we try to select the first application.
   */
  private fun getDefaultModuleToBuild(project: Project): Module? {
    return CachedValuesManager.getManager(project).getCachedValue(project, CachedValueProvider {
      val firstApp =
        project.modules.firstOrNull { it.isMainModule() && it.isGradleModule && it.androidProjectType() == AndroidModuleSystem.Type.TYPE_APP }
      return@CachedValueProvider CachedValueProvider.Result(firstApp, ProjectSyncModificationTracker.getInstance(project))
    })
  }

  companion object {
    @JvmStatic
    fun updatePresentation(e: AnActionEvent, project: Project) {
      updatePresentation(e, project, extractModuleNames(e, project))
    }

    private fun extractModuleNames(e: AnActionEvent, project: Project): List<String> =
      GradleProjectInfo.getInstance(project).getModulesToBuildFromSelection(e.dataContext).map { it.name }

    private fun updatePresentation(e: AnActionEvent, project: Project, moduleNames: List<String?>) {
      val moduleCount = moduleNames.size
      val presentation = e.presentation
      val isCompilationActive = CompilerManager.getInstance(project).isCompilationActive
      presentation.isEnabled = moduleCount > 0 && !isCompilationActive
      val presentationText: String
      if (moduleCount > 0) {
        var text = StringBuilder("Make Module")
        if (moduleCount > 1) {
          text.append("s")
        }
        for (i in 0 until moduleCount) {
          if (text.length > 30) {
            text = StringBuilder("Make Selected Modules")
            break
          }
          val toMake = moduleNames[i]
          if (i != 0) {
            text.append(",")
          }
          text.append(" '").append(toMake).append("'")
        }
        presentationText = text.toString()
      } else {
        presentationText = "Make (No Modules Selected)"
      }
      presentation.text = presentationText
      presentation.isVisible = moduleCount > 0 || ActionPlaces.PROJECT_VIEW_POPUP != e.place
    }
  }
}
