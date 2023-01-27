/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.runconfiguration

import com.android.tools.adtui.TabularLayout
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.projectsystem.getAndroidModulesForDisplay
import com.android.tools.idea.run.editor.AndroidDebuggerPanel
import com.intellij.application.options.ModulesComboBox
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Represents the UI for editing and creating instances of [ComposePreviewRunConfiguration] in the
 * run configurations edit panel.
 */
class ComposePreviewSettingsEditor(
  private val project: Project,
  private val config: ComposePreviewRunConfiguration
) : SettingsEditor<ComposePreviewRunConfiguration>() {
  private val panel: JPanel
  private val debuggerTab: AndroidDebuggerPanel?
  private val modulesComboBox = ModulesComboBox()
  private val composableField =
    JBTextField().apply { emptyText.text = message("run.configuration.composable.empty.text") }

  init {
    Disposer.register(project, this)
    panel = JPanel(TabularLayout("*", "Fit,*"))
    val tabbedPane = JBTabbedPane()
    tabbedPane.add(message("run.configuration.general.tab"), createGeneralTab())
    debuggerTab = createDebuggerTab()
    debuggerTab?.component?.let { tabbedPane.add(message("run.configuration.debugger.tab"), it) }

    panel.add(tabbedPane, TabularLayout.Constraint(0, 0))
  }

  private fun createGeneralTab(): JPanel {
    val tab = JPanel(TabularLayout("Fit,*", "Fit,Fit"))
    modulesComboBox.allowEmptySelection(message("run.configuration.no.module.selected"))
    tab.add(
      LabeledComponent.create(
        modulesComboBox,
        message("run.configuration.module.label"),
        BorderLayout.WEST
      ),
      TabularLayout.Constraint(0, 0)
    )
    tab.add(
      LabeledComponent.create(
        composableField,
        message("run.configuration.composable.label"),
        BorderLayout.WEST
      ),
      TabularLayout.Constraint(1, 0, 2)
    )
    return tab
  }

  private fun createDebuggerTab(): AndroidDebuggerPanel? {
    val debuggerContext = config.androidDebuggerContext
    return if (debuggerContext.androidDebuggers.size > 1)
      AndroidDebuggerPanel(config, debuggerContext)
    else null
  }

  private fun resetComboBoxModules() {
    modulesComboBox.setModules(project.getAndroidModulesForDisplay())
  }

  override fun resetEditorFrom(runConfiguration: ComposePreviewRunConfiguration) {
    resetComboBoxModules()
    runConfiguration.modules.takeUnless { it.isEmpty() }?.let {
      modulesComboBox.selectedModule = it[0]
    }
    composableField.text = runConfiguration.composableMethodFqn
    debuggerTab?.resetFrom(runConfiguration.androidDebuggerContext)
  }

  @Throws(ConfigurationException::class)
  override fun applyEditorTo(runConfiguration: ComposePreviewRunConfiguration) {
    debuggerTab?.applyTo(runConfiguration.androidDebuggerContext)
    runConfiguration.composableMethodFqn = composableField.text
    runConfiguration.setModule(modulesComboBox.selectedModule)
  }

  override fun createEditor() = panel
}
