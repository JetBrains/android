/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.configuration

import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.isHolderModule
import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.ui.ConfigurationModuleSelector
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.android.util.AndroidBundle.message
import java.awt.BorderLayout
import java.awt.Dimension

class AndroidBaselineProfileRunConfigurationEditor(
  private val project: Project,
  private val configuration: AndroidBaselineProfileRunConfiguration
) : SettingsEditor<AndroidBaselineProfileRunConfiguration>() {

  private val modulesComboBox = ModulesComboBox()
  private var generateAllVariants: Boolean = configuration.generateAllVariants

  private val moduleSelector = object : ConfigurationModuleSelector(project, modulesComboBox) {
    override fun isModuleAccepted(module: Module?): Boolean {
      if (module == null || !super.isModuleAccepted(module)) {
        return false
      }
      val facet = AndroidFacet.getInstance(module) ?: return false
      if (!module.isHolderModule()) return false
      return when (facet.getModuleSystem().type) {
        AndroidModuleSystem.Type.TYPE_APP -> true

        AndroidModuleSystem.Type.TYPE_NON_ANDROID,
        AndroidModuleSystem.Type.TYPE_LIBRARY,
        AndroidModuleSystem.Type.TYPE_TEST,
        AndroidModuleSystem.Type.TYPE_ATOM,
        AndroidModuleSystem.Type.TYPE_INSTANTAPP,
        AndroidModuleSystem.Type.TYPE_FEATURE,
        AndroidModuleSystem.Type.TYPE_DYNAMIC_FEATURE -> false
      }
    }
  }

  init {
    Disposer.register(project, this)

    modulesComboBox.addActionListener {
      object : Task.Modal(project, AndroidBundle.message("android.run.configuration.loading"), true) {
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
              add(JBPanelWithEmptyText().withEmptyText("Can't edit configuration while Project is synchronizing"))
            }
          }
        }
      }.queue()
    }
  }

  override fun resetEditorFrom(runConfiguration: AndroidBaselineProfileRunConfiguration) {
    moduleSelector.reset(runConfiguration)
    generateAllVariants = runConfiguration.generateAllVariants
    (component as DialogPanel).reset()
  }

  override fun applyEditorTo(runConfiguration: AndroidBaselineProfileRunConfiguration) {
    (component as DialogPanel).apply()
    moduleSelector.applyTo(runConfiguration)
    runConfiguration.generateAllVariants = generateAllVariants
  }

  override fun createEditor() = panel {

    row {
      label(AndroidBundle.message("android.run.configuration.module.label"))
      cell(modulesComboBox)
        .align(AlignX.FILL)
        .applyToComponent {
          maximumSize = Dimension(400, maximumSize.height)
        }
    }.layout(RowLayout.LABEL_ALIGNED)

    group(message("android.baseline.profile.run.configuration.group.variants.title"), indent = true) {
      buttonsGroup {
        row {
          radioButton(
            message("android.baseline.profile.run.configuration.group.variants.current"),
            value = false
          ).align(AlignX.FILL)
        }.layout(RowLayout.LABEL_ALIGNED)
        row {
          radioButton(
            message("android.baseline.profile.run.configuration.group.variants.allvariants"),
            value = true
          ).align(AlignX.FILL)
        }.layout(RowLayout.LABEL_ALIGNED)
      }.bind(::generateAllVariants)
    }
  }
}
