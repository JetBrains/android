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

import com.intellij.concurrency.JobScheduler
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Manages the setup process and provides hooks to trigger captures for a single type of JFR report.
 * For each report, the manager will invoke the generatorSupplier to get a new instance of JfrReportGenerator.
 * setupTask should be used to set up whatever hooks are necessary to determine when to trigger a capture (e.g.
 * subscribing to a message bus to get notified when a freeze starts), at which point startCapture() and
 * stopCapture() should be called.
 *
 * JfrReportManagers should be initialized in [RecordingManager.createReportManagers]
 */
sealed class JfrReportManager<T : JfrReportGenerator>(val generatorSupplier: () -> T) {
  var currentReportGenerator: T? = null
  abstract fun startCapture()
  abstract fun stopCapture()

  companion object {
    fun <T : JfrReportGenerator> create(generatorSupplier: () -> T,
                                        aggregation: Duration? = null, // null means no aggregation
                                        setupTask: JfrReportManager<T>.() -> Unit): JfrReportManager<T> {
      val manager = if (aggregation == null) {
        ReportPerCaptureJfrReportManager(generatorSupplier)
      }
      else {
        AggregatingJfrReportManager(aggregation, generatorSupplier)
      }
      manager.setupTask();
      return manager
    }
  }
}

private class ReportPerCaptureJfrReportManager<T : JfrReportGenerator>(generatorSupplier: () -> T): JfrReportManager<T>(generatorSupplier) {
  override fun startCapture() {
    if (currentReportGenerator != null) {
      throw IllegalStateException("Overlapping JFR capture intervals not permitted.")
    }
    println("Starting capture")
    currentReportGenerator = generatorSupplier()
    currentReportGenerator?.startCapture()
  }

  override fun stopCapture() {
    println("Stopping capture")
    currentReportGenerator?.stopCapture()
    currentReportGenerator?.finish()
    currentReportGenerator = null
  }
}

private class AggregatingJfrReportManager<T : JfrReportGenerator>(aggregationPeriod: Duration,
                                                                  generatorSupplier: () -> T): JfrReportManager<T>(generatorSupplier) {
  init {
    currentReportGenerator = generatorSupplier()
    JobScheduler.getScheduler().scheduleWithFixedDelay({
      currentReportGenerator?.finish()
      currentReportGenerator = generatorSupplier()
    }, aggregationPeriod.seconds, aggregationPeriod.seconds, TimeUnit.SECONDS);
  }
  override fun startCapture() {
    currentReportGenerator?.startCapture()
  }

  override fun stopCapture() {
    currentReportGenerator?.stopCapture()
  }
}
