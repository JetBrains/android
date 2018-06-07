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

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.intellij.openapi.options.BaseConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.ToolbarDecorator
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener

/**
 * Configurable defining the Variables panel in the Project Structure Dialog
 */
class VariablesConfigurable(private val project: Project, private val context: PsContext) : BaseConfigurable() {

  override fun getDisplayName(): String = "Variables"

  override fun createComponent(): JComponent? {
    val panel = JPanel(BorderLayout())
    panel.border = BorderFactory.createEmptyBorder(20, 10, 20, 10)
    val table = VariablesTable(project, context)
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
        .setAddAction {}
        .setRemoveAction {}
        .setEditAction {}
        .createPanel(), BorderLayout.CENTER)
    return panel
  }

  override fun apply() {
    context.project.applyChanges()
    isModified = false
  }

}