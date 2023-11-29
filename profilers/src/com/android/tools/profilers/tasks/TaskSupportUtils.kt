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
package com.android.tools.profilers.tasks

import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandler

object TaskSupportUtils {

  /**
   * Returns whether the task supports a session artifact. In the case of LiveView Task, there are no children artifacts, so it uses the
   * SessionItem (the parent session artifact). Otherwise, it uses the first and only child artifact
   */
  fun isTaskSupportedByRecording(taskType: ProfilerTaskType, taskHandler: ProfilerTaskHandler, selectedRecording: SessionItem): Boolean {
    // If no child artifact and its live view task then parent session artifact support is verified.
    if (taskType == ProfilerTaskType.LIVE_VIEW && selectedRecording.getChildArtifacts().isEmpty()) {
      return taskHandler.supportsArtifact(selectedRecording)
    }
    // If only one child artifact then its support by the task is verified.
    return selectedRecording.containsExactlyOneArtifact()
           && taskHandler.supportsArtifact(selectedRecording.getChildArtifacts().first())
  }
}