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
package com.android.build.attribution.proto.converters

import com.android.build.attribution.BuildAnalysisResultsMessage
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.proto.PairEnumFinder
import com.google.wireless.android.sdk.stats.BuildDownloadsAnalysisData

class DownloadsAnalyzerResultMessageConverter {
  companion object {
    fun transform(downloadsAnalyzerResult: DownloadsAnalyzer.Result): BuildAnalysisResultsMessage.DownloadsAnalyzerResult = when (downloadsAnalyzerResult) {
      is DownloadsAnalyzer.ActiveResult -> BuildAnalysisResultsMessage.DownloadsAnalyzerResult.newBuilder()
        .setActiveResult(transformActiveResult(downloadsAnalyzerResult.repositoryResults))
        .setResultStatus(BuildAnalysisResultsMessage.DownloadsAnalyzerResult.ResultStatus.ACTIVE_RESULT)
        .build()

      is DownloadsAnalyzer.GradleDoesNotProvideEvents -> BuildAnalysisResultsMessage.DownloadsAnalyzerResult.newBuilder()
        .setResultStatus(BuildAnalysisResultsMessage.DownloadsAnalyzerResult.ResultStatus.GRADLE_DOES_NOT_PROVIDE_EVENTS)
        .build()

      is DownloadsAnalyzer.AnalyzerIsDisabled -> BuildAnalysisResultsMessage.DownloadsAnalyzerResult.newBuilder()
        .setResultStatus(BuildAnalysisResultsMessage.DownloadsAnalyzerResult.ResultStatus.ANALYZER_IS_DISABLED)
        .build()
    }

    fun construct(downloadsAnalyzerResult: BuildAnalysisResultsMessage.DownloadsAnalyzerResult): DownloadsAnalyzer.Result {
      val downloadAnalyzerResult: DownloadsAnalyzer.Result
      when (downloadsAnalyzerResult.resultStatus) {
        BuildAnalysisResultsMessage.DownloadsAnalyzerResult.ResultStatus.ACTIVE_RESULT -> {
          val repositoryResults = mutableListOf<DownloadsAnalyzer.RepositoryResult>()
          for (repositoryResult in downloadsAnalyzerResult.activeResult.repositoryResultList) {
            val downloadResults = mutableListOf<DownloadsAnalyzer.DownloadResult>()
            for (download in repositoryResult.downloadsList) {
              val repositoryType = constructRepositoryType(repositoryResult.repository.analyticsType, repositoryResult.repository.host)
              val status = constructDownloadStatus(download.status)
              downloadResults.add(
                DownloadsAnalyzer.DownloadResult(
                  download.timestamp,
                  repositoryType,
                  download.url,
                  status,
                  download.duration,
                  download.bytes,
                  if (download.failureMessage == "") null else download.failureMessage
                )
              )
              repositoryResults.add(DownloadsAnalyzer.RepositoryResult(repositoryType, downloadResults))
            }
          }
          downloadAnalyzerResult = DownloadsAnalyzer.ActiveResult(repositoryResults)
        }

        BuildAnalysisResultsMessage.DownloadsAnalyzerResult.ResultStatus.GRADLE_DOES_NOT_PROVIDE_EVENTS -> downloadAnalyzerResult = DownloadsAnalyzer.GradleDoesNotProvideEvents
        else -> downloadAnalyzerResult = DownloadsAnalyzer.AnalyzerIsDisabled
      }
      return downloadAnalyzerResult
    }

    private fun transformActiveResult(repositoryResults: List<DownloadsAnalyzer.RepositoryResult>) =
      BuildAnalysisResultsMessage.DownloadsAnalyzerResult.ActiveResult.newBuilder()
        .addAllRepositoryResult(repositoryResults.map(Companion::transformRepositoryResult))
        .build()

    private fun transformRepositoryResult(repositoryResult: DownloadsAnalyzer.RepositoryResult): BuildAnalysisResultsMessage.DownloadsAnalyzerResult.ActiveResult.RepositoryResult {
      val result = BuildAnalysisResultsMessage.DownloadsAnalyzerResult.ActiveResult.RepositoryResult.newBuilder()
        .addAllDownloads(repositoryResult.downloads.map(Companion::transformDownloadResult))
      if (repositoryResult.repository is DownloadsAnalyzer.OtherRepository) {
        result.repository = transformOtherRepository(repositoryResult.repository)
      }
      else {
        result.repository = transformRepository(repositoryResult.repository)
      }
      return result.build()
    }

    private fun transformDownloadResult(downloadResult: DownloadsAnalyzer.DownloadResult): BuildAnalysisResultsMessage.DownloadsAnalyzerResult.DownloadResult {
      val result = BuildAnalysisResultsMessage.DownloadsAnalyzerResult.DownloadResult.newBuilder()
        .setTimestamp(downloadResult.timestamp)
        .setUrl(downloadResult.url)
        .setStatus(transformDownloadStatus(downloadResult.status))
        .setDuration(downloadResult.duration)
        .setBytes(downloadResult.bytes)
        .setFailureMessage(downloadResult.failureMessage ?: "")
      if (downloadResult.repository is DownloadsAnalyzer.OtherRepository) {
        result.repository = transformOtherRepository(downloadResult.repository)
      }
      else {
        result.repository = transformRepository(downloadResult.repository)
      }
      return result.build()
    }

    private fun transformOtherRepository(repository: DownloadsAnalyzer.OtherRepository) =
      BuildAnalysisResultsMessage.DownloadsAnalyzerResult.Repository.newBuilder()
        .setAnalyticsType(transformRepositoryType(repository.analyticsType))
        .setHost(repository.host)
        .build()

    private fun transformRepository(repository: DownloadsAnalyzer.Repository) =
      BuildAnalysisResultsMessage.DownloadsAnalyzerResult.Repository.newBuilder()
        .setAnalyticsType(transformRepositoryType(repository.analyticsType))
        .build()


    private fun transformDownloadStatus(status: DownloadsAnalyzer.DownloadStatus): BuildAnalysisResultsMessage.DownloadsAnalyzerResult.DownloadResult.DownloadStatus =
      PairEnumFinder.aToB(status)

    private fun transformRepositoryType(repositoryType: BuildDownloadsAnalysisData.RepositoryStats.RepositoryType) =
      when(repositoryType) {
        BuildDownloadsAnalysisData.RepositoryStats.RepositoryType.GOOGLE ->
          BuildAnalysisResultsMessage.DownloadsAnalyzerResult.Repository.RepositoryType.GOOGLE
        BuildDownloadsAnalysisData.RepositoryStats.RepositoryType.JCENTER ->
          BuildAnalysisResultsMessage.DownloadsAnalyzerResult.Repository.RepositoryType.JCENTER
        BuildDownloadsAnalysisData.RepositoryStats.RepositoryType.MAVEN_CENTRAL ->
          BuildAnalysisResultsMessage.DownloadsAnalyzerResult.Repository.RepositoryType.MAVEN_CENTRAL
        BuildDownloadsAnalysisData.RepositoryStats.RepositoryType.OTHER_REPOSITORY ->
          BuildAnalysisResultsMessage.DownloadsAnalyzerResult.Repository.RepositoryType.OTHER_REPOSITORY
        BuildDownloadsAnalysisData.RepositoryStats.RepositoryType.UNKNOWN_REPOSITORY ->
          BuildAnalysisResultsMessage.DownloadsAnalyzerResult.Repository.RepositoryType.UNKNOWN_REPOSITORY
      }

    private fun constructRepositoryType(
      repositoryType: BuildAnalysisResultsMessage.DownloadsAnalyzerResult.Repository.RepositoryType,
      host: String
    ) =
      when (repositoryType) {
        BuildAnalysisResultsMessage.DownloadsAnalyzerResult.Repository.RepositoryType.UNKNOWN_REPOSITORY
        -> DownloadsAnalyzer.OtherRepository(host)

        BuildAnalysisResultsMessage.DownloadsAnalyzerResult.Repository.RepositoryType.GOOGLE
        -> DownloadsAnalyzer.KnownRepository.GOOGLE

        BuildAnalysisResultsMessage.DownloadsAnalyzerResult.Repository.RepositoryType.MAVEN_CENTRAL
        -> DownloadsAnalyzer.KnownRepository.MAVEN_CENTRAL

        BuildAnalysisResultsMessage.DownloadsAnalyzerResult.Repository.RepositoryType.JCENTER
        -> DownloadsAnalyzer.KnownRepository.JCENTER

        BuildAnalysisResultsMessage.DownloadsAnalyzerResult.Repository.RepositoryType.OTHER_REPOSITORY
        -> DownloadsAnalyzer.OtherRepository(host)

        BuildAnalysisResultsMessage.DownloadsAnalyzerResult.Repository.RepositoryType.UNRECOGNIZED -> throw IllegalStateException(
          "Unrecognized repository type")
      }

    private fun constructDownloadStatus(status: BuildAnalysisResultsMessage.DownloadsAnalyzerResult.DownloadResult.DownloadStatus): DownloadsAnalyzer.DownloadStatus =
      PairEnumFinder.bToA(status)
  }
}