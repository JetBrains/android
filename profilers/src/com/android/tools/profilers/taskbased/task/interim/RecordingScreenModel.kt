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
package com.android.tools.profilers.taskbased.task.interim

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.updater.Updatable
import com.android.tools.profilers.InterimStage
import com.android.tools.profilers.RecordingOptionsModel
import com.android.tools.profilers.StreamingStage
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.tasks.ProfilerTaskType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

class RecordingScreenModel<T>(stage: T) : AspectObserver(), Updatable where T : StreamingStage, T : InterimStage {

  private val taskType = stage.studioProfilers.sessionsManager.currentTaskType
  val isUserStoppable = taskType != ProfilerTaskType.HEAP_DUMP
  val taskName = taskType.description

  private val _isStopRecordingButtonEnabled = MutableStateFlow(false)
  val isStopRecordingButtonEnabled = _isStopRecordingButtonEnabled.asStateFlow()

  private val _elapsedNs = MutableStateFlow(0L)
  val elapsedNs = _elapsedNs.asStateFlow()

  init {
    val recordingModel: RecordingOptionsModel? =
      when (stage) {
        is CpuProfilerStage -> stage.recordingModel
        is MainMemoryProfilerStage -> stage.recordingOptionsModel
        else -> null
      }
    recordingModel?.addDependency(this)?.onChange(RecordingOptionsModel.Aspect.RECORDING_CHANGED) {
      enableStopRecordingButton(recordingModel.canStop())
    }
  }

  private fun enableStopRecordingButton(enable: Boolean) {
    _isStopRecordingButtonEnabled.value = enable
  }

  val stopRecordingAction = Runnable { stage.stop() }

  fun formatElapsedTime(elapsedNs: Long): String {
    val minutes = TimeUnit.NANOSECONDS.toMinutes(elapsedNs)
    val seconds = TimeUnit.NANOSECONDS.toSeconds(elapsedNs) - TimeUnit.MINUTES.toSeconds(minutes)
    return "$minutes min, $seconds sec"
  }

  override fun update(elapsedNs: Long) {
    _elapsedNs.value += elapsedNs
  }
}
