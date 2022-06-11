/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.jfr

import jdk.jfr.consumer.RecordedEvent
import java.time.Instant

/** A [JfrReportGenerator] handles processing the JFR events and creating the report text for a single
 * crash report. A [Capture] represents a time interval of interest, for which this generator will
 * receive events to process. In the case of an [AggregatingJfrReportManager], multiple Captures may
 * occur during the lifetime of the generator (though they must not overlap).
 *
 * Start and end offsets can be used to adjust the capture interval relative to the actual times of
 * the calls to startCapture and stopCapture. This can be useful to, for example, adjust the start
 * time of a freeze capture, given that it takes some time to detect that a freeze is occurring.
 * Negative start offsets must be shorter than the length of one recording chunk
 * ([RecordingManager.JFR_RECORDING_DURATION_SECONDS]).
 */
abstract class JfrReportGenerator(val reportType: String, val eventFilter: EventFilter, private val startOffsetMs: Int = 0, private val endOffsetMs: Int = 0) {
  private var currentCapture: Capture? = null
  var isFinished = false
    private set

  init {
    if (startOffsetMs < -RecordingManager.JFR_RECORDING_DURATION_SECONDS * 1000) {
      throw IllegalArgumentException("Start offset cannot be less than -${RecordingManager.JFR_RECORDING_DURATION_SECONDS} seconds");
    }
  }

  inner class Capture {
    val start = Instant.now().plusMillis(startOffsetMs.toLong())
    var end: Instant? = null
    val generator = this@JfrReportGenerator

    fun containsInstant(instant: Instant): Boolean {
      if (instant.isBefore(start)) return false
      if (end == null) return true
      return instant.isBefore(end)
    }
  }

  // Called for each JFR event that eventFilter accepts and is within the Capture's interval.
  abstract fun accept(e: RecordedEvent, c: Capture)

  /* Called after all of the events for this capture have been accepted by this generator. Perform
   * processing/aggregation work here.
   */
  abstract fun captureCompleted(c: Capture)

  /* Generate the contents of a crash report based on all of the relevant events accepted during this
   * generator's Captures. The report will contain a field for each key in the map, with the corresponding
   * value as its contents. If no report should be submitted (e.g., for a report aggregating profile
   * snippets for some event that did not occur during the aggregation period), return an empty map.
   */
  abstract fun generateReport(): Map<String, String>

  fun finish() {
    isFinished = true;
  }

  fun startCapture() {
    if (currentCapture != null) throw IllegalStateException("Cannot start capture: capture already in progress")
    currentCapture = Capture()
    RecordingManager.startCapture(currentCapture);
  }

  fun stopCapture() {
    if (currentCapture == null) throw IllegalStateException("Cannot stop capture: there is no active capture")
    currentCapture?.end = Instant.now().plusMillis(endOffsetMs.toLong())
    currentCapture = null
  }

}