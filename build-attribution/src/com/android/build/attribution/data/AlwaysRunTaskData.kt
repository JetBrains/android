/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.data

import org.gradle.api.internal.changedetection.TaskExecutionMode

data class AlwaysRunTaskData(val taskData: TaskData, val rerunReason: Reason) {

  enum class Reason(val message: String) {
    NO_OUTPUTS_WITHOUT_ACTIONS(TaskExecutionMode.NO_OUTPUTS_WITHOUT_ACTIONS.rebuildReason.get()),
    NO_OUTPUTS_WITH_ACTIONS(TaskExecutionMode.NO_OUTPUTS_WITH_ACTIONS.rebuildReason.get()),
    UP_TO_DATE_WHEN_FALSE((TaskExecutionMode.UP_TO_DATE_WHEN_FALSE.rebuildReason.get())),
  }
}
