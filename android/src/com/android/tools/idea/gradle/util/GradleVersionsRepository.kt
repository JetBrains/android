/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.ide.common.repository.NetworkCache
import com.android.tools.idea.ui.GuiTestingService
import com.google.common.annotations.VisibleForTesting
import com.google.common.io.ByteStreams
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.PathUtil
import com.intellij.util.net.HttpConfigurable
import org.jetbrains.ide.PooledThreadExecutor
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

const val GRADLE_VERSIONS_URL = "https://services.gradle.org/versions/all"
const val GRADLE_VERSIONS_CACHE_DIR_KEY = "gradle.versions"


object GradleVersionsRepository : NetworkCache(
  GRADLE_VERSIONS_URL, GRADLE_VERSIONS_CACHE_DIR_KEY, getCacheDir(), cacheExpiryHours = 24) {

  override fun readUrlData(url: String, timeout: Int): ByteArray? {
    val query = URL(url)
    val connection = HttpConfigurable.getInstance().openConnection(query.toExternalForm())
    if (timeout > 0) {
      connection.connectTimeout = timeout
      connection.readTimeout = timeout
    }
    return try {
      val stream = connection.getInputStream() ?: return null
      ByteStreams.toByteArray(stream)
    }
    finally {
      (connection as? HttpURLConnection)?.disconnect()
    }
  }

  override fun readDefaultData(relative: String): InputStream? = null

  override fun error(throwable: Throwable, message: String?) =
    Logger.getInstance(GradleVersionsRepository::class.java).warn(message, throwable)

  fun getKnownVersionsFuture() : ListenableFuture<List<String>> =
    MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE).submit<List<String>> { getKnownVersions() }

  fun getKnownVersions() : List<String>? = findData("")?.use { parseResponse(it) }

  /**
   * Parse json response body into versions list.
   * Same order as in response maintained.
   * Snapshot versions are filtered out.
   */
  @VisibleForTesting
  fun parseResponse(response: InputStream): List<String> {
    //Response example can be found in test
    val result = ArrayList<String>()
    val jsonParser = JsonParser()

    val array = jsonParser.parse(response.bufferedReader()).getAsJsonArray()
    for (e in array) {
      val versionObject = e.getAsJsonObject()
      if (!versionObject.get("snapshot").getAsBoolean()) {
        result.add(versionObject.get("version").getAsString())
      }
    }
    return result
  }
}

private fun getCacheDir(): File? =
  if (ApplicationManager.getApplication() == null ||
      ApplicationManager.getApplication().isUnitTestMode ||
      GuiTestingService.getInstance().isGuiTestingMode)
    //Test mode
    null
  else
    File(PathUtil.getCanonicalPath(PathManager.getSystemPath()), GRADLE_VERSIONS_CACHE_DIR_KEY)
