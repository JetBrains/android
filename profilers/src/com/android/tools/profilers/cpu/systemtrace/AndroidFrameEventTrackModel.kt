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
import java.util.concurrent.TimeUnit

/**
 * Track model for a frame lifecycle track representing Android frames in a specific phase.
 */
class AndroidFrameEventTrackModel(androidFrameEvents: List<TraceProcessor.AndroidFrameEventsResult.FrameEvent>,
                                  viewRange: Range) : StateChartModel<AndroidFrameEvent>() {
  init {
    // Organize frame events by depth so that they show up as different lanes in the state chart.
    val eventsByDepth = androidFrameEvents.groupBy { it.depth }.toSortedMap().values
    for (events in eventsByDepth) {
      val eventSeries = mutableListOf<SeriesData<AndroidFrameEvent>>()
      var lastEndTimeNs = 0L
      for (event in events) {
        // Add an fake event as padding between real events, needed for StateChart rendering.
        if (event.timestampNanoseconds > lastEndTimeNs) {
          eventSeries.add(SeriesData(TimeUnit.NANOSECONDS.toMicros(lastEndTimeNs), Padding))
        }
        lastEndTimeNs = event.timestampNanoseconds + event.durationNanoseconds
        // Add the real event.
        eventSeries.add(SeriesData(TimeUnit.NANOSECONDS.toMicros(event.timestampNanoseconds), Data(event)))
      }
      if (eventSeries.isNotEmpty()) {
        addSeries(RangedSeries(viewRange, LazyDataSeries { eventSeries }))
      }
    }
  }

  /**
   * Wrapper class for organizing track sort order, display name, etc.
   */
  private data class TrackMetadata(val sortOrder: Int, val displayName: String, val tooltipText: String)

  companion object {
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
    constructor(frameEvent: TraceProcessor.AndroidFrameEventsResult.FrameEvent) : this(frameEvent.frameNumber,
                                                                                       frameEvent.timestampNanoseconds,
                                                                                       frameEvent.durationNanoseconds)
  }

  object Padding : AndroidFrameEvent()
}
