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
package com.android.tools.profilers.taskbased.home

import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.taskbased.TaskEntranceTabModel
import com.android.tools.profilers.taskbased.home.selections.deviceprocesses.ProcessListModel
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.TaskTypeMappingUtils
import com.google.common.annotations.VisibleForTesting

/**
 * The TaskHomeTabModel serves as the data model for the task home tab. It owns the process list model to manage the available processes
 * to the user to select from, as well as current process selection. It also implements the behavior on start Profiler task button click,
 * reading the process and Profiler task selection and using such values to launch the Profiler task.
 */
class TaskHomeTabModel(profilers: StudioProfilers) : TaskEntranceTabModel(profilers) {
  @VisibleForTesting
  val processListModel = ProcessListModel(profilers, taskGridModel::resetTaskSelection)

  @VisibleForTesting
  val selectedDevice: Common.Device get() = processListModel.selectedDevice.value
  @VisibleForTesting
  val selectedProcess: Common.Process get() = processListModel.selectedProcess.value

  override val isEnterTaskButtonEnabled get() = selectedDevice != Common.Device.getDefaultInstance()
                                                && selectedProcess != Common.Process.getDefaultInstance()
                                                && selectedTaskType != ProfilerTaskType.UNSPECIFIED

  override fun onEnterTaskButtonClick() = profilers.setProcess(selectedDevice, selectedProcess,
                                                               TaskTypeMappingUtils.convertTaskType(selectedTaskType))
}