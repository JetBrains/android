/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.profilers.cpu.systemtrace

import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.RangedSeries
import com.android.tools.adtui.model.SeriesData
import com.android.tools.adtui.model.StateChartModel
import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profilers.cpu.LazyDataSeries
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameEvent.Data
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameEvent.Padding
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.TimeUnit

/**
 * Track model for a frame lifecycle track representing Android frames in a specific phase.
 */
class AndroidFrameEventTrackModel
    @VisibleForTesting
    constructor(eventSeries: List<RangedSeries<AndroidFrameEvent>>) : StateChartModel<AndroidFrameEvent>() {

  constructor(androidFrameEvents: List<TraceProcessor.AndroidFrameEventsResult.FrameEvent>, viewRange: Range)
    : this(androidFrameEvents.groupBy { it.depth }.toSortedMap().values
             .map { it.padded() }
             .filterNot { it.isEmpty() }
             .map { series -> RangedSeries(viewRange, LazyDataSeries { series }) })

  var activeSeriesIndex = -1
    set(index) {
      if (field != index) {
        field = index
        changed(Aspect.MODEL_CHANGED)
      }
    }

  init {
    eventSeries.forEach(::addSeries)
  }

  /**
   * Wrapper class for organizing track sort order, display name, etc.
   */
  private data class TrackMetadata(val sortOrder: Int, val displayName: String, val tooltipText: String)

  companion object {
    /**
     * Fill in the gaps between events
     */
    fun Iterable<TraceProcessor.AndroidFrameEventsResult.FrameEvent>.padded(): List<SeriesData<AndroidFrameEvent>> =
      padded({ TimeUnit.NANOSECONDS.toMicros(it.timestampNanoseconds) },
             { TimeUnit.NANOSECONDS.toMicros(it.timestampNanoseconds + it.durationNanoseconds) },
             ::Data, { _, _ -> Padding })

    /**
     * Mapping from phase name to metadata, e.g. sort order.
     */
    private val trackMetadataMap = mapOf(
      "App" to TrackMetadata(0, "Application", "Application is processing the frame buffer."),
      "GPU" to TrackMetadata(1, "Wait for GPU", "Waiting for GPU to process the frame buffer."),
      "Composition" to TrackMetadata(2, "Composition", "Surfaceflinger is compositing the surface frame."),
      "Display" to TrackMetadata(3, "Frames on display", "When each frame on display starts and ends."),
    )

    /**
     * Comparator for sorting Android frame phases.
     */
    @JvmStatic
    val trackComparator = compareBy<TraceProcessor.AndroidFrameEventsResult.Phase> { trackMetadataMap[it.phaseName]?.sortOrder }

    /**
     * Track group help text.
     */
    @JvmStatic
    val titleHelpText = "This section shows the lifecycle of frames." +
                        trackMetadataMap.values.sortedBy { it.sortOrder }.joinToString(separator = "") {
                          "<p><b>${it.displayName}</b>:${it.tooltipText}</p>"
                        }

    /**
     * @return the display name of a given phase.
     */
    @JvmStatic
    fun getDisplayName(phaseName: String): String = trackMetadataMap[phaseName]?.displayName ?: ""
  }
}

/**
 * Wrapper class for use with [StateChartModel].
 *
 * Use [Data] for events that contain [TraceProcessor.AndroidFrameEventsResult.FrameEvent] and [Padding] for padding events.
 */
sealed class AndroidFrameEvent {
  data class Data(val frameNumber: Int, val timestampUs: Long, val durationUs: Long) : AndroidFrameEvent() {
    constructor(frameEvent: TraceProcessor.AndroidFrameEventsResult.FrameEvent) : this(
      frameEvent.frameNumber,
      TimeUnit.NANOSECONDS.toMicros(frameEvent.timestampNanoseconds),
      TimeUnit.NANOSECONDS.toMicros(frameEvent.durationNanoseconds))
  }

  object Padding : AndroidFrameEvent()
}
