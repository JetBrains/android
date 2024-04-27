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
package com.android.tools.idea.common.assistant

import com.android.tools.idea.assistant.AssistantBundleCreator
import com.android.tools.idea.assistant.AssistantToolWindowService.Companion.TOOL_WINDOW_TITLE
import com.android.tools.idea.assistant.datamodel.TutorialBundleData
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.DesignEditorHelpPanelEvent.HelpPanelType
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import java.net.URL

/** Pairs plugin bundle id to the tutorial bundle xml */
data class HelpPanelBundle(val bundleId: String, val bundleXml: String)

/** Base tutorial bundle xml creator. */
open class LayoutEditorHelpPanelAssistantBundleCreatorBase(val type: HelpPanelBundle) :
  AssistantBundleCreator {
  override fun getBundleId(): String {
    return type.bundleId
  }

  override fun getBundle(project: Project): TutorialBundleData? {
    return null
  }

  override fun getConfig(): URL? {
    return javaClass.getResource(type.bundleXml)
  }
}

/**
 * Assistant Panel tools listener. It listens to assistatnt panel related actions (e.g. open panel,
 * close panel etc) within a project.
 */
class HelpPanelToolWindowListener private constructor(private var project: Project) :
  ToolWindowManagerListener, Disposable {

  companion object {
    /**
     * Maps action id to the HelpPanelType for metric tracking.
     *
     * @param actionId the Bundle Id used for the assistant action
     * @param helpPanelType type to use for usage tracking.
     */
    val map = HashMap<String, HelpPanelType>()

    @VisibleForTesting val projectToListener = HashMap<Project, HelpPanelToolWindowListener>()

    /** Ensure that listener is registered for the project. */
    fun registerListener(project: Project) {
      if (!projectToListener.containsKey(project)) {
        projectToListener[project] = HelpPanelToolWindowListener(project)
      }
    }
  }

  // Is tool window open or not.
  private var isOpen = false
  private var type: HelpPanelType = HelpPanelType.UNKNOWN_PANEL_TYPE
  private var currActionId: String = ""

  private val metrics = HashMap<String, AssistantPanelMetricsTracker>()
  private val currMetric: AssistantPanelMetricsTracker?
    get() = metrics[currActionId]

  init {
    project.messageBus.connect(project).subscribe(ToolWindowManagerListener.TOPIC, this)
    Disposer.register(project, this)
  }

  override fun dispose() {
    closeAndRemove()
  }

  override fun toolWindowUnregistered(id: String, toolWindow: ToolWindow) {
    closeAndRemove()
  }

  private fun closeAndRemove() {
    if (isOpen) {
      currMetric?.logClose()
      isOpen = false
    }
    projectToListener.remove(project)
  }

  override fun stateChanged() {
    val window = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_TITLE) ?: return
    currActionId = window.helpId ?: ""
    type = map[currActionId] ?: return
    if (type == HelpPanelType.UNKNOWN_PANEL_TYPE) {
      return
    }
    metrics.putIfAbsent(currActionId, AssistantPanelMetricsTracker(type))

    if (isOpen && !window.isVisible) {
      isOpen = false
      currMetric!!.logClose()
      metrics.remove(currActionId)
    } else if (!isOpen && window.isVisible) {
      isOpen = true
      currMetric!!.logOpen()
    }
  }
}
