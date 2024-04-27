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

abstract class HeapReport
@JvmOverloads
constructor(type: String,
            val heapProperties: HeapReportProperties,
            baseProperties: DiagnosticReportProperties = DiagnosticReportProperties())
  : DiagnosticReport(type, baseProperties) {

  override fun serializeReportProperties(writer: JsonWriter) {
    writer.name("reason").value(heapProperties.reason.toString())
    writer.name("liveStats").value(heapProperties.liveStats)
  }
}
