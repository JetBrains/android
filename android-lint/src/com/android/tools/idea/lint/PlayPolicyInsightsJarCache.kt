/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.lint

import com.android.ide.common.repository.GMAVEN_BASE_URL
import com.android.ide.common.repository.GoogleMavenRepository
import com.android.ide.common.repository.GoogleMavenRepository.Companion.MAVEN_GOOGLE_CACHE_DIR_KEY
import com.android.repository.api.Checksum
import com.android.repository.api.ConsoleProgressIndicator
import com.android.repository.api.Downloader
import com.android.repository.api.ProgressIndicatorAdapter
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gservices.DevServicesDeprecationDataProvider
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.ui.GuiTestingService
import com.android.tools.idea.util.StudioPathManager
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.readUrlData
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.text.nullize
import java.io.File
import java.io.IOException
import java.lang.System.currentTimeMillis
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private const val GOOGLE_PLAY_POLICY_INSIGHTS_KEY = "play_policy_insights"
private const val GROUP_ID = "com.google.play.policy.insights"
private const val ARTIFACT_ID = "insights-lint"
private const val BUNDLED_JAR_PATH = "insights-lint-0.1.2.jar"
private const val MIN_UPDATE_BACKOFF_MINUTES = 10L
private const val CACHE_EXPIRY_DAYS = 7L

private const val DEPRECATION_SERVICE_NAME = "aqi/policy"
private const val DEPRECATION_USER_FRIENDLY_SERVICE_NAME = "Play Policy Insights"

/** Load and cache the custom lint rule jars for play policy insights. */
class PlayPolicyInsightsJarCache(
  private val client: AndroidLintIdeClient,
  private val cachedDir: Path?,
  private val downloader: Downloader,
) {
  constructor(client: AndroidLintIdeClient) : this(client, getCacheDir(), StudioDownloader())

  private val scope = client.myProject.coroutineScope.createChildScope(isSupervisor = true)
  @kotlin.concurrent.Volatile private var cachedFile: File? = null

  private val bundledJar: File? = getBundledJar()
  private var googleMavenRepository: GoogleMavenRepository? = null
  @VisibleForTesting val isUpdating = MutableStateFlow(false)
  @kotlin.concurrent.Volatile private var nextUpdatingTimeMs = 0L
  private val targetLibraryVersion =
    StudioFlags.PLAY_POLICY_INSIGHTS_TARGET_LIBRARY_VERSION.get().trim()

  private fun getGoogleMavenRepository(): GoogleMavenRepository {
    return googleMavenRepository
      ?: run {
        val cacheDir = client.getCacheDir(MAVEN_GOOGLE_CACHE_DIR_KEY, true)
        val repository =
          object : GoogleMavenRepository(cacheDir?.toPath()) {
            override fun readUrlData(
              url: String,
              timeout: Int,
              lastModified: Long,
            ): ReadUrlDataResult = readUrlData(client, url, timeout, lastModified)

            override fun error(throwable: Throwable, message: String?) =
              client.log(throwable, message)
          }

        googleMavenRepository = repository
        repository
      }
  }

  /** Returns the bundle jar from resources folder. */
  private fun getBundledJar(): File? {
    val path =
      if (StudioPathManager.isRunningFromSources()) {
        StudioPathManager.resolvePathFromSourcesRoot(
          "tools/adt/idea/android-lint/policy-checks/${BUNDLED_JAR_PATH}"
        )
      } else {
        val homePath: String = FileUtil.toSystemIndependentName(PathManager.getHomePath())
        Paths.get(homePath, "plugins/android/resources/${BUNDLED_JAR_PATH}")
      }
    return path.toFile().takeIf { it.exists() }
  }

  /** Returns the custom lint rule jars for play policy insights from gMaven. */
  @Synchronized
  fun getCustomRuleJars(): List<File> {
    val gMavenRuleJar = getGMavenRuleJar()?.takeIf { it.exists() && it.name != bundledJar?.name }
    return listOfNotNull(bundledJar, gMavenRuleJar)
  }

  private fun getGMavenRuleJar(): File? {
    // No more updates are needed if we already have the target version of library.
    if (targetLibraryVersion.isNotEmpty() && cachedFile != null) {
      return cachedFile
    }

    // Check if the jar from gMaven is cached.
    try {
      if (cachedFile == null) {
        val dir = cachedDir ?: return null
        // Find the jar file with target version if exists.
        var jarFile =
          targetLibraryVersion
            .nullize()
            ?.let { version -> "$ARTIFACT_ID-$version.jar" }
            ?.let { jarName -> dir.resolve(jarName).toFile() }
            ?.takeIf { file -> file.exists() }
        // Find the latest library in the directory.
        if (jarFile == null) {
          jarFile =
            dir
              .toFile()
              .listFiles()
              ?.filter { it.name.startsWith(ARTIFACT_ID) && it.name.endsWith(".jar") }
              ?.sortedBy { file -> file.name }
              ?.maxByOrNull { file -> file.lastModified() }
        }
        if (jarFile != null) {
          // Verify existing jar file with its sha256 checksum.
          if (sha256Verified(jarFile.sha256(), jarFile.sha256FileText())) {
            cachedFileVerified(jarFile)
          }
        }
      }
    } catch (e: Exception) {
      client.log(
        Severity.WARNING,
        e,
        "Failed to find cached lint rule jar: ${GROUP_ID}-${ARTIFACT_ID}",
      )
    }

    updateCachedJar()
    return cachedFile
  }

  /** Starts a new job to update the cached file. */
  private fun updateCachedJar() {
    // Check service deprecation status if target version is not specified.
    if (
      targetLibraryVersion.isEmpty() &&
        service<DevServicesDeprecationDataProvider>()
          .getCurrentDeprecationData(
            DEPRECATION_SERVICE_NAME,
            DEPRECATION_USER_FRIENDLY_SERVICE_NAME,
          )
          .isUnsupported()
    )
      return

    val dir = cachedDir ?: return

    val actionTimeMs = currentTimeMillis()
    if (actionTimeMs < nextUpdatingTimeMs) {
      return
    }
    if (isUpdating.compareAndSet(expect = false, update = true)) {
      // Apply the minimum backoff time for next update.
      nextUpdatingTimeMs =
        nextUpdatingTimeMs.coerceAtMost(
          actionTimeMs + TimeUnit.MINUTES.toMillis(MIN_UPDATE_BACKOFF_MINUTES)
        )
      scope
        .launch(Dispatchers.IO) {
          try {
            // Update the library from gMaven.
            val repository = getGoogleMavenRepository()
            val version =
              targetLibraryVersion.takeIf { it.isNotEmpty() }
                ?: repository.getVersions(GROUP_ID, ARTIFACT_ID).maxOrNull()
                ?: return@launch
            val jarName = "${ARTIFACT_ID}-${version}.jar"
            val urlResolver: (String) -> URL = { fileName ->
              URL(
                "${GMAVEN_BASE_URL}/${GROUP_ID.replace('.', '/')}/${ARTIFACT_ID}/${version}/${fileName}"
              )
            }
            val jarPath = dir.resolve(jarName)
            val jarFile = jarPath.toFile()

            // Update the sha256 file.
            var sha256FileText = jarFile.sha256FileText()
            var sha256 = jarFile.sha256()
            val verified: () -> Boolean = { sha256Verified(sha256, sha256FileText) }

            if (!verified()) {
              val shaFile = jarFile.sha256File()
              downloader.downloadFullyWithCaching(
                urlResolver(shaFile.name),
                shaFile.toPath(),
                null,
                object : ProgressIndicatorAdapter() {},
              )
              sha256FileText = jarFile.sha256FileText()
            }

            // Download the jar.
            if (!verified()) {
              downloader.downloadFullyWithCaching(
                urlResolver(jarName),
                jarPath,
                Checksum.create(sha256FileText, "sha-256"),
                ConsoleProgressIndicator(),
              )
              sha256 = jarFile.sha256()
            }

            if (verified()) {
              cachedFileVerified(jarFile, actionTimeMs)
            }
          } catch (e: Exception) {
            client.log(Severity.WARNING, e, "Failed to download jar: ${GROUP_ID}-${ARTIFACT_ID}")
          }
          try {
            // Remove outdated files.
            dir.toFile().listFiles()?.forEach { fileToRemove ->
              if (cachedFile?.let { file -> FileUtils.isSameFile(fileToRemove, file) } != true) {
                fileToRemove.deleteOnExit()
              }
            }
          } catch (e: IOException) {
            client.log(
              e,
              "Failed to remove outdated jars in %s folder",
              GOOGLE_PLAY_POLICY_INSIGHTS_KEY,
            )
          }
        }
        .invokeOnCompletion { isUpdating.value = false }
    }
  }

  /** Checksum of the file with sha256 algorithm. */
  private fun File.sha256(): String =
    takeIf { exists() }?.let { Hashing.sha256().hashBytes(Files.toByteArray(this)).toString() }
      ?: ""

  /** Checksum read from its sha256 file. */
  private fun File.sha256FileText(): String =
    sha256File().takeIf { it.exists() }?.readText()?.trim() ?: ""

  private fun File.sha256File(): File = File("${path}.sha256")

  private fun sha256Verified(checksum1: String, checksum2: String) =
    checksum1.isNotEmpty() && checksum1 == checksum2

  private fun cachedFileVerified(file: File, timestamp: Long = file.lastModified()) {
    cachedFile = file
    nextUpdatingTimeMs =
      nextUpdatingTimeMs.coerceAtMost(timestamp + TimeUnit.DAYS.toMillis(CACHE_EXPIRY_DAYS))
  }
}

private fun getCacheDir(): Path? {
  if (
    ApplicationManager.getApplication().isUnitTestMode ||
      GuiTestingService.getInstance().isGuiTestingMode
  ) {
    return null
  }
  return Paths.get(PathManager.getSystemPath()).normalize().resolve(GOOGLE_PLAY_POLICY_INSIGHTS_KEY)
}
