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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.compose.preview.PreviewGroup
import com.android.tools.idea.compose.preview.findComposePreviewManagerForContext
import com.android.tools.idea.compose.preview.message
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ToggleAction

/** [DropDownAction] that allows the user filtering the visible previews by group. */
internal class GroupSwitchAction :
  DropDownAction(null, message("action.group.switch.title"), null) {
  // Actions calling findComposePreviewManagersForContext in the update method, must run in BGT
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  /** [ToggleAction] that sets the given [group] as filter. */
  inner class SetGroupAction(private val group: PreviewGroup, private val isSelected: Boolean) :
    ToggleAction(group.displayName) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean = isSelected

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (state) {
        findComposePreviewManagerForContext(e.dataContext)?.groupFilter = group
      }
    }
  }

  override fun displayTextInToolbar(): Boolean = true

  override fun update(e: AnActionEvent) {
    super.update(e)

    val presentation = e.presentation
    val previewManager = findComposePreviewManagerForContext(e.dataContext)
    val availableGroups = previewManager?.availableGroupsFlow?.value?.toSet() ?: emptySet()
    presentation.isVisible = availableGroups.isNotEmpty() && previewManager?.isFilterEnabled != true

    presentation.isEnabled =
      availableGroups.isNotEmpty() && previewManager?.status()?.isRefreshing != true
    if (presentation.isVisible) {
      presentation.text = previewManager?.groupFilter?.displayName
    }
  }

  override fun updateActions(context: DataContext): Boolean {
    removeAll()
    val previewManager = findComposePreviewManagerForContext(context)
    val availableGroups = previewManager?.availableGroupsFlow?.value?.toSet() ?: emptySet()
    if (availableGroups.isEmpty()) return true

    val selectedGroup = previewManager?.groupFilter ?: PreviewGroup.All
    addGroups(availableGroups, selectedGroup)
    return true
  }

  private fun addGroups(groups: Set<PreviewGroup>, selected: PreviewGroup) {
    add(SetGroupAction(PreviewGroup.All, selected == PreviewGroup.All))
    addSeparator()
    groups.sortedBy { it.displayName }.forEach { add(SetGroupAction(it, it == selected)) }
  }
}
