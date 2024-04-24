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
package com.android.tools.idea.profilers.actions

import com.android.tools.profilers.taskbased.home.TaskHomeTabModel
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * These profiler task actions are to be performed in a sequential format:
 *
 * ProfilerSelectDeviceAction -> ProfilerSelectProcessAction -> Select Profiler Java/Kotlin Method Recording Task ->
 * Select dropdown action to SetProfilingStartingPointToNowAction -> SetRecordingTypeToSamplingAction (Sampling) ->
 * StartProfilerTaskAction -> StopProfilerTaskAction
 */
class SetRecordingTypeToSamplingAction : ProfilerTaskActionBase() {
  @Suppress("VisibleForTests")
  override fun actionPerformed(e: AnActionEvent) {
    selectRecordingType(e.project!!, TaskHomeTabModel.TaskRecordingType.SAMPLED)
  }
}