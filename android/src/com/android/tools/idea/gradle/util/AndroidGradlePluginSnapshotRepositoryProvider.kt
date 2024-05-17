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
package com.android.tools.idea.gradle.util

import com.android.annotations.concurrency.Slow
import com.android.ide.common.gradle.Module
import com.android.ide.common.repository.AgpVersion
import com.android.ide.common.repository.IdeNetworkCacheUtils
import com.android.ide.common.repository.NetworkCache
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository
import com.android.tools.idea.ui.GuiTestingService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.JDOMUtil
import java.io.InputStream
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern

private const val ANDROIDX_DEV_BASE_URL = "https://androidx.dev/"
private const val ANDROIDX_DEV_CACHE_DIR_KEY = "androidx.dev"
private val AGP_PLUGIN_MARKER_MODULE = Module("com.android.application", "com.android.application.gradle.plugin")
private val ARTIFACT_LINK_PATTERN = Pattern.compile(".*/studio/builds/(\\d+)/artifact.*")

private val Module.path get() = "${group.replace('.', '/')}/$name"
private val Module.mavenMetadataPath get() = "$path/maven-metadata.xml"

object IdeAndroidGradlePluginSnapshotRepositoryProvider : AndroidGradlePluginSnapshotRepositoryProvider(getCacheDir()) {
  @Slow
  override fun readUrlData(url: String, timeout: Int, lastModified: Long) =
    IdeNetworkCacheUtils.readHttpUrlData(url, timeout, lastModified)
}

abstract class AndroidGradlePluginSnapshotRepositoryProvider(cacheDir: Path?) : NetworkCache(
  baseUrl = ANDROIDX_DEV_BASE_URL,
  cacheKey = ANDROIDX_DEV_CACHE_DIR_KEY,
  cacheDir = cacheDir,
  networkTimeoutMs = 3000,
  cacheExpiryHours = 1,
  networkEnabled = true,
) {

  data class SnapshotRepository(
    val agpVersions: Set<AgpVersion>,
    val repositoryURL: URL,
  )

  override fun readDefaultData(relative: String): InputStream? = null

  override fun error(throwable: Throwable, message: String?) {
    Logger.getInstance(IdeGoogleMavenRepository::class.java).warn(message, throwable)
  }

  @Slow
  fun getLatestSnapshot(): SnapshotRepository? {
    val indexPageContent = (findData("studio/builds", treatAsDirectory = true) ?: return null).use { it.bufferedReader().readText() }
    val matcher = ARTIFACT_LINK_PATTERN.matcher(indexPageContent)
    if (!matcher.find()) {
      error(Exception(), "Unable to find latest AGP snapshot build. Failed to parse index page content from ${ANDROIDX_DEV_BASE_URL}studio/builds")
      return null
    }
    val buildId = matcher.group(1)
    val repositoryPath = "studio/builds/$buildId/artifacts/artifacts/repository"
    val mavenMetadataPath = repositoryPath + '/' + AGP_PLUGIN_MARKER_MODULE.mavenMetadataPath
    val versionStrings = (findData(mavenMetadataPath) ?: return null).use { mavenMetadataXml ->
      try {
        JDOMUtil.load(mavenMetadataXml).getChild("versioning").getChild("versions").getChildren("version").map { it.text }
      }
      catch (e: Exception) {
        error(e, "Unable to find latest AGP snapshot build. Failed to parse content of $ANDROIDX_DEV_BASE_URL$mavenMetadataPath")
        return null
      }
    }
    val versions = versionStrings.mapTo(mutableSetOf()) {
      try {
        AgpVersion.parse(it)
      } catch (e: Exception) {
        error(e,"Unable to find latest AGP snapshot build. Invalid AGP version '$it' in $ANDROIDX_DEV_BASE_URL$mavenMetadataPath")
        return null
      }
    }
    return SnapshotRepository(versions.toSet(), URL(ANDROIDX_DEV_BASE_URL + repositoryPath))
  }
}

private fun getCacheDir(): Path? {
  if (ApplicationManager.getApplication().isUnitTestMode || GuiTestingService.getInstance().isGuiTestingMode) {
    return null
  }
  return Paths.get(PathManager.getSystemPath()).normalize().resolve(ANDROIDX_DEV_CACHE_DIR_KEY)
}
