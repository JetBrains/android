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
package com.android.tools.idea.profilers

import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.sessions.SessionsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

/**
 * This class maps 1-to-1 with an [AndroidProfilerToolWindow] instance.
 */
class AndroidProfilerWindowManagerListener constructor(private val project: Project,
                                                       private val profilers: StudioProfilers,
                                                       private val profilersView: StudioProfilersView) : ToolWindowManagerListener {
  private var isProfilingActiveBalloonShown = false
  private var wasWindowExpanded = false

  // Tracks the tasks already attempted to be stopped, helping prevent multiple prompts to stop the task. The issue can occur when
  // stateChanged is invoked in-between stopping the current task and the current task being updated as being terminated.
  private val endedTaskIDs = mutableSetOf<Long>()

  /**
   * How the profilers should respond to the tool window's state changes is as follows:
   * 1. If the window is hidden while a session is running, we prompt to user whether they want to stop the session.
   *    If yes, we stop and kill the profilers. Otherwise, the hide action is undone and the tool strip button remain shown.
   * 2. If the window is minimized while a session is running, a balloon is shown informing users that the profilers is still running.
   */
  override fun stateChanged(toolWindowManager: ToolWindowManager) {
    // We need to query the tool window again, because it might have been unregistered when closing the project.
    val window = toolWindowManager.getToolWindow(AndroidProfilerToolWindowFactory.ID) ?: return

    val isTaskBasedUxEnabled = profilers.ideServices.featureConfig.isTaskBasedUxEnabled

    // In the Task-Based UX, determining if there is an ongoing task/recording is done via inspection of the selected session, rather than
    // the profiling session used in Session-Based UX.
    val hasAliveSession = if (isTaskBasedUxEnabled) profilers.sessionsManager.isSessionAlive
    else SessionsManager.isSessionAlive(profilers.sessionsManager.profilingSession)

    val isWindowTabHidden = !window.isShowStripeButton // Profiler window is removed from the toolbar.
    val isWindowExpanded = window.isVisible // Profiler window is expanded.
    val windowVisibilityChanged = isWindowExpanded != wasWindowExpanded
    wasWindowExpanded = isWindowExpanded

    if (isWindowTabHidden) {
      // If the task has already been attempted to be stopped, do not prompt the user again.
      val taskID = profilers.sessionsManager.selectedSession.sessionId
      if (isTaskBasedUxEnabled && endedTaskIDs.contains(taskID)) {
        return
      }

      if (hasAliveSession) {
        val hidePrompt = profilers.ideServices.persistentProfilerPreferences.getBoolean(HIDE_STOP_PROMPT, false)
        val confirm = hidePrompt || profilersView.ideProfilerComponents.createUiMessageHandler().displayOkCancelMessage(
          "Confirm Stop ${if (isTaskBasedUxEnabled) "Task" else "Profiling"}",
          "Hiding the window will stop the current ${if (isTaskBasedUxEnabled) "ongoing task" else "profiling session"}. Are you sure?",
          "Yes", "Cancel",
          null) { doNotShow: Boolean ->
          profilers.ideServices.persistentProfilerPreferences.setBoolean(HIDE_STOP_PROMPT, doNotShow)
        }

        if (!confirm) {
          window.isShowStripeButton = true
          return
        }
      }
      if (isTaskBasedUxEnabled) {
        profilers.currentTaskHandler?.let {
          it.stopTask()
          endedTaskIDs.add(taskID)
        }
      }
      else {
        profilers.stop()
      }
      return
    }

    if (isWindowExpanded) {
      isProfilingActiveBalloonShown = false
      // Preferred process is fetched and set in another way in Task-Based UX, so this doesn't apply for Task-Based UX.
      if (windowVisibilityChanged && !isTaskBasedUxEnabled) {
        val processInfo = project.getUserData(AndroidProfilerToolWindow.LAST_RUN_APP_INFO)
        if (processInfo != null && Common.Session.getDefaultInstance() == profilers.session) {
          profilers.setPreferredProcess(processInfo.deviceName, processInfo.processName) { p: Common.Process? ->
            processInfo.processFilter.invoke(p!!)
          }
        }
      }
    }
    else {
      profilers.autoProfilingEnabled = false
      if (hasAliveSession && !isProfilingActiveBalloonShown) {
        // Only shown the balloon if we detect the window is hidden for the first time.
        isProfilingActiveBalloonShown = true
        val messageHtml =
          if (isTaskBasedUxEnabled) "A task is running in the background.<br>To end the task, open the profiler and click the stop button."
          else "A profiler session is running in the background.<br>To end the session, open the profiler and click the stop button in " +
               "the Sessions pane."
        ToolWindowManager.getInstance(project).notifyByBalloon(AndroidProfilerToolWindowFactory.ID, MessageType.INFO, messageHtml)
      }
    }
  }

  companion object {
    const val HIDE_STOP_PROMPT = "profilers.hide.stop.prompt"
  }
}
