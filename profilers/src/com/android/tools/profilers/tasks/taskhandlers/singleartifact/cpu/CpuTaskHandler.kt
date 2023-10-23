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
package com.android.tools.profilers.tasks.taskhandlers.singleartifact.cpu

import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.cpu.CpuCaptureSessionArtifact
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.config.ProfilingConfiguration
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.args.TaskArgs
import com.android.tools.profilers.tasks.args.singleartifact.cpu.CpuTaskArgs
import com.android.tools.profilers.tasks.taskhandlers.TaskHandlerUtils.findTaskArtifact
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.SingleArtifactTaskHandler
import com.intellij.util.asSafely

abstract class CpuTaskHandler(private val sessionsManager: SessionsManager) : SingleArtifactTaskHandler<CpuProfilerStage>(sessionsManager) {
  override fun setupStage() {
    val studioProfilers = sessionsManager.studioProfilers
    val stage = CpuProfilerStage(studioProfilers, this::stopTask)
    stage.profilerConfigModel.profilingConfiguration = getCpuRecordingConfig()
    studioProfilers.stage = stage
    super.stage = stage
  }

  override fun startCapture(stage: CpuProfilerStage) {
    stage.startCpuRecording()
  }

  override fun stopCapture(stage: CpuProfilerStage) {
    stage.stopCpuRecording()
  }

  override fun loadTask(args: TaskArgs?): Boolean {
    if (args !is CpuTaskArgs) {
      handleError("The task arguments (TaskArgs) supplied are not of the expected type (CpuTaskArgs)")
      return false
    }
    loadCapture(args.getCpuCaptureArtifact())
    return true
  }

  override fun createArgs(
    sessionItems: Map<Long, SessionItem>,
    selectedSession: Common.Session
  ): CpuTaskArgs? {
    val artifact = findTaskArtifact(selectedSession, sessionItems, ::supportsArtifact)

    // Only if the underlying artifact is non-null should the TaskArgs be non-null
    return if (supportsArtifact(artifact)) {
      artifact.asSafely<CpuCaptureSessionArtifact>()?.let { CpuTaskArgs(it) }
    }
    else {
      null
    }
  }

  override fun checkDeviceAndProcess(device: Common.Device, process: Common.Process) =
    device.featureLevel >= getCpuRecordingConfig().requiredDeviceLevel

  protected abstract fun getCpuRecordingConfig(): ProfilingConfiguration
}