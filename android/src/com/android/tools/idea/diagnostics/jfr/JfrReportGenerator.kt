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

import com.android.tools.idea.diagnostics.jfr.JfrReportGenerator.Capture
import com.android.tools.idea.diagnostics.report.DiagnosticReportProperties
import com.android.tools.idea.diagnostics.report.JfrBasedReport
import com.intellij.openapi.diagnostic.thisLogger
import jdk.jfr.consumer.RecordedEvent
import java.time.Clock
import java.time.Instant

/**
 * A [JfrReportGenerator] handles processing the JFR events and creating the report text for a single
 * crash report. A [Capture] represents a time interval of interest, for which this generator will
 * receive events to process. In the case of an [AggregatingJfrReportManager], multiple Captures may
 * occur during the lifetime of the generator (though they must not overlap).
 *
 * Start and end offsets can be used to adjust the capture interval relative to the actual times of
 * the calls to startCapture and stopCapture. This can be useful to, for example, adjust the start
 * time of a freeze capture, given that it takes some time to detect that a freeze is occurring.
 * Negative start offsets must be shorter than the length of one recording chunk ([JFR_RECORDING_DURATION]).
 */
abstract class JfrReportGenerator(
  val reportType: String,
  val eventFilter: EventFilter,
  private val startOffsetMs: Long = 0,
  private val endOffsetMs: Long = 0,
  private val clock: Clock = Clock.systemUTC(),
  ) {
  private var currentCapture: Capture? = null
  var isFinished = false
    private set

  init {
    require(startOffsetMs >= -JFR_RECORDING_DURATION.inWholeMilliseconds) {
      "Start offset cannot be less than -${JFR_RECORDING_DURATION.inWholeSeconds} seconds"
    }
  }

  inner class Capture {
    val start: Instant = clock.instant().plusMillis(startOffsetMs)
    var end: Instant? = null

    fun maybeAccept(e: RecordedEvent) {
      if (containsInstant(e.startTime) && eventFilter.accepts(e)) accept(e, this)
    }

    private fun containsInstant(instant: Instant) =
      !instant.isBefore(start) && (end == null || instant.isBefore(end))

    fun completeAndGenerateReport(endThreshold: Instant, reportCallback: ReportCallback) : Boolean {
      if (end?.isBefore(endThreshold) != true) return false
      captureCompleted(this)
      if (isFinished)  {
        try {
          val report = generateReport()
          if (report.isNotEmpty()) reportCallback(JfrBasedReport(reportType, report, DiagnosticReportProperties()))
        }
        catch (e: Exception) {
          thisLogger().warn(e)
        }
      }
      return true
    }
  }

  // Called for each JFR event that eventFilter accepts and is within the Capture's interval.
  abstract fun accept(e: RecordedEvent, c: Capture)

  /**
   * Indicates all of the events for this capture have been accepted by this generator. Perform
   * processing/aggregation work here.
   */
  abstract fun captureCompleted(c: Capture)

  /**
   * Generates the contents of a crash report based on all the relevant events accepted during this
   * generator's Captures. The report will contain a field for each key in the map, with the corresponding
   * value as its contents. If no report should be submitted (e.g., for a report aggregating profile
   * snippets for some event that did not occur during the aggregation period), return an empty map.
   */
  abstract fun generateReport(): Map<String, String>

  fun finish() {
    isFinished = true;
  }

  fun startCapture() {
    check(currentCapture == null) { "Cannot start capture: capture already in progress" }
    currentCapture = Capture().also {
      RecordingManager.getInstance().startCapture(it);
    }
  }

  fun stopCapture() {
    checkNotNull(currentCapture) { "Cannot stop capture: there is no active capture" }
    currentCapture?.end = clock.instant().plusMillis(endOffsetMs)
    currentCapture = null
  }
}