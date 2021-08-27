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
import com.android.tools.analytics.crash.GoogleCrashReporter
import com.android.tools.idea.diagnostics.crash.StudioExceptionReport
import com.google.common.base.Charsets
import com.google.gson.stream.JsonWriter
import com.intellij.diagnostic.ThreadDumper
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class HistogramReport
@JvmOverloads
constructor(val threadDumpPath: Path?,
            val histogramPath: Path?,
            val reason: MemoryReportReason?,
            val description: String?,
            baseProperties: DiagnosticReportProperties = DiagnosticReportProperties())
  : DiagnosticReport(REPORT_TYPE, baseProperties) {

  @Throws(IOException::class)
  override fun asCrashReport(): CrashReport {
    val threadDump = threadDumpPath?.let { String(Files.readAllBytes(it), Charsets.UTF_8) }
    val histogram = histogramPath?.let { String(Files.readAllBytes(it), Charsets.UTF_8) }

    return object : DiagnosticCrashReport(type, properties) {
      private val EXCEPTION_TYPE = "com.android.OutOfMemory"

      private val EMPTY_OOM_STACKTRACE = EXCEPTION_TYPE + ": \n" +
                                         "\tat " + HistogramReport::class.java.name + ".missingEdtStack(Unknown source)"

      override fun serializeTo(builder: MultipartEntityBuilder) {
        super.serializeTo(builder)

        val edtStack = threadDump?.let { ThreadDumper.getEdtStackForCrash(it, EXCEPTION_TYPE) }
                       ?: EMPTY_OOM_STACKTRACE

        GoogleCrashReporter.addBodyToBuilder(builder, StudioExceptionReport.KEY_EXCEPTION_INFO, edtStack)
        reason?.let {
          GoogleCrashReporter.addBodyToBuilder(builder, "reason", it.name)
        }
        histogram?.let {
          GoogleCrashReporter.addBodyToBuilder(builder, "histogram", it, ContentType.create("text/plain", Charsets.UTF_8))
        }
        threadDump?.let {
          GoogleCrashReporter.addBodyToBuilder(builder, "threadDump", it, ContentType.create("text/plain", Charsets.UTF_8))
        }
      }

    }
  }

  override fun serializeReportProperties(writer: JsonWriter) {
    if (threadDumpPath != null) writer.name("threadDumpPath").value(threadDumpPath.toString())
    if (histogramPath != null) writer.name("histogramPath").value(histogramPath.toString())
    if (reason != null) writer.name("reason").value(reason.name)
    if (description != null) writer.name("description").value(description)
  }

  companion object {
    const val REPORT_TYPE = "Histogram"

    fun deserialize(baseProperties: DiagnosticReportProperties,
                    properties: Map<String, String>,
                    format: Long): HistogramReport {
      if (format >= 1L) {
        return HistogramReport(
          properties["threadDumpPath"]?.let {
            fixDirectoryPathAndCheckIfReadable(
              Paths.get(it))
          },
          properties["histogramPath"]?.let {
            fixDirectoryPathAndCheckIfReadable(
              Paths.get(it))
          },
          properties["reason"]?.let { MemoryReportReason.valueOf(it) },
          properties["description"],
          baseProperties)
      }
      throw IllegalArgumentException("Unrecognized format version: $format")
    }
  }
}