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
package com.android.tools.profilers.leakcanary

import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.sessions.SessionArtifact
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.taskbased.home.StartTaskSelectionError
import com.android.tools.profilers.tasks.args.TaskArgs
import com.android.tools.profilers.tasks.args.singleartifact.leakcanary.LeakCanaryTaskArgs
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.SingleArtifactTaskHandler

class LeakCanaryTaskHandler(private val sessionsManager: SessionsManager): SingleArtifactTaskHandler<LeakCanaryModel>(sessionsManager) {

  override fun setupStage() {
    val studioProfilers = sessionsManager.studioProfilers
    val stage = LeakCanaryModel(studioProfilers)
    // Set the new stage to be the current stage in the Profiler.
    studioProfilers.stage = stage
    // Set the new stage to be this task handler's stage, which can now be used ot start and stop captures.
    super.stage = stage
  }

  override fun startCapture(stage: LeakCanaryModel) {
    stage.startListening()
  }

  override fun stopCapture(stage: LeakCanaryModel) {
    stage.stopListening()
  }

  override fun loadTask(args: TaskArgs): Boolean {
    if (args !is LeakCanaryTaskArgs) {
      handleError("The task arguments (TaskArgs) supplier are not of the expected type (LeakCanaryTaskArgs)")
      return false
    }

    val leakCanaryArtifact = args.getLeakCanaryArtifact()
    if (leakCanaryArtifact == null) {
      handleError("The task arguments (LeakCanaryTaskArgs) supplied do not contains a valid artifact to load")
      return false
    }
    loadCapture(leakCanaryArtifact)
    return true
  }

  override fun getTaskName() = "LeakCanary"

  override fun supportsArtifact(artifact: SessionArtifact<*>?): Boolean {
    return artifact is LeakCanarySessionArtifact
  }

  override fun createStartTaskArgs(isStartupTask: Boolean) = LeakCanaryTaskArgs(false, null)

  override fun createLoadingTaskArgs(artifact: SessionArtifact<*>) =
    LeakCanaryTaskArgs(false, artifact as LeakCanarySessionArtifact)

  /**
   * Always returns true since leak canary task is available regardless of devices feature level and process
   */
  override fun checkSupportForDeviceAndProcess(device: Common.Device, process: Common.Process): StartTaskSelectionError? = null
}