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
import com.android.tools.idea.stats.withProjectId
import com.android.tools.idea.ui.GuiTestingService
import com.android.tools.lint.checks.GooglePlaySdkIndex
import com.android.tools.lint.checks.GooglePlaySdkIndex.Companion.GOOGLE_PLAY_SDK_INDEX_KEY
import com.android.tools.lint.client.api.LintClient
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SDK_INDEX_CACHING_ERROR
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SDK_INDEX_DEFAULT_DATA_ERROR
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SDK_INDEX_LIBRARY_HAS_CRITICAL_ISSUES
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SDK_INDEX_LIBRARY_IS_NON_COMPLIANT
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SDK_INDEX_LIBRARY_IS_OUTDATED
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SDK_INDEX_LOADED_CORRECTLY
import com.google.wireless.android.sdk.stats.SdkIndexLibraryDetails
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

object IdeGooglePlaySdkIndex: GooglePlaySdkIndex(getCacheDir()) {
  private var lintClient: LintClient? = null

  override fun readUrlData(url: String, timeout: Int): ByteArray? = HttpRequests
    .request(URL(url).toExternalForm())
    .connectTimeout(timeout)
    .readTimeout(timeout)
    .readBytes(null)

  override fun error(throwable: Throwable, message: String?) =
    lintClient?.log(throwable, message) ?: Logger.getInstance(this::class.java).error(message, throwable)

  override fun logNonCompliant(groupId: String, artifactId: String, versionString: String, file: File?) {
    super.logNonCompliant(groupId, artifactId, versionString, file)
    logTrackerEventForLibraryVersion(groupId, artifactId, versionString, file, SDK_INDEX_LIBRARY_IS_NON_COMPLIANT)
  }

  override fun logHasCriticalIssues(groupId: String, artifactId: String, versionString: String, file: File?) {
    super.logHasCriticalIssues(groupId, artifactId, versionString, file)
    logTrackerEventForLibraryVersion(groupId, artifactId, versionString, file, SDK_INDEX_LIBRARY_HAS_CRITICAL_ISSUES)
  }

  override fun logOutdated(groupId: String, artifactId: String, versionString: String, file: File?) {
    super.logOutdated(groupId, artifactId, versionString, file)
    logTrackerEventForLibraryVersion(groupId, artifactId, versionString, file, SDK_INDEX_LIBRARY_IS_OUTDATED)
  }

  override fun logIndexLoadedCorrectly() {
    super.logIndexLoadedCorrectly()
    logTrackerEvent(SDK_INDEX_LOADED_CORRECTLY)
  }

  override fun logCachingError(message: String?) {
    super.logCachingError(message)
    logTrackerEvent(SDK_INDEX_CACHING_ERROR)
  }

  override fun logErrorInDefaultData(message: String?) {
    super.logErrorInDefaultData(message)
    logTrackerEvent(SDK_INDEX_DEFAULT_DATA_ERROR)
  }

  fun setLintClient(client: LintClient) {
    this.lintClient = client
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

  private fun logTrackerEvent(kind: AndroidStudioEvent.EventKind) {
    val event = createTrackerEvent(null, kind)
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
}

fun getCacheDir(): Path? {
  if (ApplicationManager.getApplication().isUnitTestMode || GuiTestingService.getInstance().isGuiTestingMode) {
    return null
  }
  return Paths.get(PathManager.getSystemPath()).normalize().resolve(GOOGLE_PLAY_SDK_INDEX_KEY)
}
