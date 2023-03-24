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
package com.android.tools.idea.devicemanagerv2

import com.android.tools.adtui.categorytable.CategoryTable
import com.android.tools.adtui.categorytable.Column
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

internal class GroupingAction(
  private val table: CategoryTable<DeviceRowData>,
  private val column: Column<DeviceRowData, *, *>,
) : ToggleAction(column.name) {
  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean {
    return table.groupByAttributes.contains(column.attribute)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      table.addGrouping(column.attribute)
    } else {
      table.removeGrouping(column.attribute)
    }
  }
}

internal class GroupByNoneAction(val table: CategoryTable<DeviceRowData>) : ToggleAction("None") {
  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent) = table.groupByAttributes.isEmpty()

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    table.groupByAttributes.forEach { table.removeGrouping(it) }
  }
}
