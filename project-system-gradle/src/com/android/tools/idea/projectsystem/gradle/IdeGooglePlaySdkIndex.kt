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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.stats.withProjectId
import com.android.tools.idea.ui.GuiTestingService
import com.android.tools.lint.checks.GooglePlaySdkIndex
import com.android.tools.lint.checks.GooglePlaySdkIndex.Companion.GOOGLE_PLAY_SDK_INDEX_KEY
import com.android.tools.lint.detector.api.LintFix
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SDK_INDEX_CACHING_ERROR
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SDK_INDEX_DEFAULT_DATA_ERROR
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SDK_INDEX_LIBRARY_HAS_CRITICAL_ISSUES
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SDK_INDEX_LIBRARY_IS_NON_COMPLIANT
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SDK_INDEX_LIBRARY_IS_OUTDATED
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SDK_INDEX_LINK_FOLLOWED
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SDK_INDEX_LOADED_CORRECTLY
import com.google.wireless.android.sdk.stats.SdkIndexLibraryDetails
import com.google.wireless.android.sdk.stats.SdkIndexLoadingDetails
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectForFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.io.HttpRequests
import java.io.File
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths

object IdeGooglePlaySdkIndex : GooglePlaySdkIndex(getCacheDir()) {
  val logger = Logger.getInstance(this::class.java)

  override fun readUrlData(url: String, timeout: Int): ByteArray? = HttpRequests
    .request(URL(url).toExternalForm())
    .connectTimeout(timeout)
    .readTimeout(timeout)
    .readBytes(null)

  override fun error(throwable: Throwable, message: String?) =
    logger.error(message, throwable)

  override fun logNonCompliant(groupId: String, artifactId: String, versionString: String, file: File?) {
    super.logNonCompliant(groupId, artifactId, versionString, file)
    logger.warn(generatePolicyMessage(groupId, artifactId, versionString))
    logTrackerEventForLibraryVersion(groupId, artifactId, versionString, file, SDK_INDEX_LIBRARY_IS_NON_COMPLIANT)
  }

  override fun logHasCriticalIssues(groupId: String, artifactId: String, versionString: String, file: File?) {
    super.logHasCriticalIssues(groupId, artifactId, versionString, file)
    val warnMsg =
      if (hasLibraryBlockingIssues(groupId, artifactId, versionString))
        generateBlockingCriticalMessage(groupId, artifactId, versionString)
      else
        generateCriticalMessage(groupId, artifactId, versionString)
    logger.warn(warnMsg)
    logTrackerEventForLibraryVersion(groupId, artifactId, versionString, file, SDK_INDEX_LIBRARY_HAS_CRITICAL_ISSUES)
  }

  override fun logOutdated(groupId: String, artifactId: String, versionString: String, file: File?) {
    super.logOutdated(groupId, artifactId, versionString, file)
    val warnMsg =
      if (hasLibraryBlockingIssues(groupId, artifactId, versionString))
        generateBlockingOutdatedMessage(groupId, artifactId, versionString)
      else
        generateOutdatedMessage(groupId, artifactId, versionString)
    logger.warn(warnMsg)
    logTrackerEventForLibraryVersion(groupId, artifactId, versionString, file, SDK_INDEX_LIBRARY_IS_OUTDATED)
  }

  override fun logIndexLoadedCorrectly(dataSourceType: DataSourceType) {
    super.logIndexLoadedCorrectly(dataSourceType)
    logger.info("SDK Index data loaded correctly from $dataSourceType")
    val event = createTrackerEvent(null, SDK_INDEX_LOADED_CORRECTLY)
    event.setSdkIndexLoadingDetails(
      SdkIndexLoadingDetails.newBuilder().setSourceType(dataSourceType.toTrackerType())
    )
    UsageTracker.log(event)
  }

  override fun logCachingError(readResult: ReadDataResult, dataSourceType: DataSourceType) {
    super.logCachingError(readResult, dataSourceType)
    val warnMsg = if (readResult.exception?.message.isNullOrEmpty()) "" else ": ${readResult.exception?.message}"
    logger.warn("Could not use data from cache$warnMsg (error: ${readResult.readDataErrorType}, source: $dataSourceType)")
    logTrackerEventForIndexLoadingError(SDK_INDEX_CACHING_ERROR, readResult, dataSourceType)
  }

  override fun logErrorInDefaultData(readResult: ReadDataResult) {
    super.logErrorInDefaultData(readResult)
    val warnMsg = if (readResult.exception?.message.isNullOrEmpty()) "" else ": ${readResult.exception?.message}"
    logger.warn("Could not use default SDK Index$warnMsg (${readResult.readDataErrorType})")
    logTrackerEventForIndexLoadingError(SDK_INDEX_DEFAULT_DATA_ERROR, readResult, DataSourceType.DEFAULT_DATA)
  }

  override fun generateSdkLinkLintFix(groupId: String, artifactId: String, versionString: String, buildFile: File?): LintFix? {
    val url = getSdkUrl(groupId, artifactId)
    return if (url != null)
      LintFix.ShowUrl(VIEW_DETAILS_MESSAGE, null, url, onUrlOpen = {
        logTrackerEventForLibraryVersion(groupId, artifactId, versionString, buildFile, SDK_INDEX_LINK_FOLLOWED)
      })
    else
      null
  }

  /***
   * Initialize the SDK index and set flags according to StudioFlags
   */
  fun initializeAndSetFlags() {
    initialize()
    showCriticalIssues = StudioFlags.SHOW_SDK_INDEX_CRITICAL_ISSUES.get()
    showMessages = StudioFlags.SHOW_SDK_INDEX_MESSAGES.get()
    showLinks = StudioFlags.INCLUDE_LINKS_TO_SDK_INDEX.get()
    showPolicyIssues = StudioFlags.SHOW_SDK_INDEX_POLICY_ISSUES.get()
  }

  private fun findProject(file: File): Project? {
    val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(file.toPath())
    return if (virtualFile == null) {
      null
    }
    else {
      guessProjectForFile(virtualFile)
    }
  }

  private fun logTrackerEventForLibraryVersion(groupId: String,
                                               artifactId: String,
                                               versionString: String,
                                               file: File?,
                                               kind: AndroidStudioEvent.EventKind) {
    val event = createTrackerEvent(file, kind)
    event.setSdkIndexLibraryDetails(
      SdkIndexLibraryDetails.newBuilder().setGroupId(groupId).setArtifactId(artifactId).setVersionString(versionString))
    UsageTracker.log(event)
  }

  private fun logTrackerEventForIndexLoadingError(kind: AndroidStudioEvent.EventKind, readResult: ReadDataResult, dataSource: DataSourceType) {
    val event = createTrackerEvent(null, kind)
    event.setSdkIndexLoadingDetails(
      SdkIndexLoadingDetails.newBuilder()
        .setReadErrorType(readResult.readDataErrorType.toTrackerType())
        .setSourceType(dataSource.toTrackerType())
    )
    UsageTracker.log(event)
  }

  private fun createTrackerEvent(file: File?,
                                 kind: AndroidStudioEvent.EventKind): AndroidStudioEvent.Builder {
    val event = AndroidStudioEvent.newBuilder()
      .setCategory(AndroidStudioEvent.EventCategory.GOOGLE_PLAY_SDK_INDEX)
      .setKind(kind)
    val project = if (file != null) {
      findProject(file)
    }
    else {
      null
    }
    if (project != null) {
      event.withProjectId(project)
    }
    return event
  }

  private fun DataSourceType.toTrackerType(): SdkIndexLoadingDetails.SourceType {
    return when (this) {
      DataSourceType.UNKNOWN_SOURCE -> SdkIndexLoadingDetails.SourceType.UNKNOWN_SOURCE
      DataSourceType.TEST_DATA -> SdkIndexLoadingDetails.SourceType.TEST_DATA
      DataSourceType.CACHE_FILE_EXPIRED_NO_NETWORK -> SdkIndexLoadingDetails.SourceType.CACHE_FILE_EXPIRED_NO_NETWORK
      DataSourceType.CACHE_FILE_EXPIRED_NETWORK_ERROR -> SdkIndexLoadingDetails.SourceType.CACHE_FILE_EXPIRED_NETWORK_ERROR
      DataSourceType.CACHE_FILE_EXPIRED_UNKNOWN -> SdkIndexLoadingDetails.SourceType.CACHE_FILE_EXPIRED_UNKNOWN
      DataSourceType.CACHE_FILE_RECENT -> SdkIndexLoadingDetails.SourceType.CACHE_FILE_RECENT
      DataSourceType.CACHE_FILE_NEW -> SdkIndexLoadingDetails.SourceType.CACHE_FILE_NEW
      DataSourceType.DEFAULT_DATA -> SdkIndexLoadingDetails.SourceType.DEFAULT_DATA
    }
  }

  private fun ReadDataErrorType.toTrackerType(): SdkIndexLoadingDetails.ReadErrorType {
    return when (this) {
      ReadDataErrorType.NO_ERROR -> SdkIndexLoadingDetails.ReadErrorType.NO_ERROR
      ReadDataErrorType.DATA_FUNCTION_EXCEPTION -> SdkIndexLoadingDetails.ReadErrorType.DATA_FUNCTION_EXCEPTION
      ReadDataErrorType.DATA_FUNCTION_NULL_ERROR -> SdkIndexLoadingDetails.ReadErrorType.DATA_FUNCTION_NULL_ERROR
      ReadDataErrorType.GZIP_EXCEPTION -> SdkIndexLoadingDetails.ReadErrorType.GZIP_EXCEPTION
      ReadDataErrorType.INDEX_PARSE_EXCEPTION -> SdkIndexLoadingDetails.ReadErrorType.INDEX_PARSE_EXCEPTION
      ReadDataErrorType.INDEX_PARSE_NULL_ERROR -> SdkIndexLoadingDetails.ReadErrorType.INDEX_PARSE_NULL_ERROR
    }
  }
}

fun getCacheDir(): Path? {
  if (ApplicationManager.getApplication().isUnitTestMode || GuiTestingService.getInstance().isGuiTestingMode) {
    return null
  }
  return Paths.get(PathManager.getSystemPath()).normalize().resolve(GOOGLE_PLAY_SDK_INDEX_KEY)
}
