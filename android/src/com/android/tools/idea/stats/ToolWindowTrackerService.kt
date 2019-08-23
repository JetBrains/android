/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.stats

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.StudioToolWindowActionStats
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

/**
 * Tracks tool window usage by listening to state changes.
 * [ToolWindowManagerListener]'s [stateChanged] method doesn't actually contained which tool window was changed, so we
 * calculate it by tracking all registered tool window states in a state map and checking which tool window(s) state was changed.
 * Note:
 * If a tool window is active and the user opens another tool window in the same group, then the active tool window is closed and
 * the new tool window is opened. This triggers 2 events (1 close and 1 open).
 */
class ToolWindowTrackerService(private val toolWindowManager: ToolWindowManager?) : ToolWindowManagerListener {
  private val stateMap = HashMap<String, ToolWindowState>()
  private lateinit var myProject: Project

  sealed class ToolWindowState {
    object UNKNOWN : ToolWindowState()
    object OPENED : ToolWindowState()
    object CLOSED : ToolWindowState()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ToolWindowTrackerService {
      val service = ServiceManager.getService(project, ToolWindowTrackerService::class.java)
      service.myProject = project
      return service
    }
  }

  override fun toolWindowRegistered(id: String) {
    stateMap[id] = queryToolWindowState(id)
  }

  override fun stateChanged() {
    stateMap.forEach { id, previousState ->
      val currentState = queryToolWindowState(id)
      if (currentState != previousState) {
        UsageTracker.log(AndroidStudioEvent.newBuilder()
                           .setKind(AndroidStudioEvent.EventKind.STUDIO_TOOL_WINDOW_ACTION_STATS)
                           .setStudioToolWindowActionStats(StudioToolWindowActionStats.newBuilder()
                                                             .setToolWindowId(id)
                                                             .setEventType(when (currentState) {
                                                                             ToolWindowState.UNKNOWN -> StudioToolWindowActionStats.EventType.UNKNOWN_EVENT_TYPE
                                                                             ToolWindowState.OPENED -> StudioToolWindowActionStats.EventType.OPEN_EVENT_TYPE
                                                                             ToolWindowState.CLOSED -> StudioToolWindowActionStats.EventType.CLOSED_EVENT_TYPE
                                                                           })))
        stateMap[id] = currentState
      }
    }
  }

  private fun queryToolWindowState(id: String): ToolWindowState {
    val manager = toolWindowManager ?: ToolWindowManager.getInstance(myProject)
    val window = manager.getToolWindow(id)
    if (window != null) {
      return if (window.isActive) {
        ToolWindowState.OPENED
      }
      else {
        ToolWindowState.CLOSED
      }
    }
    return ToolWindowState.UNKNOWN
  }
}
