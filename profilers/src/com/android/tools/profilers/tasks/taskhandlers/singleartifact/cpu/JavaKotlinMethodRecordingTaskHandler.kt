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

import com.android.tools.profilers.cpu.CpuCaptureSessionArtifact
import com.android.tools.profilers.cpu.config.ArtInstrumentedConfiguration
import com.android.tools.profilers.cpu.config.ArtSampledConfiguration
import com.android.tools.profilers.cpu.config.ProfilingConfiguration
import com.android.tools.profilers.sessions.SessionArtifact
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel

class JavaKotlinMethodRecordingTaskHandler(private val sessionsManager: SessionsManager) : CpuTaskHandler(sessionsManager) {
  override fun getCpuRecordingConfig(): ProfilingConfiguration {
    val taskHomeTabModel = sessionsManager.studioProfilers.taskHomeTabModel
    val taskRecordingMode = taskHomeTabModel.taskRecordingType.value
    return when (taskRecordingMode) {
      TaskHomeTabModel.TaskRecordingType.SAMPLED ->  ArtSampledConfiguration("Java/Kotlin Method Sample (legacy)")
      TaskHomeTabModel.TaskRecordingType.INSTRUMENTED ->  ArtInstrumentedConfiguration("Java/Kotlin Method Trace")
    }
  }

  override fun supportsArtifact(artifact: SessionArtifact<*>?) =
    artifact is CpuCaptureSessionArtifact
    && artifact.artifactProto.hasConfiguration()
    && artifact.artifactProto.configuration.hasArtOptions()

  override fun getTaskName() = "Java/Kotlin Method Recording"
}