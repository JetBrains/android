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
import com.android.tools.profilers.LiveStage
import com.android.tools.profilers.sessions.SessionArtifact
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.args.TaskArgs
import com.android.tools.profilers.tasks.args.singleartifact.LiveTaskArgs
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandler
import com.android.tools.profilers.tasks.taskhandlers.TaskHandlerUtils

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
  override fun startTask(args: TaskArgs) {
    val studioProfilers = sessionsManager.studioProfilers
    val liveStage = LiveStage(studioProfilers, ::stopTask)
    studioProfilers.stage = liveStage
  }

  /**
   * Ends live view task by ending the session.
   */
  override fun stopTask() {
    sessionsManager.endSelectedSession()
  }

  /**
   * Reads the task arguments (@param args) for the backing task data, then uses it to load the task.
   *
   * Returns a boolean indicating whether it was able to cast to the correct TaskArgs subtype or not.
   */
  override fun loadTask(args: TaskArgs): Boolean {
    if (args !is LiveTaskArgs) {
      return false
    }

    val liveTaskArtifact = args.getLiveTaskArtifact()
    if (liveTaskArtifact == null) {
      handleError("The task arguments (LiveTaskArgs) supplied do not contains a valid artifact to load")
      return false
    }
    TaskHandlerUtils.executeTaskAction(action = { liveTaskArtifact.doSelect() }, errorHandler = ::handleError)
    return true
  }

  override fun createStartTaskArgs(isStartupTask: Boolean) = LiveTaskArgs(false, null)

  override fun createLoadingTaskArgs(artifact: SessionArtifact<*>) = LiveTaskArgs(false, artifact as SessionItem)

  /**
   * Returns the name of the task.
   */
  override fun getTaskName(): String {
    return "Live View"
  }

  /**
   * Always returns true since live view task is available regardless of devices feature level and process
   */
  override fun checkDeviceAndProcess(device: Common.Device, process: Common.Process) = true
}