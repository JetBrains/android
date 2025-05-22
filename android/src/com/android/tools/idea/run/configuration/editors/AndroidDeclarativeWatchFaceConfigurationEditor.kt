/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.configuration.AndroidDeclarativeWatchFaceConfiguration
import com.google.common.annotations.VisibleForTesting
import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.ui.ConfigurationModuleSelector
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle.message
import java.awt.BorderLayout
import java.awt.Dimension

class AndroidDeclarativeWatchFaceConfigurationEditor(private val project: Project) :
  SettingsEditor<AndroidDeclarativeWatchFaceConfiguration>() {

  private val modulesComboBox = ModulesComboBox()

  @VisibleForTesting
  val moduleSelector =
    object : ConfigurationModuleSelector(project, modulesComboBox) {
      override fun isModuleAccepted(module: Module?): Boolean {
        if (module == null || !super.isModuleAccepted(module)) {
          return false
        }
        val isHolderModule = module.getModuleSystem().getHolderModule() == module
        if (!isHolderModule) return false
        val facet = AndroidFacet.getInstance(module) ?: return false
        return facet.getModuleSystem().type == AndroidModuleSystem.Type.TYPE_APP
      }
    }

  init {
    modulesComboBox.addActionListener {
      object :
          Task.Modal(project, message("android.run.configuration.loading"), true) {
          override fun run(indicator: ProgressIndicator) {
            val module = moduleSelector.module
            if (module == null || DumbService.isDumb(project)) {
              return
            }
          }

          override fun onFinished() {
            if (project.getProjectSystem().getSyncManager().isSyncInProgress()) {
              component?.parent?.parent?.apply {
                removeAll()
                layout = BorderLayout()
                add(
                  JBPanelWithEmptyText()
                    .withEmptyText(message("android.run.configuration.synchronization.warning"))
                )
              }
            }
          }
        }
        .queue()
    }
  }

  override fun resetEditorFrom(configuration: AndroidDeclarativeWatchFaceConfiguration) {
    moduleSelector.reset(configuration)
    (component as DialogPanel).reset()
  }

  override fun applyEditorTo(configuration: AndroidDeclarativeWatchFaceConfiguration) {
    (component as DialogPanel).apply()
    moduleSelector.applyTo(configuration)
  }

  override fun createEditor() = panel {
    row {
        label(message("android.run.configuration.module.label"))
        cell(modulesComboBox).align(AlignX.FILL).applyToComponent {
          maximumSize = Dimension(400, maximumSize.height)
        }
      }
      .layout(RowLayout.LABEL_ALIGNED)
  }
}
