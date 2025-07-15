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
import com.android.repository.api.ConsoleProgressIndicator
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.ui.GuiTestingService
import com.android.tools.idea.util.StudioPathManager
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.readUrlData
import com.android.utils.FileUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
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

/** Load and cache the custom lint rule jars for play policy insights. */
class PlayPolicyInsightsJarCache(private val client: AndroidLintIdeClient) {
  private val scope = client.myProject.coroutineScope.createChildScope(isSupervisor = true)
  private val cachedDir = getCacheDir()
  @kotlin.concurrent.Volatile private var cachedFile: File? = null

  private val bundledJar: File? = getBundledJar()
  private var googleMavenRepository: GoogleMavenRepository? = null
  private val isUpdating = MutableStateFlow(false)
  private var nextUpdatingTimeMs = 0L

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
    if (!StudioFlags.PLAY_POLICY_INSIGHTS_AUTO_UPDATE.get()) return null
    // Check if the jar from gMaven is cached.
    try {
      if (cachedFile == null) {
        val dir = cachedDir ?: return null
        cachedFile =
          dir
            .toFile()
            .listFiles()
            ?.filter { it.name.startsWith(ARTIFACT_ID) }
            ?.sortedBy { file -> file.name }
            ?.maxByOrNull { file -> file.lastModified() }
        cachedFileVerified(cachedFile?.lastModified() ?: 0L)
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
            val version = repository.getVersions(GROUP_ID, ARTIFACT_ID).maxOrNull() ?: return@launch
            val fileName = "${ARTIFACT_ID}-${version}.jar"
            val url =
              URL(
                "${GMAVEN_BASE_URL}/${GROUP_ID.replace('.', '/')}/${ARTIFACT_ID}/${version}/${fileName}"
              )
            val path = dir.resolve(fileName)
            val file = path.toFile()
            if (!file.exists()) {
              StudioDownloader()
                .downloadFullyWithCaching(url, path, null, ConsoleProgressIndicator())
            }
            cachedFile = file
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

  private fun cachedFileVerified(timestamp: Long) {
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
