/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.serverflags

import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.idea.ServerFlagData
import com.android.tools.idea.ServerFlagList
import com.google.common.hash.Hashing
import com.google.common.io.ByteStreams
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Path
import kotlin.math.abs


const val FILE_NAME = "serverflaglist.protobuf"
const val DIRECTORY_PREFIX = "serverflags"
const val DEFAULT_BASE_URL = "https://dl.google.com/android/studio/server_flags/"
const val BASE_URL_OVERRIDE_KEY = "studio.server.flags.baseurl.override"
const val VERSION_OVERRIDE_KEY = "studio.server.flags.version.override"
const val ENABLED_OVERRIDE_KEY = "studio.server.flags.enabled.override"

/**
 * ServerFlagInitializer initializes the ServerFlagService.instance field.
 * It will try to download the protobuf file for the current version of Android Studio
 * from the specified URL. If it succeeds it will save the file to a local path, then initialize the service.
 * If the download fails, it will use the last successful download to initialize the service
 */
class ServerFlagInitializer {
  companion object {
    @JvmStatic
    fun initializeService() {
      val baseUrl = System.getProperty(BASE_URL_OVERRIDE_KEY, DEFAULT_BASE_URL)
      val localPath = File(PathManager.getSystemPath()).toPath().resolve(DIRECTORY_PREFIX)
      val version = System.getProperty(VERSION_OVERRIDE_KEY, ApplicationInfo.getInstance().versionString)
      val experiments = System.getProperty(ENABLED_OVERRIDE_KEY)?.split(',') ?: emptyList()

      initializeService(baseUrl, localPath, version, experiments)

      val logger = Logger.getInstance(ServerFlagInitializer::class.java)
      val names = ServerFlagService.instance.names
      val string = names.joinToString()
      logger.info("Enabled server flags: $string")
    }

    /**
     * Initialize the server flag service
     * @param baseUrl: The base url where the download files are located.
     * @param localPath: The local directory to store the most recent download.
     * @param version: The current version of Android Studio. This is used to construct the full paths from the first two parameters.
     * @param experiments: An optional set of experiment names to be enabled. If empty, the percentEnabled field will determine whether
     * a given flag is enabled.
     */
    @JvmStatic
    fun initializeService(baseUrl: String, localPath: Path, version: String, experiments: Collection<String>) {
      val localFilePath = localPath.resolve(version).resolve(FILE_NAME)
      val url = buildUrl(baseUrl, version)

      val serverFlagList = url?.let { downloadServerFlagList(it, localFilePath) } ?: loadLocalFlagList(localFilePath)
      val configurationVersion = serverFlagList?.configurationVersion ?: -1
      val list = serverFlagList?.serverFlagsList ?: emptyList()

      val filter = if (experiments.isEmpty()) {
        { flag: ServerFlagData -> flag.isEnabled }
      }
      else {
        { flag: ServerFlagData -> experiments.contains(flag.name) }
      }

      val map = list.filter(filter).map { it.name to it.serverFlag }.toMap()
      ServerFlagService.instance = ServerFlagServiceImpl(configurationVersion, map)
    }

    private fun downloadServerFlagList(url: URL, localFilePath: Path): ServerFlagList? {
      val tempFile = downloadFile(url) ?: return null
      try {
        val serverFlagList = unmarshalFlagList(tempFile)
        tempFile.copyTo(localFilePath.toFile(), true)
        return serverFlagList
      }
      finally {
        try {
          tempFile.delete()
        }
        catch (e: IOException) {
        }
      }
    }

    private fun buildUrl(baseUrl: String, version: String): URL? {
      try {
        return URL("$baseUrl/$version/$FILE_NAME")
      }
      catch (e: MalformedURLException) {
        return null
      }
    }

    private fun downloadFile(url: URL): File? {
      try {
        val tempFile = File.createTempFile(DIRECTORY_PREFIX, "")
        url.openStream().use { inputStream ->
          tempFile.outputStream().use { outputStream ->
            ByteStreams.copy(inputStream, outputStream)
          }
        }
        return tempFile
      }
      catch (e: IOException) {
        return null
      }
    }

    private fun loadLocalFlagList(localFilePath: Path): ServerFlagList? {
      try {
        return unmarshalFlagList(localFilePath.toFile())
      }
      catch (e: IOException) {
        return null
      }
    }

    private fun unmarshalFlagList(file: File): ServerFlagList = file.inputStream().use { ServerFlagList.parseFrom(it) }
  }
}

private val ServerFlagData.isEnabled: Boolean
  get() {
    val key = AnalyticsSettings.userId + this.name
    val hash = Hashing.farmHashFingerprint64().hashString(key, Charsets.UTF_8)
    return (abs(hash.asLong()) % 100).toInt() < this.serverFlag.percentEnabled
  }

private val ApplicationInfo.versionString: String
  get() {
    val major = majorVersion ?: return ""
    val minor = minorVersion ?: return ""
    val micro = microVersion ?: return ""
    val patch = patchVersion ?: return ""
    return "$major.$minor.$micro.$patch"
  }