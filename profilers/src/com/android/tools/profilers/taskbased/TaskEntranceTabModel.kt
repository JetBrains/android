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

import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.taskbased.tasks.TaskGridModel

/**
 * This class is to be extended by tab UI models allowing the user to select and enter a Profiler task.
 */
abstract class TaskEntranceTabModel(val profilers: StudioProfilers) {
  val taskGridModel: TaskGridModel = TaskGridModel()

  val selectedTaskType get() = taskGridModel.selectedTaskType.value
  val taskHandlers get() = profilers.taskHandlers

  /**
   * Returns whether the Profiler task button should be enabled or not. This usually means that a valid task is selected along with a valid
   * process or recording to enter the task with.
   */
  abstract val isEnterTaskButtonEnabled : Boolean

  /**
   * Handles click of start or open Profiler task button.
   */
  abstract fun onEnterTaskButtonClick()
}