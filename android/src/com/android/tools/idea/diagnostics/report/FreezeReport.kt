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
import com.google.gson.stream.JsonWriter
import com.intellij.diagnostic.ThreadDumper
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.TreeMap

class FreezeReport
@JvmOverloads
constructor(val threadDumpPath: Path?,
            val reportParts: Map<String, Path>,
            val timedOut: Boolean,
            val totalDuration: Long?,
            val description: String?,
            baseProperties: DiagnosticReportProperties = DiagnosticReportProperties())
  : DiagnosticReport("Freeze", baseProperties) {

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
  override fun asCrashReport(): CrashReport {
    val edtStack = ThreadDumper.getEdtStackForCrash(String(Files.readAllBytes(threadDumpPath)),
                                                    "com.android.ApplicationNotResponding") ?: EMPTY_ANR_STACKTRACE
    val contents = TreeMap<String, String>()
    reportParts.forEach { name, path -> contents[name] = String(Files.readAllBytes(path)) }

    return object : DiagnosticCrashReport(type, properties) {
      override fun serialize(builder: MultipartEntityBuilder) {
        super.serialize(builder)
        builder.addTextBody("totalDuration", totalDuration.toString())
        builder.addTextBody("timedOut", timedOut.toString())
        builder.addTextBody(StudioExceptionReport.KEY_EXCEPTION_INFO, edtStack)
        contents.forEach { name, contents ->
          builder.addTextBody(name, contents,
                              ContentType.create("text/plain", Charsets.UTF_8))
        }
      }
    }
  }

  companion object {
    private const val EXCEPTION_TYPE = "com.android.ApplicationNotResponding"
    private val EMPTY_ANR_STACKTRACE = EXCEPTION_TYPE + ": \n" +
                                       "\tat " + FreezeReport::class.java.name + ".missingEdtStack(Unknown source)"

    fun deserialize(baseProperties: DiagnosticReportProperties,
                    properties: Map<String, String>,
                    format: Long): FreezeReport {
      if (format >= 1L) {
        val dynamicProperties = TreeMap<String, String>(properties)
        val totalDuration = dynamicProperties.remove("totalDuration")?.toLong()
        val description = dynamicProperties.remove("description")
        val threadDumpPath = dynamicProperties.remove("threadDumpPath")?.let {
          fixDirectoryPathAndCheckIfReadable(
            Paths.get(it))
        }
        val timedOut = dynamicProperties.remove("timedOut")?.toBoolean() ?: false

        val paths = TreeMap<String, Path>()
        dynamicProperties.forEach { name, pathName ->
          if (name.endsWith("Path")) {
            fixDirectoryPathAndCheckIfReadable(
              Paths.get(pathName))?.let {
              paths[name.dropLast("Path".length)] = it
            }
          }
        }
        return FreezeReport(
          threadDumpPath,
          paths,
          timedOut,
          totalDuration,
          description,
          baseProperties
        )
      }
      throw IllegalArgumentException("Unrecognized format version: $format")
    }
  }
}