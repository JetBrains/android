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
package com.android.tools.idea.diagnostics.jfr.reports

import com.android.tools.idea.diagnostics.crash.StudioExceptionReport
import com.android.tools.idea.diagnostics.jfr.CallTreeAggregator
import com.android.tools.idea.diagnostics.jfr.EventFilter
import com.android.tools.idea.diagnostics.jfr.JfrReportGenerator
import com.android.tools.idea.diagnostics.jfr.JfrReportManager
import com.android.tools.idea.diagnostics.report.FreezeReport
import com.intellij.diagnostic.IdePerformanceListener
import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import jdk.jfr.consumer.RecordedEvent
import java.io.File

class JfrFreezeReports {
  companion object {
    const val REPORT_TYPE = "JFR-Freeze"
    private const val CALL_TREES_FIELD = "callTrees"
    val FIELDS = listOf(CALL_TREES_FIELD, StudioExceptionReport.KEY_EXCEPTION_INFO)

    private const val EXCEPTION_TYPE = "com.android.ApplicationNotResponding"
    private val EMPTY_ANR_STACKTRACE = EXCEPTION_TYPE + ": \n" +
                                       "\tat " + FreezeReport::class.java.name + ".missingEdtStack(Unknown source)"

    val freezeReportManager = JfrReportManager.create(::JfrFreezeReportGenerator, null) {
      val application = ApplicationManager.getApplication()
      application.messageBus.connect(application).subscribe(IdePerformanceListener.TOPIC, object : IdePerformanceListener {
        override fun uiFreezeStarted() {
          startCapture()
          (currentReportGenerator as JfrFreezeReportGenerator).edtStackForCrash =
            ThreadDumper.getEdtStackForCrash(ThreadDumper.dumpThreadsToString(), EXCEPTION_TYPE) ?: EMPTY_ANR_STACKTRACE
        }

        override fun uiFreezeFinished(durationMs: Long, reportDir: File?) {
          stopCapture()
        }
      })
    }
  }

  class JfrFreezeReportGenerator : JfrReportGenerator(REPORT_TYPE, EventFilter.CPU_SAMPLES,
                                                      -Registry.intValue("performance.watcher.unresponsive.interval.ms", 0)) {
    private val callTreeAggregator = CallTreeAggregator(CallTreeAggregator.THREAD_FILTER_ALL)
    var edtStackForCrash: String = EMPTY_ANR_STACKTRACE

    override fun accept(e: RecordedEvent, c: Capture) {
      callTreeAggregator.accept(e)
    }

    override fun captureCompleted(c: Capture) {
      callTreeAggregator.processBatch(c.end!!)
    }

    override fun generateReport(): Map<String, String> {
      return mapOf(CALL_TREES_FIELD to callTreeAggregator.generateReport(200_000),
                   StudioExceptionReport.KEY_EXCEPTION_INFO to edtStackForCrash)
    }
  }
}