/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.actions.analytics

import com.android.tools.idea.assistant.AssistNavListener
import com.android.tools.idea.assistant.OpenAssistSidePanelAction
import com.android.tools.idea.uibuilder.actions.CONSTRAINT_LAYOUT_BUNDLE_ID
import com.android.tools.idea.uibuilder.actions.FULL_HELP_BUNDLE_ID
import com.android.tools.idea.uibuilder.actions.MOTION_EDITOR_BUNDLE_ID
import com.android.tools.idea.uibuilder.actions.NAV_EDITOR_BUNDLE_ID
import com.google.wireless.android.sdk.stats.DesignEditorHelpPanelEvent.HelpPanelType
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import java.awt.event.ActionEvent
import java.lang.ref.WeakReference

/**
 * Assistant Panel navigation listener. It listens to navigation events
 * specific to the layout editor (filtered through [AssistantPanelNavListener.getIdPrefix])
 */
class AssistantPanelNavListener : AssistNavListener {
  override fun getIdPrefix(): String {
    return "analytics-layouteditor"
  }

  override fun onActionPerformed(id: String?, e: ActionEvent) {
    AssistantPanelMetricsTracker(LayoutEditorHelpActionListener.type).logButtonClicked()
  }
}

/**
 * Assistant Panel tools listener. It listens to all assistatnt panel related actions.
 */
class LayoutEditorHelpActionListener : ToolWindowManagerListener {

  companion object {
    val type: HelpPanelType get() = mutableType
    private var mutableType: HelpPanelType = HelpPanelType.UNKNOWN_PANEL_TYPE
  }

  private var isOpen = false
  private var isRegistered = false
  private var projectRef: WeakReference<Project?> = WeakReference(null)

  private val metrics = HashMap<String, AssistantPanelMetricsTracker>()

  fun register(project: Project?) {
    projectRef = WeakReference(project)
    project ?: return

    if (!isRegistered) {
      project.messageBus.connect(project).subscribe(ToolWindowManagerListener.TOPIC, this)
      isRegistered = true
    }
  }

  override fun stateChanged() {
    val project = projectRef.get() ?: return
    val window = ToolWindowManager.getInstance(project).getToolWindow(
      OpenAssistSidePanelAction.TOOL_WINDOW_TITLE) ?: return
    val actionId = window.helpId ?: return

    mutableType = convertType(actionId)
    if (mutableType == HelpPanelType.UNKNOWN_PANEL_TYPE) {
      return
    }

    val currMetric = metrics[actionId].let {
      if (it == null) {
        metrics[actionId] = AssistantPanelMetricsTracker(mutableType)
      }
      metrics[actionId]!!
    }

    if (isOpen && !window.isVisible) {
      isOpen = false
      currMetric.logClose()
    }
    else if (!isOpen && window.isVisible) {
      isOpen = true
      currMetric.logOpen()
    }
  }

  private fun convertType(actionId: String): HelpPanelType {
    return when (actionId) {
      CONSTRAINT_LAYOUT_BUNDLE_ID -> HelpPanelType.CONSTRAINT_LAYOUT
      MOTION_EDITOR_BUNDLE_ID -> HelpPanelType.MOTION_LAYOUT
      FULL_HELP_BUNDLE_ID -> HelpPanelType.FULL_ALL
      NAV_EDITOR_BUNDLE_ID -> HelpPanelType.NAV_EDITOR
      else -> HelpPanelType.UNKNOWN_PANEL_TYPE
    }
  }
}
