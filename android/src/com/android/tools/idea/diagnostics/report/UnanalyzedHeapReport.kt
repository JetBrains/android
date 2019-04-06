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
import com.google.gson.stream.JsonWriter
import java.nio.file.Path
import java.nio.file.Paths

class UnanalyzedHeapReport
@JvmOverloads
constructor(
  val hprofPath: Path,
  heapProperties: HeapReportProperties,
  baseProperties: DiagnosticReportProperties = DiagnosticReportProperties())
  : HeapReport("UnanalyzedHeap", heapProperties, baseProperties) {

  override fun serializeReportProperties(writer: JsonWriter) {
    super.serializeReportProperties(writer)
    writer.name("hprofPath").value(hprofPath.toString())
  }

  override fun asCrashReport(): CrashReport {
    // Never send unanalyzed heap reports
    throw UnsupportedOperationException()
  }

  companion object {
    fun deserialize(baseProperties: DiagnosticReportProperties,
                    properties: Map<String, String>,
                    format: Long): UnanalyzedHeapReport {
      if (format >= 2L) {
        val hprofPath = Paths.get(properties["hprofPath"] ?: throw IllegalArgumentException("Missing hprofPath entry"))
        val heapReportProperties =
          HeapReportProperties(
            properties["reason"]?.let { MemoryReportReason.valueOf(it) } ?: MemoryReportReason.None,
            properties["liveStats"] ?: ""
          )
        return UnanalyzedHeapReport(hprofPath, heapReportProperties, baseProperties)
      }
      throw IllegalArgumentException("Unrecognized format version: $format")
    }
  }
}
