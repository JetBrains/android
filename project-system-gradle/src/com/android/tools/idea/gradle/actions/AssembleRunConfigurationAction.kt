/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker.Companion.getInstance
import com.android.tools.idea.projectsystem.gradle.getGradleContext
import com.android.tools.idea.projectsystem.gradle.isUnitTestModule
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ModuleRunProfile
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import icons.StudioIcons.Shell.Toolbar.BUILD_RUN_CONFIGURATION
import org.jetbrains.kotlin.idea.base.projectStructure.externalProjectPath
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration

/**
 * Action to build the currently selected run configuration.
 */
class AssembleRunConfigurationAction : AbstractBuildRunConfigurationAction()

abstract class AbstractBuildRunConfigurationAction :
  AndroidStudioGradleAction("Build Selected Run Configuration", "Build selected Run Configuration", BUILD_RUN_CONFIGURATION) {

  open fun getRunConfigNameToBuild(e: AnActionEvent, project: Project): String? = extractRunConfigurationName(project)

  final override fun doUpdate(e: AnActionEvent, project: Project) {
    val runConfigurationName = getRunConfigNameToBuild(e, project)

    updatePresentation(e, runConfigurationName)
  }

  final override fun doPerform(e: AnActionEvent, project: Project) {
    val selectedRunConfig = RunManager.getInstance(project).selectedConfiguration?.configuration
    if (selectedRunConfig == null) {
      BuildRunConfigNotifier.notifyNoRunConfigFound(project)
      return
    }
    val modulesToBuild = getModulesToBuild(selectedRunConfig)
    if (modulesToBuild.isNullOrEmpty()) {
      BuildRunConfigNotifier.notifyNoModulesFoundToBuild(selectedRunConfig.name, project)
      return
    }
    val configurationContext = selectedRunConfig.getGradleContext()
    check(modulesToBuild.isNotEmpty()) { BuildRunConfigNotifier.notifyNoModulesFoundToBuild(selectedRunConfig.name, project) }

    getInstance(project).buildConfiguration(modulesToBuild, configurationContext?.alwaysDeployApkFromBundle ?: false)
  }

  private fun getModulesToBuild(configuration: RunConfiguration): Array<Module>? {
    return when (configuration) {
      // ModuleBasedConfiguration includes Android (including Android Test) and JUnit run configurations,
      // which is AbstractRerunFailedTestsAction.MyRunProfile.
      is ModuleRunProfile -> configuration.modules
      // This is for Run configurations that are not module based like GradleRunConfiguration.
      is ExternalSystemRunConfiguration -> getExternalSystemConfigurationModule(configuration) ?: return null
      else -> return null
    }
  }

  /**
   * We currently only support the GradleRunConfiguration and we have two cases possible:
   * first: The run configuration is a test one, and in this case, we can get the module from the test task because we do populate the
   * module data nodes with the tasks data during Sync.
   * Second: The run configuration is a random Gradle one, and in this case it applies to the Gradle project of this run config, so we extract
   * the module from [ExternalSystemRunConfiguration.getSettings]'s externalProjectPath.
   */
  private fun getExternalSystemConfigurationModule(configuration: RunConfiguration): Array<Module>? {
    val project = configuration.project
    if (configuration is GradleRunConfiguration && configuration.isRunAsTest) {
      // The test task is the first one in the taskNames.
      val testTask = configuration.settings.taskNames.firstOrNull() ?: return null
      val modules = ModuleManager.getInstance(project).modules.filter {
        CachedModuleDataFinder
          .getGradleModuleData(it)?.findAll(ProjectKeys.TEST)?.filter { x -> x.testTaskName == testTask }?.isNotEmpty() == true
      }
      // As this is a unitTest run config, it will belong to the unitTest module.
      return modules.firstOrNull { it.isUnitTestModule() }?.let { arrayOf(it) }

    } else if (configuration is GradleRunConfiguration) {
      val configurationProjectPath = configuration.settings.externalProjectPath
      // Get the modules that have the same Gradle project path as the Run Configuration.
      return ModuleManager.getInstance(project).modules.filter { it.externalProjectPath == configurationProjectPath }.toTypedArray()
    }
    return null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  companion object {
    @JvmStatic
    fun updatePresentation(e: AnActionEvent, project: Project) {
      updatePresentation(e, extractRunConfigurationName(project))
    }

    private fun extractRunConfigurationName(project: Project): String? =
      RunManager.getInstance(project).selectedConfiguration?.name

    private fun updatePresentation(e: AnActionEvent, runConfigurationName: String?) {
      val presentation = e.presentation
      presentation.isEnabled = runConfigurationName != null
      val presentationText = if (!presentation.isEnabled) {
        "Assemble Run Configuration (No Configuration Selected)"
      } else {
        "Assemble '$runConfigurationName' Run Configuration"
      }
      presentation.text = presentationText
    }
  }
}
