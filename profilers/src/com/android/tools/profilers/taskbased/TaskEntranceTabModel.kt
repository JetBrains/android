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
package com.android.tools.profilers.taskbased

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.sessions.SessionAspect
import com.android.tools.profilers.taskbased.pastrecordings.PastRecordingsTabModel
import com.android.tools.profilers.taskbased.task.TaskGridModel

/**
 * This class is to be extended by tab UI models allowing the user to select and enter a Profiler task.
 */
abstract class TaskEntranceTabModel(val profilers: StudioProfilers): AspectObserver() {
  val taskGridModel: TaskGridModel = TaskGridModel(::updateProfilingProcessStartingPointDropdown)

  val selectedTaskType get() = taskGridModel.selectedTaskType.value
  val taskHandlers get() = profilers.taskHandlers

  val sessionsManager get() = profilers.sessionsManager

  open fun updateProfilingProcessStartingPointDropdown() {}

  /**
   * Handles click of start or open Profiler task button.
   */
  open fun onEnterTaskButtonClick() {
    val isTaskOngoing = profilers.sessionsManager.isSessionAlive

    // If the existing Profiler task tab is already showing the selected recording, there is no need to load the task again. Instead, the
    // existing task tab will be re-opened.
    if (this is PastRecordingsTabModel) {
      val selectedSession = selectedRecording!!.session
      if (selectedSession == profilers.session) {
        profilers.openTaskTab()
        return
      }
    }

    val currentTaskHandler = profilers.currentTaskHandler
    // If the current task handler is non-null, this indicates that a task tab is currently open
    if (currentTaskHandler != null) {
      val dialogTitle = if (isTaskOngoing) "Confirm Termination of Ongoing Recording" else "Confirm Close of Currently Open Task"
      // Prompt/warn the user that there can only be one task tab open at a time, so starting a new task will close the current one first.
      val dialogMsg = "Profiler displays only one task at this time. Starting a new task or opening a task " +
                      "recording will ${if (isTaskOngoing) "terminate your ongoing recording." else "close your currently open task."}"

      // Retrieve user selection for "Do not ask again" checkbox on dialog. Prevent dialog from showing if true.
      val hidePrompt = profilers.ideServices.temporaryProfilerPreferences.getBoolean(HIDE_NEW_TASK_PROMPT, false)
      val confirm = hidePrompt || profilers.ideServices.openOkCancelDialog(dialogMsg, dialogTitle) { doNotShow: Boolean ->
        // Save user's preference to not see dialog again.
        profilers.ideServices.temporaryProfilerPreferences.setBoolean(HIDE_NEW_TASK_PROMPT, doNotShow)
      }

      // If the user cancels, we cancel the stoppage of the current task and start of the new one.
      if (!confirm) {
        return
      }

      // If there is an ongoing task, then the ongoing task is stopped. Then, on notification of the stoppage, the new task is entered.
      if (isTaskOngoing) {
        sessionsManager.addDependency(this).onChange(SessionAspect.ONGOING_SESSION_NEWLY_ENDED) {
          sessionsManager.removeDependencies(this)
          doEnterTaskButton()
        }
        currentTaskHandler.stopTask()
      }
      // If the current task is already terminated, then the new task is entered.
      else {
        doEnterTaskButton()
      }
    }
    // If there is no current task/no task tab open, the new task is entered.
    else {
      doEnterTaskButton()
    }
  }

  /**
   * Behavior on task entrance. A task can be entered via the home tab (invoking a new task) or via the past recordings tab (opening a
   * previously recorded task).
   */
  protected abstract fun doEnterTaskButton()

  companion object {
    const val HIDE_NEW_TASK_PROMPT = "profilers.hide.new.task.prompt"
  }
}