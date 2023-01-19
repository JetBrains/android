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
import com.android.tools.idea.compose.preview.PreviewGroup.Companion.ALL_PREVIEW_GROUP
import com.android.tools.idea.compose.preview.findComposePreviewManagersForContext
import com.android.tools.idea.compose.preview.message
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ToggleAction

/** [DropDownAction] that allows the user filtering the visible previews by group. */
internal class GroupSwitchAction :
  DropDownAction(null, message("action.group.switch.title"), null) {
  /** [ToggleAction] that sets the given [group] as filter. */
  inner class SetGroupAction(private val group: PreviewGroup, private val isSelected: Boolean) :
    ToggleAction(group.displayName) {
    override fun isSelected(e: AnActionEvent): Boolean = isSelected

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (state) {
        findComposePreviewManagersForContext(e.dataContext).forEach { it.groupFilter = group }
      }
    }
  }

  override fun displayTextInToolbar(): Boolean = true

  override fun update(e: AnActionEvent) {
    super.update(e)

    val presentation = e.presentation
    val previewManagers = findComposePreviewManagersForContext(e.dataContext)
    val availableGroups = previewManagers.flatMap { it.availableGroups }.toSet()
    presentation.isVisible =
      availableGroups.isNotEmpty() && previewManagers.none { it.isFilterEnabled }

    presentation.isEnabled =
      availableGroups.isNotEmpty() && !previewManagers.any { it.status().isRefreshing }
    if (presentation.isVisible) {
      presentation.text = previewManagers.map { it.groupFilter.displayName }.firstOrNull()
    }
  }

  override fun updateActions(context: DataContext): Boolean {
    removeAll()
    val previewManagers = findComposePreviewManagersForContext(context)
    val availableGroups = previewManagers.flatMap { it.availableGroups }.toSet()
    if (availableGroups.isEmpty()) return true

    val selectedGroup = previewManagers.map { it.groupFilter }.firstOrNull() ?: ALL_PREVIEW_GROUP
    addGroups(availableGroups, selectedGroup)
    return true
  }

  private fun addGroups(groups: Set<PreviewGroup>, selected: PreviewGroup) {
    add(SetGroupAction(ALL_PREVIEW_GROUP, selected == ALL_PREVIEW_GROUP))
    addSeparator()
    groups.sortedBy { it.displayName }.forEach { add(SetGroupAction(it, it == selected)) }
  }
}
