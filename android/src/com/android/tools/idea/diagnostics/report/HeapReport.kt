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
package com.android.tools.idea.diagnostics.report

import com.android.tools.analytics.crash.CrashReport
import com.android.tools.idea.diagnostics.crash.StudioExceptionReport
import com.google.gson.stream.JsonWriter
import org.apache.http.entity.mime.MultipartEntityBuilder

class HeapReport
@JvmOverloads
constructor(val report: String,
            baseProperties: DiagnosticReportProperties = DiagnosticReportProperties())
  : DiagnosticReport("Heap", baseProperties) {
  private val EXCEPTION_TYPE = "com.android.OutOfMemory"

  private val EMPTY_OOM_STACKTRACE = EXCEPTION_TYPE + ": \n" +
                                     "\tat " + HeapReport::class.java.name + ".missingEdtStack(Unknown source)"

  override fun serializeReportProperties(writer: JsonWriter) {
    writer.name("report").value(report)
  }

  override fun asCrashReport(): CrashReport {
    return object : DiagnosticCrashReport(type, properties) {
      override fun serialize(builder: MultipartEntityBuilder) {
        super.serialize(builder)
        builder.addTextBody(StudioExceptionReport.KEY_EXCEPTION_INFO, EMPTY_OOM_STACKTRACE)
        builder.addTextBody("heapReport", report)
      }
    }
  }
}
