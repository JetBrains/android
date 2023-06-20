/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade.ui

import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity
import com.intellij.icons.AllIcons
import com.intellij.ui.CheckboxTree
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class UpgradeAssistantTreeCellRenderer : CheckboxTree.CheckboxTreeCellRenderer(true, true) {
  override fun customizeRenderer(tree: JTree?,
                                 value: Any?,
                                 selected: Boolean,
                                 expanded: Boolean,
                                 leaf: Boolean,
                                 row: Int,
                                 hasFocus: Boolean) {
    if (value is DefaultMutableTreeNode) {
      when (val o = value.userObject) {
        is AgpUpgradeComponentNecessity -> {
          textRenderer.append(o.treeText())
          myCheckbox.let { toolTipText = o.checkboxToolTipText(it.isEnabled, it.isSelected) }
        }
        is UpgradeAssistantWindowModel.StepUiPresentation -> {
          (value.parent as? DefaultMutableTreeNode)?.let { parent ->
            if (parent.userObject == AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT) {
              toolTipText = null
              myCheckbox.isVisible = false
              textRenderer.append("")
              val totalXoffset = myCheckbox.width + myCheckbox.margin.left + myCheckbox.margin.right
              val firstXoffset = 2 * myCheckbox.width / 5 // approximate padding needed to put the bullet centrally in the space
              textRenderer.appendTextPadding(firstXoffset)
              textRenderer.append("\u2022", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, true)
              // Although this looks wrong (one might expect e.g. `totalXoffset - firstXoffset`), it does seem to be the case that
              // SimpleColoredComponent interprets padding from the start of the extent, rather than from the previous end.  Of course this
              // might be a bug, and if the behaviour of SimpleColoredComponent is changed this will break alignment of the Upgrade steps.
              textRenderer.appendTextPadding(totalXoffset)
            }
            else {
              myCheckbox.let {
                toolTipText = (parent.userObject as? AgpUpgradeComponentNecessity)?.let { n ->
                  n.checkboxToolTipText(it.isEnabled, it.isSelected)
                }
              }
            }
          }
          textRenderer.append(o.treeText, SimpleTextAttributes.REGULAR_ATTRIBUTES, true)
          if ((o as? UpgradeAssistantWindowModel.StepUiPresentation)?.isBlocked == true) {
            textRenderer.icon = AllIcons.General.Error
            textRenderer.isIconOnTheRight = true
            textRenderer.iconTextGap = 10
          }
          else if (o is UpgradeAssistantWindowModel.StepUiWithComboSelectorPresentation || o is UpgradeAssistantWindowModel.StepUiWithUserSelection) {
            textRenderer.icon = AllIcons.Actions.Edit
            textRenderer.isIconOnTheRight = true
            textRenderer.iconTextGap = 10
          }
        }
      }
    }
    super.customizeRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
  }
}