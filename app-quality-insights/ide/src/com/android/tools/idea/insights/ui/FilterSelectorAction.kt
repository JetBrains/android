/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.idea.insights.inspection.AppInsightsFilterSelector
import com.android.tools.idea.insights.inspection.ConnectionFilter
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import java.awt.Component
import java.awt.Point
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.TestOnly

class FilterSelectorAction(
  private val project: Project,
  private val scope: CoroutineScope = project.service<AppInsightsFilterSelector>().scope,
  @TestOnly private val getLocationOnScreen: Component.() -> Point = Component::getLocationOnScreen,
) : DumbAwareAction(null, null, null) {

  private val flow: StateFlow<List<ConnectionFilter>>
    get() = project.service<AppInsightsFilterSelector>().filters

  private var selectedFilter: ConnectionFilter? = null
  private lateinit var popup: JBPopup

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    if (selectedFilter == null && flow.value.isNotEmpty()) {
      selectedFilter = flow.value.firstOrNull()
    }

    val text = selectedFilter?.let { "${it.title} [${it.appId}]" } ?: "No apps available"
    e.presentation.setText(text, false)
    e.presentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
  }

  override fun actionPerformed(eve: AnActionEvent) {
    popup =
      FilterSelectorPopup(flow.value, selectedFilter, scope) { filter ->
          selectedFilter = filter
          project.service<AppInsightsFilterSelector>().selectedAppId.value = selectedFilter?.appId
          popup.closeOk(null)
        }
        .asPopup()

    val owner = eve.inputEvent!!.component
    val location = getLocationOnScreen(owner)
    location.translate(0, owner.height)
    popup.showInScreenCoordinates(owner, location)
  }
}
