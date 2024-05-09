/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.ui.actions.ToggleIssueAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.table.TableView
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class IssueTableMouseListener(private val controller: AppInsightsProjectLevelController) :
  MouseAdapter() {
  private val appInsightState =
    controller.state.stateIn(controller.coroutineScope, SharingStarted.Eagerly, null)

  override fun mouseClicked(e: MouseEvent) = handleMouseClick(e)

  override fun mousePressed(e: MouseEvent) = handleMouseClick(e)

  private fun handleMouseClick(e: MouseEvent) {
    if (e.isPopupTrigger) {
      val table = e.component as? TableView<*> ?: return
      val row = table.rowAtPoint(e.point)
      if (row < 0) return
      val issue = table.getRow(row) as? AppInsightsIssue ?: return
      val state = appInsightState.value ?: return
      val toggleIssueAction = ToggleIssueAction(controller, state, issue)
      ActionManager.getInstance()
        .createActionPopupMenu("issue table", DefaultActionGroup(toggleIssueAction))
        .component
        .show(e.component, e.x, e.y)
    }
  }
}
