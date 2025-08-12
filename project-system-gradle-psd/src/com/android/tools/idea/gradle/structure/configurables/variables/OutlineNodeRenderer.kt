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
package com.android.tools.idea.gradle.structure.configurables.variables

import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.tree.TreeCellRenderer

class OutlineNodeRenderer(private val nodeRenderer: NodeRenderer) : JBPanel<OutlineNodeRenderer>(BorderLayout()), TreeCellRenderer {
  private var iconLabel = JLabel()
  private val errorBorders = JBUI.Borders.empty(2, 5, 2, 6 + 6)
  private val emptyBorders = JBUI.Borders.empty()
  override fun getTreeCellRendererComponent(
    tree: JTree?,
    value: Any?,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean
  ): Component {
    if (value is VariablesTableNode && shouldShowValidationErrors(value, expanded)) {
      showError()
    }
    else {
      showNoError()
    }

    val mainComponent = nodeRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
    add(mainComponent, BorderLayout.CENTER)
    add(iconLabel, BorderLayout.EAST)
    return this
  }

  private fun shouldShowValidationErrors(value: VariablesTableNode, expanded: Boolean): Boolean {
    fun hasInvalidNameRecursive(value: VariablesTableNode): Boolean {
      return !value.hasValidName() ||
        value.children().toList().filterIsInstance<VariablesTableNode>().any { hasInvalidNameRecursive(it) }
    }
    // if element itself has invalid name
    if (!value.hasValidName()) return true
    // or if it's collapsed and has invalid children
    if (!expanded) {
      return hasInvalidNameRecursive(value)
    }
    return false
  }

  private fun showError() {
    iconLabel.setIcon(AllIcons.General.BalloonError)
    iconLabel.isVisible = true
    iconLabel.border = errorBorders
  }

  private fun showNoError() {
    iconLabel.isVisible = false
    iconLabel.border = emptyBorders
  }

}
