/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.compose.COMPOSE_PREVIEW_ACTIVITY_FQN
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.runconfiguration.ComposePreviewRunConfiguration
import com.android.tools.idea.compose.preview.runconfiguration.ComposePreviewRunConfigurationType
import com.android.tools.idea.compose.preview.util.previewElement
import com.android.tools.idea.preview.essentials.PreviewEssentialsModeManager
import com.android.tools.idea.projectsystem.isTestFile
import com.android.tools.preview.ComposePreviewElement
import com.android.tools.preview.ParametrizedComposePreviewElementInstance
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.runConfigurationType
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import icons.StudioIcons.Compose.Toolbar.RUN_ON_DEVICE
import org.jetbrains.kotlin.idea.base.util.module

/** Action to run a Compose Preview on a device/emulator. */
internal class DeployToDeviceAction :
  AnAction(message("action.run.title"), message("action.run.description"), RUN_ON_DEVICE) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    e.dataContext.previewElement()?.let {
      val psiElement = it.previewElementDefinition?.element
      val project = psiElement?.project ?: return@actionPerformed
      val module = psiElement.module ?: return@actionPerformed

      runPreviewConfiguration(project, module, it)
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val isTestFile =
      e.dataContext.previewElement()?.previewBody?.let { isTestFile(it.project, it.virtualFile) }
        ?: false
    e.presentation.apply {
      val isEssentialsModeEnabled = PreviewEssentialsModeManager.isEssentialsModeEnabled
      isEnabled = !isTestFile && !isEssentialsModeEnabled
      isVisible = true
      text = if (isEssentialsModeEnabled) null else message("action.run.title")
      description =
        if (isTestFile) message("action.run.description.test.files")
        else {
          if (isEssentialsModeEnabled) message("action.run.essentials.mode.description")
          else message("action.run.description")
        }
    }
  }

  private fun runPreviewConfiguration(
    project: Project,
    module: Module,
    previewElement: ComposePreviewElement<*>,
  ) {
    val factory =
      runConfigurationType<ComposePreviewRunConfigurationType>().configurationFactories[0]
    val composePreviewRunConfiguration =
      ComposePreviewRunConfiguration(project, factory, COMPOSE_PREVIEW_ACTIVITY_FQN).apply {
        name = previewElement.displaySettings.name
        composableMethodFqn = previewElement.methodFqn
        previewElement.previewProviderClassAndIndex()?.let {
          providerClassFqn = it.first
          providerIndex = it.second
        }
        setModule(module)
      }

    val configurationAndSettings =
      RunManager.getInstance(project).findSettings(composePreviewRunConfiguration)
        ?: RunManager.getInstance(project)
          .createConfiguration(composePreviewRunConfiguration, factory)
          .apply { isTemporary = true }
          .also { configAndSettings ->
            RunManager.getInstance(project).addConfiguration(configAndSettings)
          }
    (configurationAndSettings.configuration as ComposePreviewRunConfiguration).triggerSource =
      ComposePreviewRunConfiguration.TriggerSource.TOOLBAR
    RunManager.getInstance(project).selectedConfiguration = configurationAndSettings
    ProgramRunnerUtil.executeConfiguration(
      configurationAndSettings,
      DefaultRunExecutor.getRunExecutorInstance(),
    )
  }
}

/**
 * If the [ComposePreviewElement] is a [ParametrizedComposePreviewElementInstance], returns the
 * provider class FQN and the target value index.
 */
private fun ComposePreviewElement<*>.previewProviderClassAndIndex() =
  if (this is ParametrizedComposePreviewElementInstance) Pair(providerClassFqn, index) else null
