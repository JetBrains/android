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
package com.android.tools.profilers.taskbased.task

import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.ProfilerTaskType.Companion.getNthRankedTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * This class serves as the model for the list/grid of Profiler tasks a user can select from. Each task is represented via a TaskGridItem
 * which shows the name and icon of the respective task.
 */
class TaskGridModel(private val updateProfilingProcessStartingPoint: () -> Unit) {
  /**
   * The first ranked task type is selected by default.
   */
  private val _selectedTaskType = MutableStateFlow(getNthRankedTask(0))
  val selectedTaskType = _selectedTaskType.asStateFlow()

  fun onTaskSelection(taskType: ProfilerTaskType) {
    _selectedTaskType.value = taskType
    updateProfilingProcessStartingPoint()
  }

  fun resetTaskSelection() {
    onTaskSelection(ProfilerTaskType.UNSPECIFIED)
  }
}