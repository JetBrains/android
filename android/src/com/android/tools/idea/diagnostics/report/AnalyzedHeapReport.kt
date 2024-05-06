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
package com.android.tools.idea.diagnostics.report

import com.android.tools.analytics.crash.CrashReport
import com.android.tools.analytics.crash.GoogleCrashReporter
import com.android.tools.idea.diagnostics.crash.StudioExceptionReport
import com.google.gson.stream.JsonWriter
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder

class AnalyzedHeapReport(val text: String,
                         val summary: String,
                         heapProperties: HeapReportProperties,
                         baseProperties: DiagnosticReportProperties = DiagnosticReportProperties())
  : HeapReport("Heap", heapProperties, baseProperties) {

  override fun serializeReportProperties(writer: JsonWriter) {
    super.serializeReportProperties(writer)
    writer.name("reportText").value(text)
    writer.name("heapSummary").value(summary)
  }

  override fun asCrashReport(): CrashReport {
    return object : HeapCrashReport(type, heapProperties, properties) {
      override fun serialize(builder: MultipartEntityBuilder) {
        super.serialize(builder)
        GoogleCrashReporter.addBodyToBuilder(builder, StudioExceptionReport.KEY_EXCEPTION_INFO, EMPTY_OOM_STACKTRACE)
        GoogleCrashReporter.addBodyToBuilder(builder, "heapSummary", summary)
        builder.addBinaryBody("heapReport", text.toByteArray(), ContentType.TEXT_PLAIN, "heapReport.txt")
      }
    }
  }

  companion object {
    private const val EXCEPTION_TYPE = "com.android.OutOfMemory"

    private val EMPTY_OOM_STACKTRACE = EXCEPTION_TYPE + ": \n" +
                                       "\tat " + HeapReport::class.java.name + ".missingEdtStack(Unknown source)"
  }
}