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
package com.android.tools.profilers.tasks.taskhandlers.singleartifact.memory

import com.android.sdklib.AndroidVersion
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.SupportLevel
import com.android.tools.profilers.memory.HeapProfdSessionArtifact
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.sessions.SessionArtifact
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.args.TaskArgs
import com.android.tools.profilers.tasks.args.singleartifact.memory.NativeAllocationsTaskArgs
import com.android.tools.profilers.tasks.taskhandlers.TaskHandlerUtils.findTaskArtifact
import com.intellij.util.asSafely

/**
 * This class defines the task handler to perform a native allocations task.
 */
class NativeAllocationsTaskHandler(sessionsManager: SessionsManager) : MemoryTaskHandler(sessionsManager) {
  override fun startCapture(stage: MainMemoryProfilerStage) {
    stage.startNativeAllocationCapture()
  }

  override fun stopCapture(stage: MainMemoryProfilerStage) {
    stage.stopMemoryRecording()
  }

  override fun loadTask(args: TaskArgs?): Boolean {
    if (args !is NativeAllocationsTaskArgs) {
      handleError("The task arguments (TaskArgs) supplied are not of the expected type (NativeAllocationsTaskArgs)")
      return false
    }
    val nativeAllocationsTaskArtifact = args.getMemoryCaptureArtifact()
    loadCapture(nativeAllocationsTaskArtifact)
    return true
  }

  override fun createArgs(sessionItems: Map<Long, SessionItem>,
                          selectedSession: Common.Session): NativeAllocationsTaskArgs? {
    // Finds the artifact that backs the task identified via its corresponding unique session (selectedSession).
    val artifact = findTaskArtifact(selectedSession, sessionItems, ::supportsArtifact)

    // Only if the underlying artifact is non-null should the TaskArgs be non-null.
    return if (supportsArtifact(artifact)) {
      artifact.asSafely<HeapProfdSessionArtifact>()?.let { NativeAllocationsTaskArgs(it) }
    }
    else {
      null
    }
  }

  override fun checkDeviceAndProcess(device: Common.Device, process: Common.Process) =
    SupportLevel.of(process.exposureLevel).isFeatureSupported(SupportLevel.Feature.MEMORY_NATIVE_RECORDING)
    && device.featureLevel >= AndroidVersion.VersionCodes.Q

  override fun supportsArtifact(artifact: SessionArtifact<*>?) = artifact is HeapProfdSessionArtifact

  override fun getTaskName(): String = "Native Allocations"
}