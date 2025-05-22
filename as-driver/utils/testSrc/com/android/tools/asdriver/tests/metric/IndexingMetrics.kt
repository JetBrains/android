/*
 * Copyright (C) 2024 The Android Open Source Project
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
@file:Suppress("UnstableApiUsage")

package com.android.tools.asdriver.tests.metric

import com.android.tools.asdriver.tests.AndroidProject
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.diagnostic.dto.JsonFileSize
import com.intellij.util.indexing.diagnostic.dto.JsonIndexingActivityDiagnostic
import com.intellij.util.indexing.diagnostic.dto.JsonProcessingSpeed
import com.intellij.util.indexing.diagnostic.dto.JsonProjectDumbIndexingFileCount
import com.intellij.util.indexing.diagnostic.dto.JsonProjectDumbIndexingHistory
import com.intellij.util.indexing.diagnostic.dto.JsonProjectScanningFileCount
import com.intellij.util.indexing.diagnostic.dto.JsonProjectScanningHistory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.extension
import kotlin.io.path.name

/**
 * IndexingMetricsCollector collects indexing-related metrics from the $LOGS_DIR/indexing-diagnostic directory.
 *
 * The following metrics will be collected:
 * <ul>
 * <li>indexingTimeWithoutPauses: total time spent on indexing excluding pauses;</li>
 * <li>scanningTimeWithoutPauses: total time spent in scanning excluding pauses;</li>
 * <li>pausedTimeInIndexingOrScanning: total time spent on indexing pauses;</li>
 * <li>dumbModeTimeWithPauses: total time spent in dumbMode excluding pauses;</li>
 * <li>numberOfIndexedFiles: total number of indexed files;</li>
 * <li>numberOfIndexedFilesWritingIndexValue: number of file indexings that led to indexes being updated;</li>
 * <li>numberOfIndexedFilesWithNothingToWrite: number of file indexings that didn't lead to indexes being updated;</li>
 * <li>numberOfFilesIndexedByExtensions: number of file indexings performed by extensions;</li>
 * <li>numberOfFilesIndexedWithoutExtensions: number of file indexings performed not by extensions;</li>
 * <li>numberOfRunsOfScanning: number of scanning iterations;</li>
 * <li>numberOfRunsOfIndexing: number of indexing iterations;</li>
 * <li>processingSpeedAvg_$FILE_TYPE: average processing speed for a given file type;</li>
 * <li>processingSpeedOfBaseLanguageAvg_$LANGUAGE: average processing speed for a given language;</li>
 * <li>processingTime_$FILE_TYPE:  processing time for a given file type;</li>
 * </ul>
 */
class IndexingMetrics(private val indexingDiagnosticsDir: Path) {
  fun get(project: AndroidProject): List<IndexingMetric> {
    val indexDiagnosticDirectoryChildren = Files.list(indexingDiagnosticsDir).filter { it.toFile().isDirectory }.use { it.toList() }

    val projectIndexDiagnosticDirectory = indexDiagnosticDirectoryChildren.let { perProjectDirs ->
      val projectName = project.targetProject.fileName
      if (projectName == null) {
        perProjectDirs.singleOrNull() ?: error("Only one project diagnostic dir is expected: ${perProjectDirs.joinToString()}")
      }
      else {
        perProjectDirs.find { it.name.startsWith("$projectName.") }
      }
    }
    val jsonIndexDiagnostics = Files.list(projectIndexDiagnosticDirectory)
      .use { stream -> stream.filter { it.extension == "json" }.toList() }
      .filter { Files.size(it) > 0L }
      .mapNotNull { IndexDiagnosticDumper.readJsonIndexingActivityDiagnostic(it) }
    return IndexingMetrics(jsonIndexDiagnostics).getIndexingMetrics()
  }

  data class IndexingMetrics(
    val jsonIndexDiagnostics: List<JsonIndexingActivityDiagnostic>,
  ) {
    private val scanningHistories: List<JsonProjectScanningHistory>
      get() = jsonIndexDiagnostics.map { it.projectIndexingActivityHistory }.filterIsInstance<JsonProjectScanningHistory>()
        .sortedBy { it.times.updatingStart.instant }
    private val indexingHistories: List<JsonProjectDumbIndexingHistory>
      get() = jsonIndexDiagnostics.map { it.projectIndexingActivityHistory }.filterIsInstance<JsonProjectDumbIndexingHistory>()
        .sortedBy { it.times.updatingStart.instant }

    private val totalNumberOfRunsOfScanning: Int
      get() = scanningHistories.count { it.projectName.isNotEmpty() }

    private val totalNumberOfRunsOfIndexing: Int
      get() = indexingHistories.count { it.projectName.isNotEmpty() }

    private val totalDumbModeTimeWithPauses: Long
      get() = jsonIndexDiagnostics.sumOf {
        TimeUnit.NANOSECONDS.toMillis(it.projectIndexingActivityHistory.times.dumbWallTimeWithPauses.nano)
      }

    private val totalIndexingTimeWithoutPauses: Long
      get() = TimeUnit.NANOSECONDS.toMillis(indexingHistories.sumOf { it.times.totalWallTimeWithoutPauses.nano })

    private val totalScanFilesTimeWithoutPauses: Long
      get() = TimeUnit.NANOSECONDS.toMillis(scanningHistories.sumOf { it.times.totalWallTimeWithoutPauses.nano })

    private val totalPausedTime: Long
      get() = TimeUnit.NANOSECONDS.toMillis(jsonIndexDiagnostics.sumOf { it.projectIndexingActivityHistory.times.wallTimeOnPause.nano })

    private val totalNumberOfIndexedFiles: Int
      get() = indexingHistories.sumOf { history -> history.fileProviderStatistics.sumOf { it.totalNumberOfIndexedFiles } }

    private val totalNumberOfIndexedFilesWritingIndexValues: Int
      get() = indexingHistories.sumOf { history -> history.fileProviderStatistics.sumOf { it.totalNumberOfIndexedFiles - it.totalNumberOfNothingToWriteFiles } }

    private val totalNumberOfIndexedFilesWithNothingToWrite: Int
      get() = indexingHistories.sumOf { history -> history.fileProviderStatistics.sumOf { it.totalNumberOfNothingToWriteFiles } }

    private val totalNumberOfFilesFullyIndexedByExtensions: Int
      get() = jsonIndexDiagnostics.sumOf {
        when (val fileCount = it.projectIndexingActivityHistory.fileCount) {
          is JsonProjectScanningFileCount -> fileCount.numberOfFilesIndexedByInfrastructureExtensionsDuringScan
          is JsonProjectDumbIndexingFileCount -> fileCount.numberOfFilesIndexedByInfrastructureExtensionsDuringIndexingStage
        }
      }

    private val processingSpeedPerFileTypeWorst: Map<String, Int>
      get() {
        return indexingHistories.flatMap { it.totalStatsPerFileType }.groupBy { it.fileType }.mapValues {
          it.value.minOf { jsonStatsPerFileType -> jsonStatsPerFileType.totalProcessingSpeed.toKiloBytesPerSecond() }
        }
      }

    private val processingSpeedPerFileTypeAvg: Map<String, Int>
      get() {
        return indexingHistories.flatMap { history ->
          history.totalStatsPerFileType.map {
            Triple(it.fileType, it.partOfTotalProcessingTime.partition * history.times.totalWallTimeWithPauses.nano, it.totalFilesSize)
          }
        }.computeAverageSpeed()
      }

    private fun Collection<Triple<String, Double, JsonFileSize>>.computeAverageSpeed(): Map<String, Int> = groupBy { it.first }.mapValues { entry ->
      JsonProcessingSpeed(entry.value.sumOf { it.third.bytes }, entry.value.sumOf { it.second.toLong() }).toKiloBytesPerSecond()
    }

    private val processingSpeedPerBaseLanguageWorst: Map<String, Int>
      get() {
        return indexingHistories.flatMap { it.totalStatsPerBaseLanguage }.groupBy { it.language }.mapValues {
          it.value.minOf { jsonStatsPerParentLanguage -> jsonStatsPerParentLanguage.totalProcessingSpeed.toKiloBytesPerSecond() }
        }
      }

    private val processingSpeedPerBaseLanguageAvg: Map<String, Int>
      get() {
        return indexingHistories.flatMap { history ->
          history.totalStatsPerBaseLanguage.map {
            Triple(it.language, it.partOfTotalProcessingTime.partition * history.times.totalWallTimeWithPauses.nano, it.totalFilesSize)
          }
        }.computeAverageSpeed()
      }

    private val processingTimePerFileType: Map<String, Long>
      get() {
        val indexingDurationMap = mutableMapOf<String, Long>()
        indexingHistories.forEach { indexingHistory ->
          indexingHistory.totalStatsPerFileType.forEach { totalStatsPerFileType ->
            val duration = (indexingHistory.times.totalWallTimeWithPauses.nano * totalStatsPerFileType.partOfTotalProcessingTime.partition).toLong()
            indexingDurationMap[totalStatsPerFileType.fileType] = indexingDurationMap[totalStatsPerFileType.fileType]?.let { it + duration }
                                                                  ?: duration
          }
        }
        return indexingDurationMap
      }

    private fun prepareMetricLabel(label: String): String {
      return label.replace(' ', '_')
    }

    private fun getProcessingSpeedOfFileTypes(mapFileTypeToSpeed: Map<String, Int>, suffix: String): List<IndexingMetric> =
      mapFileTypeToSpeed.map {
        IndexingMetric("processingSpeed${suffix}_${prepareMetricLabel(it.key)}", it.value.toLong())
      }

    private fun getProcessingSpeedOfBaseLanguages(mapBaseLanguageToSpeed: Map<String, Int>, suffix: String): List<IndexingMetric> =
      mapBaseLanguageToSpeed.map {
        IndexingMetric("processingSpeedOfBaseLanguage${suffix}_${prepareMetricLabel(it.key)}", it.value.toLong())
      }

    private fun getProcessingTimeOfFileType(mapFileTypeToDuration: Map<String, Long>): List<IndexingMetric> =
      mapFileTypeToDuration.map {
        IndexingMetric("processingTime_${prepareMetricLabel(it.key)}", TimeUnit.NANOSECONDS.toMillis(it.value))
      }

    fun getIndexingMetrics(): List<IndexingMetric> {
      val numberOfIndexedFiles = totalNumberOfIndexedFiles.toLong()
      val numberOfFilesFullyIndexedByExtensions = totalNumberOfFilesFullyIndexedByExtensions.toLong()
      return listOf(
        IndexingMetric("indexingTimeWithoutPauses", totalIndexingTimeWithoutPauses),
        IndexingMetric("scanningTimeWithoutPauses", totalScanFilesTimeWithoutPauses),
        IndexingMetric("pausedTimeInIndexingOrScanning", totalPausedTime),
        IndexingMetric("dumbModeTimeWithPauses", totalDumbModeTimeWithPauses),
        IndexingMetric("numberOfIndexedFiles", numberOfIndexedFiles),
        IndexingMetric("numberOfIndexedFilesWritingIndexValue", totalNumberOfIndexedFilesWritingIndexValues.toLong()),
        IndexingMetric("numberOfIndexedFilesWithNothingToWrite", totalNumberOfIndexedFilesWithNothingToWrite.toLong()),
        IndexingMetric("numberOfFilesIndexedByExtensions", numberOfFilesFullyIndexedByExtensions),
        IndexingMetric("numberOfFilesIndexedWithoutExtensions", numberOfIndexedFiles - numberOfFilesFullyIndexedByExtensions),
        IndexingMetric("numberOfRunsOfScanning", totalNumberOfRunsOfScanning.toLong()),
        IndexingMetric("numberOfRunsOfIndexing", totalNumberOfRunsOfIndexing.toLong())
      ) + getProcessingSpeedOfFileTypes(processingSpeedPerFileTypeAvg, "Avg") +
             getProcessingSpeedOfFileTypes(processingSpeedPerFileTypeWorst, "Worst") +
             getProcessingSpeedOfBaseLanguages(processingSpeedPerBaseLanguageAvg, "Avg") +
             getProcessingSpeedOfBaseLanguages(processingSpeedPerBaseLanguageWorst, "Worst") +
             getProcessingTimeOfFileType(processingTimePerFileType)
    }
  }

  data class IndexingMetric(val metricLabel: String, val metricValue: Long)

}
