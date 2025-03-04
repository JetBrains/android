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
package com.android.tools.idea.downloads

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import org.jetbrains.annotations.TestOnly
import java.io.InputStream
import kotlin.time.Duration

private val CONNECT_TIMEOUT = 5.seconds
private val READ_TIMEOUT = 2.minutes

/** Read-through, on-disk cache of downloaded files. */
@Service(Service.Level.PROJECT)
class UrlFileCache
@TestOnly
constructor(
  coroutineScope: CoroutineScope,
  ioDispatcher: CoroutineDispatcher,
  timeSource: TimeSource,
  clock: Clock,
) : RemoteFileCache<UrlFileCache.UrlWithHeaders>(coroutineScope, ioDispatcher, timeSource, clock) {
  constructor(
    coroutineScope: CoroutineScope
  ) : this(coroutineScope, Dispatchers.IO, TimeSource.Monotonic, Clock.System)

  private val lastModified = mutableMapOf<UrlWithHeaders, String>()
  private val eTags = mutableMapOf<UrlWithHeaders, String>()

  override fun fetchAndFilterLocked(
    existing: Path?,
    identifier: UrlWithHeaders,
    indicator: ProgressIndicator?,
    start: TimeMark,
  ): Path {
    val url = URL(identifier.url) // Will throw if it doesn't parse
    indicator?.text = "Downloading from ${url.host}"
    return HttpRequests.request(identifier.url)
      .connectTimeout(CONNECT_TIMEOUT.inWholeMilliseconds.toInt())
      .readTimeout(READ_TIMEOUT.inWholeMilliseconds.toInt())
      .tuner { connection ->
        for ((key, value) in identifier.headers) {
          connection.setRequestProperty(key, value)
        }
        lastModified[identifier]?.let { connection.setRequestProperty("If-Modified-Since", it) }
        eTags[identifier]?.let { connection.setRequestProperty("If-None-Match", it) }
      }
      .connect { request ->
        val responseCode = (request.connection as HttpURLConnection).responseCode
        if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
          if (existing != null && existing.exists()) {
            return@connect existing
          }
          throw RemoteFileCacheException(
            FetchStats(start.elapsedNow(), success = false, notModified = true),
            HttpRequests.HttpStatusException(
              "Received NOT_MODIFIED (304) but nothing in the cache.",
              304,
              identifier.url,
            ),
          )
        }
        val newFile = getNewWritablePath()
        try {
          request.saveToFile(newFile, indicator)
        } catch (e: IOException) {
          newFile.deleteIfExists()
          throw RemoteFileCacheException(FetchStats(start.elapsedNow(), success = false), e)
        }
        request.connection.getHeaderField("Last-Modified").let {
          if (it != null) lastModified[identifier] = it else lastModified.remove(identifier)
        }
        request.connection.getHeaderField("ETag").let {
          if (it != null) eTags[identifier] = it else eTags.remove(identifier)
        }
        return@connect newFile
      }
  }

  data class UrlWithHeaders(val url: String, val headers: Map<String, String> = emptyMap())

  companion object {
    @JvmStatic fun getInstance(project: Project): UrlFileCache = project.service()
  }
}
