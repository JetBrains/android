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
import com.intellij.openapi.options.BaseConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.AnActionButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.util.ui.EmptyIcon
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener

/**
 * Configurable defining the Variables panel in the Project Structure Dialog
 */
class VariablesConfigurable(private val project: Project, private val context: PsContext)
  : BaseConfigurable(), TrackedConfigurable {

  override fun getDisplayName(): String = "Variables"
  override val leftConfigurable = PSDEvent.PSDLeftConfigurable.PROJECT_STRUCTURE_DIALOG_LEFT_CONFIGURABLE_VARIABLES

  override fun createComponent(): JComponent? {
    val panel = JPanel(BorderLayout())
    panel.border = BorderFactory.createEmptyBorder(20, 10, 20, 10)
    val table = VariablesTable(project, context.project)
    table.tableModel.addTreeModelListener(object: TreeModelListener {
      override fun treeNodesInserted(e: TreeModelEvent?) {
        this@VariablesConfigurable.isModified = true
      }

      override fun treeStructureChanged(e: TreeModelEvent?) {
        this@VariablesConfigurable.isModified = true
      }

      override fun treeNodesChanged(e: TreeModelEvent?) {
        this@VariablesConfigurable.isModified = true
      }

      override fun treeNodesRemoved(e: TreeModelEvent?) {
        this@VariablesConfigurable.isModified = true
      }
    })
    panel.add(ToolbarDecorator.createDecorator(table)
        .setAddAction { createAddAction(it, table) }
        .setRemoveAction { table.deleteSelectedVariables() }
        .setEditAction {}
        .createPanel(), BorderLayout.CENTER)
    return panel
  }

  private fun createAddAction(button: AnActionButton, table: VariablesTable) {
    val actions = listOf(
      AddAction("1. Simple value", GradlePropertyModel.ValueType.STRING),
      AddAction("2. List", GradlePropertyModel.ValueType.LIST),
      AddAction("3. Map", GradlePropertyModel.ValueType.MAP)
    )
    val icons = listOf<Icon>(EmptyIcon.ICON_0, EmptyIcon.ICON_0, EmptyIcon.ICON_0)
    val popup = JBPopupFactory.getInstance().createListPopup(object : BaseListPopupStep<AddAction>(null, actions, icons) {
      override fun onChosen(selectedValue: AddAction?, finalChoice: Boolean): PopupStep<*>? {
        return doFinalStep { selectedValue?.type?.let { table.addVariable(it) } }
      }
    })
    popup.show(button.preferredPopupPoint)
  }

  override fun apply() {
    context.project.applyChanges()
    isModified = false
  }

  class AddAction(val text: String, val type: GradlePropertyModel.ValueType) {
    override fun toString() = text
  }
}