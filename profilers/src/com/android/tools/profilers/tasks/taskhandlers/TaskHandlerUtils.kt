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
package com.android.tools.profilers.tasks.taskhandlers

import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.ProfilerTaskType
import com.android.tools.profilers.sessions.SessionArtifact
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.tasks.TaskTypeMappingUtils

object TaskHandlerUtils {

  /**
   * This helper function executes a task action if the supplied 'arg' or argument in non-null. Otherwise, it handles the null case or the
   * exception case accordingly.
   */
  fun executeTaskAction(action: () -> Unit, errorHandler: (String) -> Unit) {
    try {
      action()
    } catch (e: Exception) {
      errorHandler(e.toString())
    }
  }

  /**
   * This method searches the sessions (SessionItems) to find one that...
   * 1. Exists in the mapping of session ids to SessionItems (registration in this map from SessionsManager verifies its past existence)
   * 2. An artifact of the currently selected session
   * 3. Is supported by the respective task (task handlers calling this method send in their own override of the supportsTask method)
   * 4. Has one child artifact or no child artifact when it's a live view task
   *
   * This logic applies for task artifacts contained in imported and non-imported sessions.
   */
  fun findTaskArtifact(
    selectedSession: Common.Session,
    sessionIdToSessionItems: Map<Long, SessionItem>,
    supportsArtifact: (SessionArtifact<*>) -> Boolean): SessionArtifact<*>? {
    val sessionItem = sessionIdToSessionItems[selectedSession.sessionId]

    return sessionItem?.let {
      val childArtifacts = it.getChildArtifacts()

      // If no child artifact and its live view task then parent session artifact is returned.
      if (childArtifacts.isEmpty() && it.profilers.sessionsManager.currentTaskType == TaskTypeMappingUtils.convertTaskType(
          ProfilerTaskType.LIVE_VIEW)) {
        it
      }
      // Verify there is only one child artifact and that it is supported by the task.
      else if (childArtifacts.size == 1 && supportsArtifact(childArtifacts[0])) {
        childArtifacts[0]
      }
      else {
        null
      }
    }
  }
}