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

import com.android.tools.idea.profilers.AndroidProfilerToolWindowFactory
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project

/**
 * This is the base action class for all the task actions related to task-based UX.
 * It provides common functionality for all profiler test actions which will be used by task based profiler-integration tests
 *
 * All Profiler task actions should extend this class.
 */
abstract class ProfilerTaskActionBase : AnAction() {

  protected fun getStudioProfilers(project: Project) =
    AndroidProfilerToolWindowFactory.getProfilerToolWindow(project)!!.profilers

  protected fun getTaskHomeTabModel(project: Project) =
    getStudioProfilers(project).taskHomeTabModel

  protected fun selectTask(project: Project, profilerTask: ProfilerTaskType) {
    val taskHomeTabModel = getTaskHomeTabModel(project)
    taskHomeTabModel
      .taskGridModel
      .onTaskSelection(profilerTask)
  }

  protected fun selectRecordingType(project: Project, recordingType: TaskHomeTabModel.TaskRecordingType) {
    getTaskHomeTabModel(project).setTaskRecordingType(recordingType)
  }

  protected fun setProfilingProcessStartingPoint(
    project: Project,
    profilingProcessStartingPoint: TaskHomeTabModel.ProfilingProcessStartingPoint) {
    val taskHomeTabModel = getTaskHomeTabModel(project)
    taskHomeTabModel.setProfilingProcessStartingPoint(profilingProcessStartingPoint)
  }
}