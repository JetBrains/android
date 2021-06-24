/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.model.DefaultTimeline
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.Timeline
import com.android.tools.perflib.vmtrace.ClockType
import com.android.tools.profiler.proto.Cpu.CpuTraceType

open class BaseCpuCapture(/**
                           * ID of the trace used to generate the capture.
                           */
                          private val traceId: Long,
                          /**
                           * Technology used to generate the capture.
                           */
                          private val type: CpuTraceType,
                          range: Range,
                          captureTrees: Map<CpuThreadInfo, CaptureNode>) : CpuCapture {
  private val availableThreads: Set<CpuThreadInfo>
  private val threadIdToNode: Map<Int, CaptureNode>
  private val mainThreadId: Int
  private var clockType: ClockType

  init {
    availableThreads = captureTrees.keys
    threadIdToNode = captureTrees.mapKeys { it.key.id }
    // If the trace is empty, use [NO_THREAD_ID].
    mainThreadId = (availableThreads.find { it.isMainThread } ?: captureTrees.maxBy { it.value.duration }?.key)?.id ?: NO_THREAD_ID
    clockType = threadIdToNode[mainThreadId]?.clockType ?: ClockType.GLOBAL
  }

  companion object {
    /**
     * A placeholder thread ID when main thread doesn't exist.
     */
    const val NO_THREAD_ID = -1
  }

  /**
   * The CPU capture has its own [Timeline] for the purpose of exposing a variety of [Range]s.
   */
  private val timeline = DefaultTimeline().apply {
    dataRange.set(range)
    viewRange.set(range)
  }

  override fun getMainThreadId() = mainThreadId
  override fun getTimeline() = timeline
  override fun getCaptureNode(threadId: Int) = threadIdToNode[threadId]
  override fun getThreads() = availableThreads
  override fun getCaptureNodes() = threadIdToNode.values
  override fun containsThread(threadId: Int) = threadId in threadIdToNode
  override fun getTraceId() = traceId

  override fun updateClockType(clockType: ClockType) {
    // Avoid traversing the capture trees if there is no change.
    if (this.clockType != clockType) {
      this.clockType = clockType
      for (tree in captureNodes) {
        tree.descendantsStream.forEach { it.clockType = clockType }
      }
    }
  }

  // It would be better if we have this type of information embedded in the enum
  // but the enum comes from a proto definition for now.
  // Right now, the only trace technology that supports dual clock is ART.
  override fun isDualClock() = type == CpuTraceType.ART
  override fun getType() = type
}