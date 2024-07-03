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
package com.android.tools.profilers.taskbased.logging

import com.android.tools.profilers.taskbased.home.TaskHomeTabModel.ProfilingProcessStartingPoint
import com.android.tools.profilers.tasks.ProfilerTaskType

object TaskLoggingUtils {

  fun buildStartTaskLogMessage(taskType: ProfilerTaskType,
                               profilingProcessStartingPoint: ProfilingProcessStartingPoint,
                               isProcessProfileable: Boolean): String {
    val message = StringBuilder()
    message.append(
      "Attempting to start the '${taskType.description}' task " +
      "from ${if (profilingProcessStartingPoint == ProfilingProcessStartingPoint.NOW) "now (non-startup)" else "process start (startup)"}")

    if (profilingProcessStartingPoint == ProfilingProcessStartingPoint.PROCESS_START) {
      message.append(".")
      return message.toString()
    }

    message.append(" with a ${if (isProcessProfileable) "'profileable'" else "'debuggable'"} process.")
    return message.toString()
  }
}