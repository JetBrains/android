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

import com.android.tools.profilers.memory.AllocationStage
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * This action is purely for testing purposes and can only be used to stop Java Kotlin Allocations task. These profiler task actions are
 * to be performed in a sequential format:
 *
 * ProfilerSelectDeviceAction -> ProfilerSelectProcessAction -> Select Java Kotlin Allocations Task ->
 * SetProfilingStartingPointToNowAction / SetProfilingStartingPointToProcessStartAction  ->
 * StartProfilerTaskAction -> StopJavaKotlinAllocationsTaskAction
 *
 * Note: This test only action is for the Java/Kotlin Allocations Task for O+ devices. Because before api O the test action would be defined
 * differently (e.g. we would need to call stopTask instead of stopTracking).
 */
class StopJavaKotlinAllocationsTaskAction : ProfilerTaskActionBase() {
  @Suppress("VisibleForTests")
  override fun actionPerformed(e: AnActionEvent) {
    val profilers = getStudioProfilers(e.project!!)

    // Stop task
    (profilers.stage as AllocationStage).stopTracking()
  }
}