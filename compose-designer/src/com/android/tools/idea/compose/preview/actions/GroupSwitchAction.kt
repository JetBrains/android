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
import com.android.tools.idea.compose.preview.ComposePreviewBundle.message
import com.android.tools.idea.compose.preview.findComposePreviewManagersForContext
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ToggleAction

/**
 * [DropDownAction] that allows the user filtering the visible previews by group.
 */
internal class GroupSwitchAction : DropDownAction(
  null, message("action.group.switch.title"), null) {
  /**
   * [ToggleAction] that sets the given [groupNameFilter] as filter. If the [groupNameFilter] is null, then no filter will be applied
   * and all groups will be shown.
   */
  inner class SetGroupAction(name: String, private val groupNameFilter: String?, private val isSelected: Boolean) : ToggleAction(name) {
    override fun isSelected(e: AnActionEvent): Boolean = isSelected

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (state) {
        findComposePreviewManagersForContext(e.dataContext).forEach { it.groupNameFilter = groupNameFilter }
      }
    }
  }

  private fun getDisplayNameForGroupName(groupName: String?) = groupName ?: message("group.switch.all")
  override fun displayTextInToolbar(): Boolean = true

  override fun update(e: AnActionEvent) {
    super.update(e)

    val presentation = e.presentation
    val previewManagers = findComposePreviewManagersForContext(e.dataContext)
    val availableGroups = previewManagers.flatMap { it.availableGroups }.toSet()
    presentation.isEnabledAndVisible = availableGroups.isNotEmpty()
    if (presentation.isEnabledAndVisible) {
      presentation.text = getDisplayNameForGroupName(previewManagers.map { it.groupNameFilter }.firstOrNull())
    }
  }

  override fun updateActions(context: DataContext): Boolean {
    removeAll();
    val previewManagers = findComposePreviewManagersForContext(context)
    val availableGroups = previewManagers.flatMap { it.availableGroups }.toSet()
    if (availableGroups.isEmpty()) return true

    val selectedGroup = previewManagers.map { it.groupNameFilter }.firstOrNull()
    addGroups(availableGroups, selectedGroup);
    return true
  }

  private fun addGroups(groups: Set<String>, selected: String?) {
    add(SetGroupAction("All", null, selected == null))
    addSeparator()
    groups.sorted().forEach { add(SetGroupAction(it, it, it == selected)) }
  }
}