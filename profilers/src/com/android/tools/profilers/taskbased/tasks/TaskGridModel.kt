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
package com.android.tools.profilers.taskbased.tasks

import com.android.tools.profilers.tasks.ProfilerTaskType
import icons.StudioIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * This class serves as the model for the list/grid of Profiler tasks a user can select from. Each task is represented via a TaskGridItem
 * which shows the name and icon of the respective task.
 */
class TaskGridModel {
  val tasks: List<TaskGridItemModel>
  private val _selectedTaskType = MutableStateFlow(ProfilerTaskType.UNSPECIFIED)
  val selectedTaskType = _selectedTaskType.asStateFlow()

  fun onTaskSelection(taskType: ProfilerTaskType) {
    _selectedTaskType.value = taskType
  }

  fun resetTaskSelection() {
    onTaskSelection(ProfilerTaskType.UNSPECIFIED)
  }

  fun getTaskGridItem(taskType: ProfilerTaskType): TaskGridItemModel? = tasks.firstOrNull { it.type == taskType }

  init {
    tasks = listOf(
      TaskGridItemModel(
        type = ProfilerTaskType.CALLSTACK_SAMPLE,
        iconPath = "studio/icons/profiler/sessions/cpu.svg",
      ),
      TaskGridItemModel(
        type = ProfilerTaskType.SYSTEM_TRACE,
        iconPath = "studio/icons/profiler/sessions/cpu.svg",
      ),
      TaskGridItemModel(
        type = ProfilerTaskType.JAVA_KOTLIN_METHOD_TRACE,
        iconPath = "studio/icons/profiler/sessions/cpu.svg",
      ),
      TaskGridItemModel(
        type = ProfilerTaskType.JAVA_KOTLIN_METHOD_SAMPLE,
        iconPath = "studio/icons/profiler/sessions/cpu.svg",
      ),
      TaskGridItemModel(
        type = ProfilerTaskType.HEAP_DUMP,
        iconPath = "studio/icons/profiler/sessions/heap.svg",
      ),
      TaskGridItemModel(
        type = ProfilerTaskType.NATIVE_ALLOCATIONS,
        iconPath = "studio/icons/profiler/sessions/allocations.svg",
      ),
      TaskGridItemModel(
        type = ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS,
        iconPath = "studio/icons/profiler/sessions/allocations.svg",
      ),
    )
  }

  companion object {
    const val DISABLED_TASK_ICON = "studio/icons/profiler/sidebar/issue.svg"
  }
}