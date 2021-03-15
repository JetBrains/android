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

import com.android.tools.idea.common.actions.ActionButtonWithToolTipDescription
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_ELEMENT
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.runconfiguration.ComposePreviewRunConfiguration
import com.android.tools.idea.compose.preview.runconfiguration.ComposePreviewRunConfigurationType
import com.android.tools.idea.compose.preview.runconfiguration.isNonLibraryAndroidModule
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.compose.preview.util.previewProviderClassAndIndex
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.runConfigurationType
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import icons.StudioIcons.Compose.Toolbar.RUN_ON_DEVICE
import org.jetbrains.kotlin.idea.util.module

/**
 * Action to deploy a @Composable to the device.
 *
 * @param dataContextProvider returns the [DataContext] containing the Compose Preview associated information.
 */
internal class DeployToDeviceAction(private val dataContextProvider: () -> DataContext)
  : AnAction(message("action.deploy.title"), message("action.deploy.description"), RUN_ON_DEVICE), CustomComponentAction {

  override fun actionPerformed(e: AnActionEvent) {
    previewElement()?.let {
      val psiElement = it.previewBodyPsi?.element
      val project = psiElement?.project ?: return@actionPerformed
      val module = psiElement.module ?: return@actionPerformed

      runPreviewConfiguration(project, module, it)
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = !isRefreshing()
                               && previewElement()?.previewBodyPsi?.element?.module?.isNonLibraryAndroidModule() == true
  }

  override fun createCustomComponent(presentation: Presentation, place: String) =
    ActionButtonWithToolTipDescription(this, presentation, place)

  private fun runPreviewConfiguration(project: Project, module: Module, previewElement: PreviewElement) {
    val factory = runConfigurationType<ComposePreviewRunConfigurationType>().configurationFactories[0]
    val composePreviewRunConfiguration = ComposePreviewRunConfiguration(project, factory).apply {
      name = previewElement.displaySettings.name
      composableMethodFqn = previewElement.composableMethodFqn
      previewElement.previewProviderClassAndIndex()?.let {
        providerClassFqn = it.first
        providerIndex = it.second
      }
      setModule(module)
    }

    // TODO(b/152186687): select the configuration in the run configurations combobox.
    val configurationAndSettings = RunManager.getInstance(project).findSettings(composePreviewRunConfiguration)
                                   ?: RunManager.getInstance(project).createConfiguration(composePreviewRunConfiguration, factory).apply {
                                     isTemporary = true
                                   }.also { configAndSettings ->
                                     RunManager.getInstance(project).addConfiguration(configAndSettings)
                                   }
    (configurationAndSettings.configuration as ComposePreviewRunConfiguration)
      .triggerSource = ComposePreviewRunConfiguration.TriggerSource.TOOLBAR
    // TODO(b/152185907): consider stopping all the ComposePreviewRunConfiguration before running this one.
    RunManager.getInstance(project).selectedConfiguration = configurationAndSettings
    ProgramRunnerUtil.executeConfiguration(configurationAndSettings, DefaultRunExecutor.getRunExecutorInstance())
  }

  private fun previewElement() = dataContextProvider().getData(COMPOSE_PREVIEW_ELEMENT)

  private fun isRefreshing() = dataContextProvider().getData(COMPOSE_PREVIEW_MANAGER)?.status()?.isRefreshing == true
}