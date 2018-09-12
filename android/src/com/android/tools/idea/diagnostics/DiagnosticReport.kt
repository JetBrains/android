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
package com.android.tools.idea.diagnostics

import com.android.tools.analytics.crash.CrashReport
import com.android.tools.idea.diagnostics.crash.BaseStudioReport
import com.android.tools.idea.diagnostics.crash.StudioExceptionReport
import com.android.tools.idea.diagnostics.crash.StudioPerformanceWatcherReport
import com.google.common.base.Charsets
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.intellij.diagnostic.ThreadDumper
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import java.io.IOException
import java.io.Reader
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.TreeMap
import java.util.stream.Collectors

private class FreezeReport(val threadDumpPath: Path?,
                           val reportParts : Map<String, Path>,
                           val timedOut: Boolean,
                           val totalDuration: Long?,
                           val description: String?) : DiagnosticReport("Freeze") {

  override fun serializeReportProperties(writer: JsonWriter) {
    if (threadDumpPath != null) writer.name("threadDumpPath").value(threadDumpPath.toString())
    reportParts.forEach {
      writer.name(it.key + "Path").value(it.value.toString())
    }
    writer.name("timedOut").value(timedOut.toString())
    if (totalDuration != null) writer.name("totalDuration").value(totalDuration)
    if (description != null) writer.name("description").value(description)
  }

  @Throws(IOException::class)
  fun asCrashReport(): CrashReport {
    val edtStack = ThreadDumper.getEdtStackForCrash(String(Files.readAllBytes(threadDumpPath)),
                                                    "com.android.ApplicationNotResponding") ?: EMPTY_ANR_STACKTRACE
    val contents = TreeMap<String, String>()
    reportParts.forEach { name, path -> contents[name] = String(Files.readAllBytes(path)) }

    return object : BaseStudioReport(null, null, "Freeze") {
      override fun serialize(builder: MultipartEntityBuilder) {
        super.serialize(builder)
        builder.addTextBody("totalDuration", totalDuration.toString())
        builder.addTextBody("timedOut", timedOut.toString())
        builder.addTextBody(StudioExceptionReport.KEY_EXCEPTION_INFO, edtStack)
        contents.forEach { name, contents ->
          builder.addTextBody(name, contents, ContentType.create("text/plain", Charsets.UTF_8))
        }
      }
    }
  }

  companion object {
    private const val EXCEPTION_TYPE = "com.android.ApplicationNotResponding"
    private val EMPTY_ANR_STACKTRACE = EXCEPTION_TYPE + ": \n" +
                                       "\tat " + FreezeReport::class.java.name + ".missingEdtStack(Unknown source)"

    fun deserialize(properties: Map<String, String>, format: Long): FreezeReport {
      if (format == 1L) {
        val dynamicProperties = TreeMap<String, String>(properties)
        val totalDuration = dynamicProperties.remove("totalDuration")?.toLong()
        val description = dynamicProperties.remove("description")
        val threadDumpPath = dynamicProperties.remove("threadDumpPath")?.let { fixDirectoryPathAndCheckIfReadable(Paths.get(it))}
        val timedOut = dynamicProperties.remove("timedOut")?.toBoolean() ?: false

        val paths = TreeMap<String, Path>()
        dynamicProperties.forEach { name, pathName ->
          if (name.endsWith("Path")) {
            fixDirectoryPathAndCheckIfReadable(Paths.get(pathName))?.let {
              paths[name.dropLast("Path".length)] = it
            }
          }
        }
        return FreezeReport(
          threadDumpPath,
          paths,
          timedOut,
          totalDuration,
          description
        )
      }
      throw IllegalArgumentException("Unrecognized format version: $format")
    }
  }
}

private class HistogramReport(val threadDumpPath: Path?,
                              val histogramPath: Path?,
                              val description: String?) : DiagnosticReport("Histogram") {

  override fun serializeReportProperties(writer: JsonWriter) {
    if (threadDumpPath != null) writer.name("threadDumpPath").value(threadDumpPath.toString())
    if (histogramPath != null) writer.name("histogramPath").value(histogramPath.toString())
    if (description != null) writer.name("description").value(description)
  }

  companion object {
    fun deserialize(properties: Map<String, String>, format: Long): HistogramReport {
      if (format == 1L) {
        return HistogramReport(
          properties["threadDumpPath"]?.let { fixDirectoryPathAndCheckIfReadable(Paths.get(it)) },
          properties["histogramPath"]?.let { fixDirectoryPathAndCheckIfReadable(Paths.get(it)) },
          properties["description"])
      }
      throw IllegalArgumentException("Unrecognized format version: $format")
    }
  }
}

private class PerformanceThreadDumpReport(val threadDumpPath: Path?,
                                          val description: String?) : DiagnosticReport("PerformanceThreadDump") {

  override fun serializeReportProperties(writer: JsonWriter) {
    if (threadDumpPath != null) writer.name("threadDumpPath").value(threadDumpPath.toString())
    if (description != null) writer.name("description").value(description)
  }

  companion object {
    fun deserialize(properties: Map<String, String>, format: Long): PerformanceThreadDumpReport {
      if (format == 1L) {
        return PerformanceThreadDumpReport(
          properties["threadDumpPath"]?.let { fixDirectoryPathAndCheckIfReadable(Paths.get(it)) },
          properties["description"])
      }
      throw IllegalArgumentException("Unrecognized format version: $format")
    }
  }
}

abstract class DiagnosticReport(val type: String) {

  abstract fun serializeReportProperties(writer: JsonWriter)

  fun serializeReport(outputWriter: Writer) {
    JsonWriter(outputWriter).use {
      it.setIndent("  ")
      it.beginObject()
      it.name("formatVersion").value(1)
      it.name("type").value(type)
      serializeReportProperties(it)
      it.endObject()
    }
  }

  companion object {
    private const val MAX_SUPPORTED_FORMAT = 1L

    private fun readDiagnosticReport(reader: JsonReader): DiagnosticReport? {
      var type: String? = null
      var format: Long = 0
      val properties = HashMap<String, String>()
      while (reader.hasNext()) {
        val name = reader.nextName()
        when (name) {
          "type" -> type = reader.nextString()
          "formatVersion" -> format = reader.nextLong()
          else -> properties[name] = reader.nextString()
        }
      }
      if (format > MAX_SUPPORTED_FORMAT) {
        return null
      }
      return try {
        when (type) {
          "Freeze" -> FreezeReport.deserialize(properties, format)
          "Histogram" -> HistogramReport.deserialize(properties, format)
          "PerformanceThreadDump" -> PerformanceThreadDumpReport.deserialize(properties, format)
          else -> null
        }
      } catch (ignored : Exception) {
        null
      }
    }

    fun readDiagnosticReports(inputReader: Reader): ArrayList<DiagnosticReport> {
      val result = ArrayList<DiagnosticReport>()
      JsonReader(inputReader).use {
        // setLenient = true, as json objects are adjacent to each other
        it.isLenient = true
        while (it.hasNext() && it.peek() != JsonToken.END_DOCUMENT) {
          it.beginObject()
          val report = readDiagnosticReport(it)
          if (report != null) {
            result.add(report)
          }
          it.endObject()
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
        val prefix = directory.fileName.toString() + "-"
        Files.newDirectoryStream(directory.parent, { it.fileName.toString().startsWith(prefix) }).use { paths ->
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
