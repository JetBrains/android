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
import com.android.tools.profilers.memory.AllocationSessionArtifact
import com.android.tools.profilers.memory.AllocationStage
import com.android.tools.profilers.memory.LegacyAllocationsSessionArtifact
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.memory.adapters.MemoryDataProvider
import com.android.tools.profilers.sessions.SessionArtifact
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.args.TaskArgs
import com.android.tools.profilers.tasks.args.singleartifact.memory.JavaKotlinAllocationsTaskArgs
import com.android.tools.profilers.tasks.args.singleartifact.memory.LegacyJavaKotlinAllocationsTaskArgs

/**
 * This class defines the task handler to perform a java/kotlin allocations task.
 */
class JavaKotlinAllocationsTaskHandler(private val sessionsManager: SessionsManager) : MemoryTaskHandler(sessionsManager) {

  val profilers get() = sessionsManager.studioProfilers

  override fun startCapture(stage: MainMemoryProfilerStage) {
    stage.startJavaKotlinAllocationCapture()
  }

  override fun stopTask() {
    profilers.taskHomeTabModel.selectedDevice?.device?.let { device ->
      // For legacy (pre-O api devices), we can invoke the stop via the interim stage: MainMemoryProfilerStage.
      if (device.featureLevel < AndroidVersion.VersionCodes.O) {
        if (profilers.stage is MainMemoryProfilerStage) {
          (profilers.stage as MainMemoryProfilerStage).stopMemoryRecording()
        }
      }
      // For non-legacy (O+ api devices) allocation tracking, stopping a Java/Kotlin Allocations capture is invoked and only accessible
      // via the Allocations stage itself.
      else {
        if (profilers.stage is AllocationStage) {
          (profilers.stage as AllocationStage).stopTracking()
        }
      }
    }
  }

  override fun loadTask(args: TaskArgs): Boolean {
    if (args !is LegacyJavaKotlinAllocationsTaskArgs && args !is JavaKotlinAllocationsTaskArgs) {
      handleError("The task arguments (TaskArgs) supplied are not of the expected type (JavaKotlinAllocationTaskArgs)")
      return false
    }

    val javaKotlinAllocationTaskArgs = args as? LegacyJavaKotlinAllocationsTaskArgs ?: args as JavaKotlinAllocationsTaskArgs
    val javaKotlinAllocationsTaskArtifact = javaKotlinAllocationTaskArgs.getAllocationSessionArtifact()
    if (javaKotlinAllocationsTaskArtifact == null) {
      handleError("The task arguments (AllocationsTaskArgs) supplied do not contains a valid artifact to load")
      return false
    }
    loadCapture(javaKotlinAllocationsTaskArtifact)
    return true
  }

  override fun createStartTaskArgs(isStartupTask: Boolean): TaskArgs {
    return if (MemoryDataProvider.getIsLiveAllocationTrackingSupported(profilers)) {
      JavaKotlinAllocationsTaskArgs(false, null)
    } else {
      LegacyJavaKotlinAllocationsTaskArgs(false, null)
    }
  }

  override fun createLoadingTaskArgs(artifact: SessionArtifact<*>) = when (artifact) {
    is LegacyAllocationsSessionArtifact -> LegacyJavaKotlinAllocationsTaskArgs(false, artifact)
    is AllocationSessionArtifact -> JavaKotlinAllocationsTaskArgs(false, artifact)
    else -> throw IllegalStateException("Unexpected artifact type: $artifact")
  }

  override fun checkDeviceAndProcess(device: Common.Device, process: Common.Process) =
    SupportLevel.of(process.exposureLevel).isFeatureSupported(SupportLevel.Feature.MEMORY_JVM_RECORDING)

  override fun supportsArtifact(artifact: SessionArtifact<*>?): Boolean {
    return artifact is AllocationSessionArtifact || artifact is LegacyAllocationsSessionArtifact
  }

  override fun getTaskName(): String {
    return "Java/Kotlin Allocations"
  }
}