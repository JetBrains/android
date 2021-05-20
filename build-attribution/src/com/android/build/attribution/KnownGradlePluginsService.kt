/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.build.attribution

import com.android.annotations.concurrency.Slow
import com.android.build.attribution.data.GradlePluginsData
import com.android.tools.idea.downloads.DownloadService
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ResourceUtil
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.download.FileDownloader
import java.io.File
import java.io.IOException
import java.net.URL

private const val SERVICE_NAME = "Known gradle plugins info"
private const val PLUGINS_DATA_URL = "https://dl.google.com/android/studio/metadata/known_gradle_plugins.json"
private const val FILENAME = "plugins.json"
private const val DOWNLOAD_FILENAME = "plugins_temp.json"
private val FALLBACK_URL = ResourceUtil.getResource(KnownGradlePluginsServiceImpl::class.java, "knownGradlePluginsData", FILENAME)
private val CACHE_PATH = File(PathManager.getSystemPath(), "knownGradlePluginsData")

/**
 * Knowledge database that provides information on known gradle plugins.
 */
interface KnownGradlePluginsService {
  val gradlePluginsData: GradlePluginsData

  /** Triggers async data refresh attempt.*/
  fun asyncRefresh()
}

/**
 * Implementation of gradle plugins knowledge database that tries to pull latest json data from the server ([PLUGINS_DATA_URL]).
 * In case of failure it reads default data provided with release version from the local file ([FALLBACK_URL]).
 * Received data is cached and new requests will not repeat more often than once in a day.
 *
 * To keep data updated [asyncRefresh] and [refreshSynchronously] call should be used. The intended usage is that [asyncRefresh]
 * would be called on service creation and each build start and [refreshSynchronously] would be called during build analysis
 * to make sure data is ready before proceeding. Note that data refresh interval is 1 day thus most of these requests will
 * not proceed making any delay to be an extremely rare case.
 */
class KnownGradlePluginsServiceImpl constructor(downloader: FileDownloader, cachePath: File) :
  DownloadService(downloader, SERVICE_NAME, FALLBACK_URL, cachePath, FILENAME),
  KnownGradlePluginsService {

  constructor() : this(DownloadableFileService.getInstance().run {
    createDownloader(listOf(createFileDescription(PLUGINS_DATA_URL, DOWNLOAD_FILENAME)), SERVICE_NAME)
  }, CACHE_PATH)

  override var gradlePluginsData: GradlePluginsData = GradlePluginsData.emptyData
    private set
    @Slow get() {
      refreshSynchronously()
      return field
    }

  init {
    asyncRefresh()
  }

  override fun asyncRefresh() {
    refresh(null, null)
  }

  override fun loadFromFile(url: URL) {
    try {
      val jsonString = ResourceUtil.loadText(url)
      gradlePluginsData = GradlePluginsData.loadFromJson(jsonString)
    }
    catch (e: IOException) {
      Logger.getInstance(KnownGradlePluginsServiceImpl::class.java).error("Error while trying to load plugins file", e)
    }
  }
}

class LocalKnownGradlePluginsServiceImpl : KnownGradlePluginsService {
  override val gradlePluginsData = try {
    GradlePluginsData.loadFromJson(ResourceUtil.loadText(FALLBACK_URL))
  }
  catch (e: Throwable) {
    GradlePluginsData.emptyData
  }

  override fun asyncRefresh() = Unit
}
