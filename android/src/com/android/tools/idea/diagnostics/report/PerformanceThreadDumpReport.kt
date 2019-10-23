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
import com.google.common.base.Charsets
import com.google.common.base.Joiner
import com.google.gson.stream.JsonWriter
import com.intellij.diagnostic.ThreadDumper
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class PerformanceThreadDumpReport
@JvmOverloads
constructor(val threadDumpPath: Path?,
            val description: String?,
            baseProperties: DiagnosticReportProperties = DiagnosticReportProperties())
  : DiagnosticReport("PerformanceThreadDump", baseProperties) {

  @Throws(IOException::class)
  override fun asCrashReport(): CrashReport {
    val threadDump = threadDumpPath?.let { Joiner.on('\n').join(Files.readAllLines(it)) } ?: ""
    val fileName = threadDumpPath?.fileName?.toString() ?: "noFilename.txt"

    return PerformanceThreadDumpCrashReport(properties,
                                            fileName,
                                            threadDump)
  }

  override fun serializeReportProperties(writer: JsonWriter) {
    if (threadDumpPath != null) writer.name("threadDumpPath").value(threadDumpPath.toString())
    if (description != null) writer.name("description").value(description)
  }

  companion object {
    fun deserialize(baseProperties: DiagnosticReportProperties,
                    properties: Map<String, String>,
                    format: Long): PerformanceThreadDumpReport {
      if (format >= 1L) {
        return PerformanceThreadDumpReport(
          properties["threadDumpPath"]?.let {
            fixDirectoryPathAndCheckIfReadable(Paths.get(it))
          },
          properties["description"],
          baseProperties)
      }
      throw IllegalArgumentException("Unrecognized format version: $format")
    }
  }
}

class PerformanceThreadDumpCrashReport(properties: DiagnosticReportProperties,
                                       private val fileName: String,
                                       private val threadDump: String) : DiagnosticCrashReport("Performance", properties) {
  private val EXCEPTION_TYPE = "com.android.ApplicationNotResponding"

  private val EMPTY_ANR_STACKTRACE = EXCEPTION_TYPE + ": \n" +
                                     "\tat " + PerformanceThreadDumpReport::class.java.name + ".missingEdtStack(Unknown source)"

  override fun serializeTo(builder: MultipartEntityBuilder) {
    super.serializeTo(builder)
    val edtStack = ThreadDumper.getEdtStackForCrash(threadDump, EXCEPTION_TYPE)

    builder.addTextBody(StudioExceptionReport.KEY_EXCEPTION_INFO,
                        edtStack ?: EMPTY_ANR_STACKTRACE)
    builder.addTextBody(fileName, threadDump,
                        ContentType.create("text/plain", Charsets.UTF_8))
  }
}