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
package com.android.tools.idea.diagnostics.report

import com.android.tools.analytics.AnalyticsSettings.dateProvider
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.project.Project
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val FILE_NAME = "MetricsInfo.log"
private const val DEFAULT_SIZE = 50

data class BuilderInfo(val date: Date, val builder: AndroidStudioEvent.Builder)

/**
 * MetricsLogFileProvider maintains a circular buffer of the most recent messages logged to the metrics infrastructure.
 * When queried it will write these messages to a file in the logs directory for inclusion in the diagnostics summary file.
 */
class MetricsLogFileProvider(private val pathProvider: PathProvider,
                             private val size: Int = DEFAULT_SIZE) : DiagnosticsSummaryFileProvider {
  override val name: String = "Metrics"
  private val builders = arrayOfNulls<BuilderInfo>(size)
  private var index = 0

  fun processEvent(builder: AndroidStudioEvent.Builder) {
    synchronized(builders) {
      builders[index] = BuilderInfo(dateProvider.now(), builder)
      index = (index + 1) % size
    }
  }

  override fun getFiles(project: Project?): List<FileInfo> {
    val orderedArray = arrayOfNulls<BuilderInfo>(size)

    synchronized(builders) {
      builders.copyInto(orderedArray, 0, index)
      builders.copyInto(orderedArray, builders.size - index, 0, index)
    }

    val path = DiagnosticsSummaryFileProvider.getDiagnosticsDirectoryPath(pathProvider.logDir).resolve(FILE_NAME)
    path.toFile().writeText(orderedArray.filterNotNull().joinToString("\n") { it.serialize() })

    return listOf(FileInfo(path, Paths.get(FILE_NAME)))
  }
}

private fun BuilderInfo.serialize(): String {
  val utcDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
  utcDateFormat.timeZone = TimeZone.getTimeZone("UTC")
  return "${utcDateFormat.format(this.date)}\n${this.builder}"
}

val DefaultMetricsLogFileProvider = MetricsLogFileProvider(DefaultPathProvider)