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
import com.android.tools.idea.diagnostics.jfr.reports.typesToFields
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.IOException
import java.io.Reader
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path

abstract class DiagnosticReport
@JvmOverloads
constructor(val type: String,
            val properties: DiagnosticReportProperties = DiagnosticReportProperties()
) {

  abstract fun serializeReportProperties(writer: JsonWriter)

  @Throws(IOException::class)
  abstract fun asCrashReport(): CrashReport

  fun serializeReport(outputWriter: Writer) {
    JsonWriter(outputWriter).use {
      it.setIndent("  ")
      it.beginObject()
      it.name("formatVersion").value(2)
      it.name("type").value(type)
      properties.studioVersion?.let { v -> it.name("studioVersion").value(v) }
      properties.kotlinVersion?.let { v -> it.name("kotlinVersion").value(v) }
      it.name("uptime").value(properties.uptime)
      it.name("reportTime").value(properties.reportTime)
      it.name("sessionId").value(properties.sessionId)
      serializeReportProperties(it)
      it.endObject()
    }
  }

  companion object {
    private const val MAX_SUPPORTED_FORMAT = 2L

    private fun readDiagnosticReport(reader: JsonReader): DiagnosticReport? {
      var type: String? = null
      var format: Long = 0
      var studioVersion: String? = null
      var kotlinVersion: String? = null
      var uptime: Long = 0
      var reportTime: Long = 0
      var sessionId: String? = null
      val properties = HashMap<String, String>()
      while (reader.hasNext()) {
        val name = reader.nextName()
        when (name) {
          "type" -> type = reader.nextString()
          "formatVersion" -> format = reader.nextLong()
          "uptime" -> uptime = reader.nextLong()
          "reportTime" -> reportTime = reader.nextLong()
          "studioVersion" -> studioVersion = reader.nextString()
          "kotlinVersion" -> kotlinVersion = reader.nextString()
          "sessionId" -> sessionId = reader.nextString()
          else -> properties[name] = reader.nextString()
        }
      }
      if (format > MAX_SUPPORTED_FORMAT) {
        return null
      }
      val baseReportProperties = when (format) {
        // Version 1 does not support serialized DiagnosticReportProperties()
        1L -> DiagnosticReportProperties()
        else -> DiagnosticReportProperties(
          uptime = uptime,
          reportTime = reportTime,
          sessionId = sessionId,
          studioVersion = studioVersion,
          kotlinVersion = kotlinVersion
        )
      }

      return try {
        when (type) {
          "Freeze" -> FreezeReport.deserialize(baseReportProperties, properties, format)
          "Histogram" -> HistogramReport.deserialize(baseReportProperties, properties,
                                                     format)
          "PerformanceThreadDump" -> PerformanceThreadDumpReport.deserialize(
            baseReportProperties, properties, format)
          "UnanalyzedHeap" -> UnanalyzedHeapReport.deserialize(baseReportProperties, properties, format)
          else -> if (type in typesToFields.keys)
            JfrBasedReport.deserialize(type!!, baseReportProperties, typesToFields[type]!!, properties, format)
          else null
        }
      }
      catch (ignored: Exception) {
        null
      }
    }

    @Throws(IOException::class)
    fun readDiagnosticReports(inputReader: Reader): ArrayList<DiagnosticReport> {
      val result = ArrayList<DiagnosticReport>()
      JsonReader(inputReader).use { reader ->
        // setLenient = true, as json objects are adjacent to each other
        reader.isLenient = true
        while (reader.hasNext() && reader.peek() != JsonToken.END_DOCUMENT) {
          reader.beginObject()
          readDiagnosticReport(reader)?.let { report ->
            result.add(report)
          }
          reader.endObject()
        }
      }
      return result
    }

    /**
     * Performance reports are moved to a different directory once UI is responsive again (path contains duration
     * of the freeze). If the file pointed by `path` doesn't exist, it checks if it exists under such directory.
     * @returns Path where such report exists, `null` otherwise
     */
    fun fixDirectoryPathAndCheckIfReadable(path: Path?): Path? {
      if (path == null) return null
      if (Files.isReadable(path)) {
        return path
      }

      val directory = path.parent
      try {
        val prefix = "${directory.fileName}-"
        Files.newDirectoryStream(directory.parent) { it.fileName.toString().startsWith(prefix) }.use { paths ->
          val iterator = paths.iterator()
          if (!iterator.hasNext()) {
            return null
          }
          val newDirectory = iterator.next()
          val newFile = newDirectory.resolve(path.fileName)
          return if (Files.isReadable(newFile)) newFile else null
        }
      }
      catch (e: IOException) {
        return null
      }
    }
  }
}
