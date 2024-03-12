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
package com.android.tools.profilers.tasks.taskhandlers

import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.LiveTaskHandler
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.cpu.CallstackSampleTaskHandler
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.cpu.JavaKotlinMethodRecordingTaskHandler
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.cpu.SystemTraceTaskHandler
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.memory.HeapDumpTaskHandler
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.memory.JavaKotlinAllocationsTaskHandler
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.memory.NativeAllocationsTaskHandler

object ProfilerTaskHandlerFactory {
  fun createTaskHandlers(sessionsManager: SessionsManager): Map<ProfilerTaskType, ProfilerTaskHandler> {
    val taskHandlers: MutableMap<ProfilerTaskType, ProfilerTaskHandler> = mutableMapOf()
    val isTraceboxEnabled = sessionsManager.studioProfilers.ideServices.featureConfig.isTraceboxEnabled
    taskHandlers[ProfilerTaskType.SYSTEM_TRACE] = SystemTraceTaskHandler(sessionsManager, isTraceboxEnabled)
    taskHandlers[ProfilerTaskType.CALLSTACK_SAMPLE] = CallstackSampleTaskHandler(sessionsManager)
    taskHandlers[ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING] = JavaKotlinMethodRecordingTaskHandler(sessionsManager)
    taskHandlers[ProfilerTaskType.HEAP_DUMP] = HeapDumpTaskHandler(sessionsManager)
    taskHandlers[ProfilerTaskType.NATIVE_ALLOCATIONS] = NativeAllocationsTaskHandler(sessionsManager)
    taskHandlers[ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS] = JavaKotlinAllocationsTaskHandler(sessionsManager)
    taskHandlers[ProfilerTaskType.LIVE_VIEW] = LiveTaskHandler(sessionsManager)
    return taskHandlers
  }
}