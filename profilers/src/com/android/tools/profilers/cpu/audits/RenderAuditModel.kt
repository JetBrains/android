/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu.audits

import com.android.tools.adtui.model.Range
import com.android.tools.profilers.cpu.atrace.AtraceCpuCapture

/**
 * Model for the RenderAudit UI Component. Holds state and dispatches events to other models.
 */
class RenderAuditModel(capture: AtraceCpuCapture) {

  // Render Audit Data
  val tripleBuffers: List<TripleBuffer> = listOf()
  val longFrames: List<LongFrame> = listOf()
  val skippedFrames: List<SkippedFrame> = listOf()
  val auditFrames: List<AuditFrame> = listOf()
  val renderStageStats: Map<RenderStage, RenderStageStats> = mapOf()

  private val mainThreadId: Int = capture.mainThreadId
  private val renderThreadId: Int = capture.renderThreadId

  // The timeline range for a frame in Us. Used to inspect the frame under other tabs in the capture pane
  private val frameRange = Range(Double.MAX_VALUE, Double.MIN_VALUE)

  // Used by the view when selecting to inspect the main or render thread for a frame
  var frameThread: FrameThread = FrameThread.MAIN

  /**
   * Called by the {@link CpuProfilerStage} to set the {@link CaptureModel}'s selected thread id
   */
  fun getFrameThreadId(): Int = if (frameThread == FrameThread.MAIN) mainThreadId else renderThreadId

  fun setFrameRange(minUs: Double, maxUs: Double) {
    frameRange.set(minUs, maxUs)
  }

  /**
   * Returns a copy of the frame range so that we can ensure only this model can mutate the range
   */
  fun getFrameRange(): Range {
    return Range(frameRange)
  }

  enum class FrameThread {
    MAIN,
    RENDER
  }

}