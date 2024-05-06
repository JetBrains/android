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
package com.android.tools.profilers.tasks.taskhandlers.singleartifact

import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.LiveStage
import com.android.tools.profilers.sessions.SessionArtifact
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.args.TaskArgs
import com.android.tools.profilers.tasks.args.singleartifact.LiveTaskArgs
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandler
import com.android.tools.profilers.tasks.taskhandlers.TaskHandlerUtils
import com.intellij.util.asSafely

class LiveTaskHandler(private val sessionsManager: SessionsManager) : ProfilerTaskHandler(sessionsManager) {

  /**
   * Returns whether the task supports a given session artifact (backing data construct).
   */
  override fun supportsArtifact(artifact: SessionArtifact<*>?): Boolean {
    return artifact is SessionItem
  }


  /**
   * Task behavior on start.
   */
  override fun startTask() {
    val studioProfilers = sessionsManager.studioProfilers
    val liveStage = LiveStage(studioProfilers)
    studioProfilers.stage = liveStage
  }

  /**
   * Task behavior on stop. This is never called in production code as the live task is self-contained (the stoppage of the task is
   * within the live stage itself) hence its empty.
   */
  override fun stopTask() {}

  /**
   * Reads the task arguments (@param args) for the backing task data, then uses it to load the task.
   *
   * Returns a boolean indicating whether it was able to cast to the correct TaskArgs subtype or not.
   */
  override fun loadTask(args: TaskArgs?): Boolean {
    if (args !is LiveTaskArgs) {
      return false
    }
    val artifact = args.getLiveTaskArtifact()
    TaskHandlerUtils.executeTaskAction(action = { artifact.doSelect() }, errorHandler = ::handleError)
    return true
  }

  /**
   * Returns the name of the task.
   */
  override fun getTaskName(): String {
    return "Live View"
  }

  /**
   * Because every task has a respective TaskArgs (arguments construct), this method allows to each task to customize how their respective
   * arguments are constructed.
   *
   * To construct the arguments to load a previous or imported task, the session (sessionItems) are passed in for inspection.
   *
   * @param sessionItems list of session items (sessions taken in the current profiler instance or from importing) that contain artifacts
   * @param selectedSession the current session (alive or not) that the current task corresponds to
   */
  override fun createArgs(sessionItems: Map<Long, SessionItem>, selectedSession: Common.Session): LiveTaskArgs? {

    // Finds the artifact that backs the task identified via its corresponding unique session (selectedSession).
    val artifact = TaskHandlerUtils.findTaskArtifact(selectedSession, sessionItems, ::supportsArtifact)

    // Only if the underlying artifact is non-null should the TaskArgs be non-null.
    return if (supportsArtifact(artifact)) {
      artifact.asSafely<SessionItem>()?.let {
        LiveTaskArgs(it)
      }
    }
    else {
      null
    }
  }

  /**
   * Always returns true since live view task is available regardless of devices feature level and process
   */
  override fun checkDeviceAndProcess(device: Common.Device, process: Common.Process) = true
}