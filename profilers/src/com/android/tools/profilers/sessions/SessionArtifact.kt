/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.sessions

import com.android.tools.adtui.model.StreamingTimeline
import com.android.tools.adtui.model.updater.Updatable
import com.android.tools.idea.protobuf.GeneratedMessageV3
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.SessionMetaData
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.CpuCaptureSessionArtifact
import java.io.OutputStream

/**
 * A SessionArtifact is any session-related entity that should show up in the sessions panel as its own row. (e.g. A session, a memory
 * heap dump, a CPU capture, etc).
 */
interface SessionArtifact<T : GeneratedMessageV3> : Updatable {
  /**
   * @return the [StudioProfilers] instance.
   */
  val profilers: StudioProfilers

  /**
   * @return the [Common.Session] instance that this artifact belongs to.
   */
  val session: Common.Session

  /**
   * @return the proto object that backs this artifact. Note - the mapping between a proto and its [SessionArtifact] is 1:1, so we
   * can use this object to check if the artifact itself is up to date.
   */
  val artifactProto: T

  /**
   * @return the [Common.Session] instance that this artifact belongs to.
   */
  val sessionMetaData: SessionMetaData

  /**
   * @return the name used for display.
   */
  val name: String

  /**
   * @return the timestamp relative to the session's start time when this artifact was created/took place.
   */
  val timestampNs: Long

  /**
   * @return whether the artifact is still in progress.
   */
  val isOngoing: Boolean

  /**
   * @return whether the artifact can be exported to disk for later use.
   */
  val canExport: Boolean

  /**
   * The [SessionArtifact] has been selected. Check if it's a reselection, if so, ignore selection.
   * If not, continue to do the respective on selection behavior.
   */
  fun onSelect() {
    if (isTopLevelArtifact()) {
      profilers.sessionsManager.registerSelectedArtifactProto(this.artifactProto)
    }
    else if (!profilers.sessionsManager.selectArtifactProto(this.artifactProto)) {
      return;
    }

    doSelect()
  }

  /**
   * Perform the corresponding navigation and selection change in the model.
   */
  fun doSelect()

  /**
   * Export operation to the given outputStream.
   */
  fun export(outputStream: OutputStream) {}

  override fun update(elapsedNs: Long) {}

  /**
   * Differentiate between parent session artifacts and their respective child artifacts
   * as both are of type [SessionArtifact].
   */
  fun isTopLevelArtifact(): Boolean {
    return this is SessionItem
  }

  /**
   * Detects whether the artifact was generated using the tracing api.
   */
  fun isInitiatedByApi(): Boolean {
    // Only CPU traces can be initialed via api
    return this is CpuCaptureSessionArtifact &&
           this.artifactProto.hasConfiguration() &&
           this.artifactProto.configuration.initiationType == Trace.TraceInitiationType.INITIATED_BY_API
  }

  companion object {
    /**
     * Helper method to jump to the ongoing capture. We don't jump to live immediately because the ongoing capture might not fit the current
     * zoom level. So first we adjust the zoom level to fit the current size of the ongoing capture + 10% of the view range, so the user can
     * see the capture animating for a while before it takes the entire view range.
     */
    @JvmStatic
    fun navigateTimelineToOngoingCapture(timeline: StreamingTimeline, startTimeUs: Long) {
      val viewRange90PercentLength = 0.9 * timeline.viewRange.length
      val currentOngoingCaptureLength = timeline.dataRange.max - startTimeUs
      if (currentOngoingCaptureLength > viewRange90PercentLength) {
        timeline.zoom(currentOngoingCaptureLength - viewRange90PercentLength)
      }

      // Then jump to live.
      timeline.isStreaming = true
      timeline.setIsPaused(false)
    }

    const val CAPTURING_SUBTITLE = "Recording..."
  }
}