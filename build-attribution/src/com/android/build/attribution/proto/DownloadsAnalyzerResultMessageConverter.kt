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
package com.android.build.attribution.proto

import com.android.build.attribution.BuildAnalysisResultsMessage.DownloadsAnalyzerResult
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.BuildDownloadsAnalysisData

class DownloadsAnalyzerResultMessageConverter {
  companion object {
    fun transform(downloadsAnalyzerResult: DownloadsAnalyzer.Result): DownloadsAnalyzerResult = when (downloadsAnalyzerResult) {
      is DownloadsAnalyzer.ActiveResult -> DownloadsAnalyzerResult.newBuilder()
        .setActiveResult(transformActiveResult(downloadsAnalyzerResult.repositoryResults))
        .setResultStatus(DownloadsAnalyzerResult.ResultStatus.ACTIVE_RESULT)
        .build()

      is DownloadsAnalyzer.GradleDoesNotProvideEvents -> DownloadsAnalyzerResult.newBuilder()
        .setResultStatus(DownloadsAnalyzerResult.ResultStatus.GRADLE_DOES_NOT_PROVIDE_EVENTS)
        .build()

      is DownloadsAnalyzer.AnalyzerIsDisabled -> DownloadsAnalyzerResult.newBuilder()
        .setResultStatus(DownloadsAnalyzerResult.ResultStatus.ANALYZER_IS_DISABLED)
        .build()
    }

    fun construct(downloadsAnalyzerResult: DownloadsAnalyzerResult): DownloadsAnalyzer.Result {
      val downloadAnalyzerResult: DownloadsAnalyzer.Result
      when (downloadsAnalyzerResult.resultStatus) {
        DownloadsAnalyzerResult.ResultStatus.ACTIVE_RESULT -> {
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
                  download.failureMessage
                )
              )
              repositoryResults.add(DownloadsAnalyzer.RepositoryResult(repositoryType, downloadResults))
            }
          }
          downloadAnalyzerResult = DownloadsAnalyzer.ActiveResult(repositoryResults)
        }

        DownloadsAnalyzerResult.ResultStatus.GRADLE_DOES_NOT_PROVIDE_EVENTS -> downloadAnalyzerResult = DownloadsAnalyzer.GradleDoesNotProvideEvents
        else -> downloadAnalyzerResult = DownloadsAnalyzer.AnalyzerIsDisabled
      }
      return downloadAnalyzerResult
    }

    private fun transformActiveResult(repositoryResults: List<DownloadsAnalyzer.RepositoryResult>) =
      DownloadsAnalyzerResult.ActiveResult.newBuilder()
        .addAllRepositoryResult(repositoryResults.map(::transformRepositoryResult))
        .build()

    private fun transformRepositoryResult(repositoryResult: DownloadsAnalyzer.RepositoryResult): DownloadsAnalyzerResult.ActiveResult.RepositoryResult {
      val result = DownloadsAnalyzerResult.ActiveResult.RepositoryResult.newBuilder()
        .addAllDownloads(repositoryResult.downloads.map(::transformDownloadResult))
      if (repositoryResult.repository is DownloadsAnalyzer.OtherRepository) {
        result.repository = transformOtherRepository(repositoryResult.repository)
      }
      else {
        result.repository = transformRepository(repositoryResult.repository)
      }
      return result.build()
    }

    private fun transformDownloadResult(downloadResult: DownloadsAnalyzer.DownloadResult): DownloadsAnalyzerResult.DownloadResult {
      val result = DownloadsAnalyzerResult.DownloadResult.newBuilder()
        .setTimestamp(downloadResult.timestamp)
        .setUrl(downloadResult.url)
        .setStatus(transformDownloadStatus(downloadResult.status))
        .setDuration(downloadResult.duration)
        .setBytes(downloadResult.bytes)
        .setFailureMessage(downloadResult.failureMessage)
      if (downloadResult.repository is DownloadsAnalyzer.OtherRepository) {
        result.repository = transformOtherRepository(downloadResult.repository)
      }
      else {
        result.repository = transformRepository(downloadResult.repository)
      }
      return result.build()
    }

    private fun transformOtherRepository(repository: DownloadsAnalyzer.OtherRepository) =
      DownloadsAnalyzerResult.Repository.newBuilder()
        .setAnalyticsType(transformRepositoryType(repository.analyticsType))
        .setHost(repository.host)
        .build()

    private fun transformRepository(repository: DownloadsAnalyzer.Repository) =
      DownloadsAnalyzerResult.Repository.newBuilder()
        .setAnalyticsType(transformRepositoryType(repository.analyticsType))
        .build()

    private fun transformDownloadStatus(status: DownloadsAnalyzer.DownloadStatus) =
      when (status) {
        DownloadsAnalyzer.DownloadStatus.SUCCESS -> DownloadsAnalyzerResult.DownloadResult.DownloadStatus.SUCCESS
        DownloadsAnalyzer.DownloadStatus.MISSED -> DownloadsAnalyzerResult.DownloadResult.DownloadStatus.MISSED
        DownloadsAnalyzer.DownloadStatus.FAILURE -> DownloadsAnalyzerResult.DownloadResult.DownloadStatus.FAILURE
      }

    private fun transformRepositoryType(repositoryType: BuildDownloadsAnalysisData.RepositoryStats.RepositoryType) =
      when (repositoryType) {
        BuildDownloadsAnalysisData.RepositoryStats.RepositoryType.UNKNOWN_REPOSITORY ->
          DownloadsAnalyzerResult.Repository.RepositoryType.UNKNOWN_REPOSITORY

        BuildDownloadsAnalysisData.RepositoryStats.RepositoryType.GOOGLE ->
          DownloadsAnalyzerResult.Repository.RepositoryType.GOOGLE

        BuildDownloadsAnalysisData.RepositoryStats.RepositoryType.MAVEN_CENTRAL ->
          DownloadsAnalyzerResult.Repository.RepositoryType.MAVEN_CENTRAL

        BuildDownloadsAnalysisData.RepositoryStats.RepositoryType.JCENTER ->
          DownloadsAnalyzerResult.Repository.RepositoryType.JCENTER

        BuildDownloadsAnalysisData.RepositoryStats.RepositoryType.OTHER_REPOSITORY ->
          DownloadsAnalyzerResult.Repository.RepositoryType.OTHER_REPOSITORY
      }

    private fun constructRepositoryType(
      repositoryType: DownloadsAnalyzerResult.Repository.RepositoryType,
      host: String
    ) =
      when (repositoryType) {
        DownloadsAnalyzerResult.Repository.RepositoryType.UNKNOWN_REPOSITORY
        -> DownloadsAnalyzer.OtherRepository(host)

        DownloadsAnalyzerResult.Repository.RepositoryType.GOOGLE
        -> DownloadsAnalyzer.KnownRepository.GOOGLE

        DownloadsAnalyzerResult.Repository.RepositoryType.MAVEN_CENTRAL
        -> DownloadsAnalyzer.KnownRepository.MAVEN_CENTRAL

        DownloadsAnalyzerResult.Repository.RepositoryType.JCENTER
        -> DownloadsAnalyzer.KnownRepository.JCENTER

        DownloadsAnalyzerResult.Repository.RepositoryType.OTHER_REPOSITORY
        -> DownloadsAnalyzer.OtherRepository(host)

        DownloadsAnalyzerResult.Repository.RepositoryType.UNRECOGNIZED -> throw IllegalStateException(
          "Unrecognized repository type")
      }

    @VisibleForTesting
    fun constructDownloadStatus(status: DownloadsAnalyzerResult.DownloadResult.DownloadStatus) =
      when (status) {
        DownloadsAnalyzerResult.DownloadResult.DownloadStatus.SUCCESS -> DownloadsAnalyzer.DownloadStatus.SUCCESS
        DownloadsAnalyzerResult.DownloadResult.DownloadStatus.FAILURE -> DownloadsAnalyzer.DownloadStatus.FAILURE
        DownloadsAnalyzerResult.DownloadResult.DownloadStatus.MISSED -> DownloadsAnalyzer.DownloadStatus.MISSED
        DownloadsAnalyzerResult.DownloadResult.DownloadStatus.UNKNOWN -> throw IllegalStateException("Unrecognised download status")
        DownloadsAnalyzerResult.DownloadResult.DownloadStatus.UNRECOGNIZED -> throw IllegalStateException("Unrecognised download status")
      }
  }
}