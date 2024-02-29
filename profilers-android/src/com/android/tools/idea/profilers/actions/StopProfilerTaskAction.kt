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
package com.android.tools.idea.profilers.actions

import android.annotation.SuppressLint
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.profilers.AndroidProfilerToolWindowFactory
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

/**
 * These profiler task actions are to be performed in a sequential format:
 *
 * ProfilerSelectDeviceAction -> ProfilerSelectProcessAction -> Select Profiler Tasks (System trace, Callstack sample, etc.) ->
 * Select dropdown actions (SetProfilingStartingPointToNowAction or SetProfilingStartingPointToProcessStartAction) ->
 * StartProfilerTaskAction -> StopProfilerTaskAction
 */
class StopProfilerTaskAction : ProfilerTaskActionBase(
  "Stop Profiler Task",
  "Stop a task in the current profiling session"
) {
  /**
   * This action is purely for testing purposes, the action stops the current profiling task, and is only limited to stopping
   * profiling tasks: System Trace, Callstack sample, Native Allocations, Java/Kotlin trace.
   */
  @SuppressLint("VisibleForTests")
  override fun actionPerformed(e: AnActionEvent) {
    val profilers = getStudioProfilers(e.project!!)
    val currentTask = profilers.sessionsManager.currentTaskType

    //Stop task
    profilers.taskHandlers[currentTask]!!.stopTask()
  }
}