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
package com.android.build.attribution.analyzers

import com.android.build.attribution.data.StudioProvidedInfo
import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData
import com.android.ide.common.repository.AgpVersion
import com.google.wireless.android.sdk.stats.BuildDownloadsAnalysisData.RepositoryStats.RepositoryType
import com.intellij.openapi.diagnostic.Logger
import org.gradle.tooling.Failure
import org.gradle.tooling.events.FailureResult
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.download.FileDownloadFinishEvent
import org.gradle.util.GradleVersion
import java.net.URI

private val LOG = Logger.getInstance(DownloadsAnalyzer::class.java)

/** Minimal version of gradle that provides file download events. */
val minGradleVersionProvidingDownloadEvents: GradleVersion = GradleVersion.version("7.3")

/**
 * The version of AGP that requires gradle at least 7.3.
 * We get AGP version from current build and can assume Gradle version surely based on that.
 */
private val minAgpVersionGuaranteesGradle_7_3 = AgpVersion.parse("7.2.0")

/**
 * Analyzer for aggregating data about file downloads during build.
 * Listens for Gradle TAPI events of [FileDownloadFinishEvent] type and
 * aggregates received info by repository.
 */
class DownloadsAnalyzer : BaseAnalyzer<DownloadsAnalyzer.Result>(),
                          BuildEventsAnalyzer,
                          BuildAttributionReportAnalyzer,
                          PostBuildProcessAnalyzer {

  private var statsAccumulator = DownloadStatsAccumulator()
  private var gradleCanProvideDownloadEvents: Boolean? = null
  private var currentAgpVersionFromBuild: AgpVersion? = null

  override fun receiveEvent(event: ProgressEvent) {
    statsAccumulator.receiveEvent(event)
  }

  override fun cleanupTempState() {
    statsAccumulator = DownloadStatsAccumulator()
    gradleCanProvideDownloadEvents = null
    currentAgpVersionFromBuild = null
  }

  override fun receiveBuildAttributionReport(androidGradlePluginAttributionData: AndroidGradlePluginAttributionData) {
    currentAgpVersionFromBuild = androidGradlePluginAttributionData.buildInfo?.agpVersion?.let { AgpVersion.tryParse(it) }
  }

  override fun runPostBuildAnalysis(analyzersResult: BuildEventsAnalyzersProxy, studioProvidedInfo: StudioProvidedInfo) {
    val doesCurrentAgpRequireGradleThatProvidesEvents = currentAgpVersionFromBuild?.let { it >= minAgpVersionGuaranteesGradle_7_3 } == true
    val canGradleVersionFromSettingsProvideEvents = studioProvidedInfo.gradleVersion?.let { it >= minGradleVersionProvidingDownloadEvents } == true
    gradleCanProvideDownloadEvents = doesCurrentAgpRequireGradleThatProvidesEvents || canGradleVersionFromSettingsProvideEvents
  }

  override fun calculateResult(): Result {
    if (gradleCanProvideDownloadEvents != true) return GradleDoesNotProvideEvents
    val resultList = statsAccumulator.repositoryResults

    LOG.debug("Downloads stats for this build: ", resultList)
    return ActiveResult(repositoryResults = resultList)
  }

  class DownloadStatsAccumulator {
    private val processedEvents = mutableListOf<DownloadResult>()
    val repositoryResults: List<RepositoryResult> get() = synchronized(processedEvents) {
      processedEvents.groupBy { it.repository }.map { (repo, events) ->
        RepositoryResult(repository = repo, downloads = events)
      }
    }

    fun receiveEvent(event: ProgressEvent) {
      if (event !is FileDownloadFinishEvent) return
      val repository = detectRepository(event.descriptor.uri)
      val status: DownloadStatus = when {
        event.result is FailureResult -> DownloadStatus.FAILURE
        event.result.bytesDownloaded == 0L -> DownloadStatus.MISSED
        else -> DownloadStatus.SUCCESS
      }
      val failureMessage: String? = (event.result as? FailureResult)?.let { buildFailureMessage(it.failures) }
      synchronized(processedEvents) {
        processedEvents.add(DownloadResult(
          timestamp = event.eventTime,
          repository = repository,
          url = event.descriptor.uri.toString(),
          status = status,
          duration = event.result.let { it.endTime - it.startTime },
          bytes = event.result.bytesDownloaded,
          failureMessage = failureMessage
        ))
      }
    }
  }

  sealed class Result: AnalyzerResult
  data class ActiveResult(
    val repositoryResults: List<RepositoryResult>
  ) : Result()

  object AnalyzerIsDisabled : Result()
  object GradleDoesNotProvideEvents: Result()

  enum class DownloadStatus {
    SUCCESS, MISSED, FAILURE
  }

  data class DownloadResult(
    val timestamp: Long,
    val repository: Repository,
    val url: String,
    val status: DownloadStatus,
    val duration: Long,
    val bytes: Long,
    val failureMessage: String?
  )

  data class RepositoryResult(
    val repository: Repository,
    val downloads: List<DownloadResult>
  ) {
    val successRequestsCount: Int
    val successRequestsTimeMs: Long
    val successRequestsBytesDownloaded: Long
    val missedRequestsCount: Int
    val missedRequestsTimeMs: Long
    val failedRequestsCount: Int
    val failedRequestsTimeMs: Long
    val failedRequestsBytesDownloaded: Long

    init {
      val groupedByStatus = downloads.groupBy { it.status }
      val successDownloads = groupedByStatus.getOrDefault(DownloadStatus.SUCCESS, emptyList())
      val failedDownloads = groupedByStatus.getOrDefault(DownloadStatus.FAILURE, emptyList())
      val missedDownloads = groupedByStatus.getOrDefault(DownloadStatus.MISSED, emptyList())
      successRequestsCount = successDownloads.size
      successRequestsTimeMs = successDownloads.sumOf { it.duration }
      successRequestsBytesDownloaded = successDownloads.sumOf { it.bytes }
      missedRequestsCount = missedDownloads.size
      missedRequestsTimeMs = missedDownloads.sumOf { it.duration }
      failedRequestsCount = failedDownloads.size
      failedRequestsTimeMs = failedDownloads.sumOf { it.duration }
      failedRequestsBytesDownloaded = failedDownloads.sumOf { it.bytes }
    }
  }

  companion object {
    fun detectRepository(uri: URI): Repository = KnownRepository.values().find { it.matches(uri) } ?: OtherRepository(uri.authority!!)
  }

  sealed interface Repository {
    val analyticsType: RepositoryType
  }

  data class OtherRepository(val host: String) : Repository {
    override val analyticsType: RepositoryType = RepositoryType.OTHER_REPOSITORY
  }

  enum class KnownRepository(
    val presentableName: String,
    override val analyticsType: RepositoryType,
    private val uri: URI
  ) : Repository {
    //TODO (mlazeba): maybe reuse from Repository.kt:24 somehow?
    //TODO (mlazeba): need to add plugins.gradle.org and repo.gradle.org? What else?
    GOOGLE("Google", RepositoryType.GOOGLE, URI.create("https://dl.google.com/dl/android/maven2/")),
    MAVEN_CENTRAL("Maven Central", RepositoryType.MAVEN_CENTRAL, URI.create("https://repo.maven.apache.org/maven2/")),
    JCENTER("JCenter", RepositoryType.JCENTER, URI.create("https://jcenter.bintray.com/"));

    fun matches(resourceURI: URI): Boolean {
      return uri.scheme == resourceURI.scheme
             && uri.authority == resourceURI.authority
    }
  }
}

fun buildFailureMessage(failures: List<Failure>): String = buildString { appendMessagesRecursively(failures) }

private fun StringBuilder.appendMessagesRecursively(failures: List<Failure>) {
  failures.forEach { failure ->
    failure.message?.let { append(it).appendLine() }
    appendMessagesRecursively(failure.causes)
  }
}