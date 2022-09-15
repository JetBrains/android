/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.variables

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.structure.dialog.TrackedConfigurable
import com.google.wireless.android.sdk.stats.PSDEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.BaseConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.AnActionButton
import com.intellij.ui.ToolbarDecorator
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

const val VARIABLES_VIEW = "VariablesView"
/**
 * Configurable defining the Variables panel in the Project Structure Dialog
 */
class VariablesConfigurable(private val project: Project, private val context: PsContext)
  : BaseConfigurable(), TrackedConfigurable, Disposable {
  private var uiDisposed = true
  override fun getDisplayName(): String = "Variables"
  override val leftConfigurable = PSDEvent.PSDLeftConfigurable.PROJECT_STRUCTURE_DIALOG_LEFT_CONFIGURABLE_VARIABLES

  override fun createComponent(): JComponent? {
    val panel = JPanel(BorderLayout())
    panel.border = BorderFactory.createEmptyBorder(20, 10, 20, 10)
    val table = VariablesTable(project, context, context.project, this)
    panel.add(
      ToolbarDecorator
        .createDecorator(table)
        .setAddAction { executeAfterAddAction(it, table) }
        .setAddActionUpdater { table.addVariableAvailable() }
        .setRemoveAction { table.deleteSelectedVariables() }
        .setRemoveActionUpdater { table.removeVariableAvailable() }
        .createPanel(), BorderLayout.CENTER)
    panel.name = VARIABLES_VIEW
    return panel
  }

  private fun executeAfterAddAction(button: AnActionButton, table: VariablesTable) {
    //next action could be creating a popup or start editing for version catalog
    table.runToolbarAddAction(button.preferredPopupPoint!!)
  }

  override fun apply() = context.applyChanges()

  override fun copyEditedFieldsTo(builder: PSDEvent.Builder) {
    builder.addAllModifiedFields(context.getEditedFieldsAndClear())
  }

  override fun isModified(): Boolean = context.project.isModified

  override fun reset() {
    super.reset()
    uiDisposed = false
  }

  override fun disposeUIResources() {
    if (uiDisposed) return
    super.disposeUIResources()
    uiDisposed = true
    Disposer.dispose(this)
  }

  override fun dispose() = Unit

  class AddAction(val text: String, val type: GradlePropertyModel.ValueType) {
    override fun toString() = text
  }
}
