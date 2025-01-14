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

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.setLastModifiedTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.annotations.TestOnly

typealias FetchStatsListener = (UrlFileCache.FetchStats) -> Unit

private val CONNECT_TIMEOUT = 5.seconds
private val READ_TIMEOUT = 2.minutes
private val NEGATIVE_INFINITY = Duration.INFINITE * -1

/** Read-through, on-disk cache of downloaded files. */
@Service(Service.Level.PROJECT)
class UrlFileCache
@TestOnly
constructor(
  private val coroutineScope: CoroutineScope,
  private val ioDispatcher: CoroutineDispatcher,
  private val timeSource: TimeSource,
  private val clock: Clock,
) : Disposable {
  constructor(
    coroutineScope: CoroutineScope
  ) : this(coroutineScope, Dispatchers.IO, TimeSource.Monotonic, Clock.System)

  private val files = mutableMapOf<String, Path>()
  private val lastModified = mutableMapOf<String, String>()
  private val eTags = mutableMapOf<String, String>()
  private val tmpDir = createTempDirectory()
  private val mutex = Mutex()

  /**
   * Calls [getWithStats] but does not include a [FetchStats] in the return value and does not wrap
   * exceptions.
   */
  fun get(
    url: String,
    maxFileAge: Duration = NEGATIVE_INFINITY, // By default, no caching at all, EVER!
    indicator: ProgressIndicator? = null,
    transform: ((InputStream) -> InputStream)? = null,
  ): Deferred<Path> =
    coroutineScope.async(ioDispatcher, CoroutineStart.UNDISPATCHED) {
      try {
        doGet(url, maxFileAge, indicator, transform).first
      } catch (e: UrlFileCacheException) {
        throw e.cause
      }
    }

  /**
   * Downloads a file at the given [url] and returns a [Deferred] for the [Path] to the downloaded
   * file.
   *
   * If a [transform] is provided, this transform is applied to the file before writing to disk. If
   * the file is already downloaded, not more than [maxFileAge] old, or the server indicates the
   * file has not changed, the cached copy will be provided and the [transform] will NOT be
   * re-applied. If an [Exception] is thrown, it will be wrapped with [UrlFileCacheException], which
   * holds a [FetchStats] object.
   */
  fun getWithStats(
    url: String,
    maxFileAge: Duration = NEGATIVE_INFINITY, // By default, no caching at all, EVER!
    indicator: ProgressIndicator? = null,
    transform: ((InputStream) -> InputStream)? = null,
  ): Deferred<Pair<Path, FetchStats>> =
    coroutineScope.async(ioDispatcher, CoroutineStart.UNDISPATCHED) {
      doGet(url, maxFileAge, indicator, transform)
    }

  /** Actually does the work to get the value (and build the [FetchStats]). */
  private suspend fun doGet(
    url: String,
    maxFileAge: Duration,
    indicator: ProgressIndicator?,
    transform: ((InputStream) -> InputStream)?,
  ): Pair<Path, FetchStats> {
    val start = timeSource.markNow()
    indicator?.isIndeterminate = true
    indicator?.text = "Checking cached downloads"

    return mutex.withLock {
      // Check the cache first.
      val existing =
        files[url]?.also {
          if (it.isFresh(maxFileAge)) {
            return@withLock it to FetchStats(start.elapsedNow(), cacheHit = true)
          }
        }
      // Otherwise yield onto the ioDispatcher and suspend.
      yield()
      fetchAndFilterUrlLocked(existing, url, indicator, transform, start)
    }
  }

  /** Statistics about an individual [get] operation. */
  data class FetchStats(
    /** How long the fetch took. */
    val fetchDuration: Duration,
    /** Whether the fetch succeeded. */
    val success: Boolean = true,
    /** Whether the fetch returned a cached value WITHOUT going to the server. */
    val cacheHit: Boolean = false,
    /** Whether the server said the file hadn't been modified. */
    val notModified: Boolean = false,
    /** How many bytes of content were fetched from the server for this [get] operation. */
    val numBytesFetched: Long = 0,
    /** How many bytes of content were stored in the cache as a result of this [get] operation. */
    val numBytesCached: Long = 0,
  )

  class UrlFileCacheException(val fetchStats: FetchStats, private val delegate: Exception) :
    Exception() {
    override val cause: Exception = delegate
    override val message = delegate.message
  }

  private fun fetchAndFilterUrlLocked(
    existing: Path?,
    url: String,
    indicator: ProgressIndicator?,
    transform: ((InputStream) -> InputStream)?,
    start: TimeMark,
  ): Pair<Path, FetchStats> {
    indicator?.text = "Downloading from ${URL(url).host}"
    val fileAndStats: Pair<Path, FetchStats> =
      HttpRequests.request(url)
        .connectTimeout(CONNECT_TIMEOUT.inWholeMilliseconds.toInt())
        .readTimeout(READ_TIMEOUT.inWholeMilliseconds.toInt())
        .tuner { connection ->
          lastModified[url]?.let { connection.setRequestProperty("If-Modified-Since", it) }
          eTags[url]?.let { connection.setRequestProperty("If-None-Match", it) }
        }
        .connect { request ->
          val responseCode = (request.connection as HttpURLConnection).responseCode
          if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
            if (existing != null && existing.exists()) {
              return@connect existing
            }
            throw UrlFileCacheException(
              FetchStats(start.elapsedNow(), success = false, notModified = true),
              HttpRequests.HttpStatusException(
                "Received NOT_MODIFIED (304) but nothing in the cache.",
                304,
                url,
              ),
            )
          }
          val newFile = createTempFile(tmpDir)
          try {
            request.saveToFile(newFile, indicator)
          } catch (e: IOException) {
            newFile.deleteIfExists()
            throw UrlFileCacheException(FetchStats(start.elapsedNow(), success = false), e)
          }
          request.connection.getHeaderField("Last-Modified").let {
            if (it != null) lastModified[url] = it else lastModified.remove(url)
          }
          request.connection.getHeaderField("ETag").let {
            if (it != null) eTags[url] = it else eTags.remove(url)
          }
          return@connect newFile
        }
        .transform(files[url], transform, start)

    if (existing != fileAndStats.first) {
      existing?.deleteIfExists()
      files[url] = fileAndStats.first
    }
    return fileAndStats
  }

  private fun Path.transform(
    cachedPath: Path?,
    transform: ((InputStream) -> InputStream)?,
    start: TimeMark,
  ): Pair<Path, FetchStats> {
    // Don't bother with the transform if we want to serve the cached file. This is the "not
    // modified" case above.
    if (cachedPath == this) {
      // Update the last modified time, because we have talked to the server.
      setLastModifiedTime(FileTime.from(clock.now().toJavaInstant()))
      return this to FetchStats(start.elapsedNow(), notModified = true)
    }

    // Save this now before we possibly delete this file
    val numBytesFetched = fileSize()
    // If we have no transform, then the transformed path is just the current path.
    val pathToReturn =
      if (transform == null) this
      else {
        val transformedPath = createTempFile(tmpDir)
        try {
          Files.newInputStream(this).use {
            Files.copy(transform(it), transformedPath, StandardCopyOption.REPLACE_EXISTING)
          }
        } catch (e: Exception) {
          transformedPath.deleteIfExists()
          // Nothing was cached because we threw.
          val stats = FetchStats(start.elapsedNow(), numBytesFetched = numBytesFetched)
          throw UrlFileCacheException(stats, e)
        }
        cachedPath?.deleteIfExists()
        deleteIfExists()
        transformedPath
      }

    val stats =
      FetchStats(
        start.elapsedNow(),
        numBytesFetched = numBytesFetched,
        numBytesCached = pathToReturn.fileSize(),
      )
    // Set the last modified time using our injected clock.
    pathToReturn.setLastModifiedTime(FileTime.from(clock.now().toJavaInstant()))
    return pathToReturn to stats
  }

  private fun FetchStatsListener.callback(fetchStats: FetchStats) {
    coroutineScope.launch { invoke(fetchStats) }
  }

  private fun Path.isFresh(maxAge: Duration): Boolean {
    if (!exists()) return false
    val age = clock.now() - getLastModifiedTime().toInstant().toKotlinInstant()
    return age < maxAge
  }

  override fun dispose() {
    tmpDir.toFile().deleteRecursively()
  }

  companion object {
    @JvmStatic fun getInstance(project: Project): UrlFileCache = project.service()
  }
}
