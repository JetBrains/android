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
package com.android.tools.idea.downloads

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import java.io.InputStream
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
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant

private val NEGATIVE_INFINITY = Duration.INFINITE * -1

/**
 * Abstract read-through, on-disk cache of remotely accessed files. Implementations need to provide
 * [fetchAndFilterLocked] which actually does the work of fetching the data.
 */
abstract class RemoteFileCache(
  private val coroutineScope: CoroutineScope,
  private val ioDispatcher: CoroutineDispatcher,
  private val timeSource: TimeSource,
  private val clock: Clock,
) : Disposable {

  private val files = mutableMapOf<String, Path>()
  private val tmpDir = createTempDirectory()
  private val mutex = Mutex()

  /**
   * Calls [getWithStats] but does not include a [FetchStats] in the return value and does not wrap
   * exceptions.
   */
  fun get(
    identifier: String,
    maxFileAge: Duration = NEGATIVE_INFINITY, // By default, no caching at all, EVER!
    indicator: ProgressIndicator? = null,
    transform: ((InputStream) -> InputStream)? = null,
  ): Deferred<Path> =
    coroutineScope.async(ioDispatcher, CoroutineStart.UNDISPATCHED) {
      try {
        doGet(identifier, maxFileAge, indicator, transform).first
      } catch (e: RemoteFileCacheException) {
        throw e.cause
      }
    }

  /**
   * Downloads a file with the given [identifier] and returns a [Deferred] for the [Path] to the
   * downloaded file.
   *
   * If a [transform] is provided, this transform is applied to the file before writing to disk. If
   * the file is already downloaded, not more than [maxFileAge] old, or the server indicates the
   * file has not changed, the cached copy will be provided and the [transform] will NOT be
   * re-applied. If an [Exception] is thrown, it will be wrapped with [RemoteFileCacheException],
   * which holds a [FetchStats] object.
   */
  fun getWithStats(
    identifier: String,
    maxFileAge: Duration = NEGATIVE_INFINITY, // By default, no caching at all, EVER!
    indicator: ProgressIndicator? = null,
    transform: ((InputStream) -> InputStream)? = null,
  ): Deferred<Pair<Path, FetchStats>> =
    coroutineScope.async(ioDispatcher, CoroutineStart.UNDISPATCHED) {
      doGet(identifier, maxFileAge, indicator, transform)
    }

  /** Actually does the work to get the value (and build the [FetchStats]). */
  private suspend fun doGet(
    identifier: String,
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
        files[identifier]?.also {
          if (it.isFresh(maxFileAge)) {
            return@withLock it to FetchStats(start.elapsedNow(), cacheHit = true)
          }
        }
      // Otherwise yield onto the ioDispatcher and suspend.
      yield()
      fetchAndFilterLocked(existing, identifier, indicator, start)
        .apply {
          require(startsWith(tmpDir)) { "Can only return Paths created with getNewWritablePath()!" }
        }
        .transform(files[identifier], transform, start)
        .also {
          if (existing != it.first) {
            existing?.deleteIfExists()
            files[identifier] = it.first
          }
        }
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

  class RemoteFileCacheException(val fetchStats: FetchStats, private val delegate: Exception) :
    Exception() {
    override val cause: Exception = delegate
    override val message = delegate.message
  }

  /**
   * Actually fetches the file, returning a [Path] to the file. The returned [Path] must be obtained
   * via a call to [getNewWritablePath]. This method can also return [existing], if the file has not
   * been modified and did not need to be fetched anew.
   */
  protected abstract fun fetchAndFilterLocked(
    existing: Path?,
    identifier: String,
    indicator: ProgressIndicator?,
    start: TimeMark,
  ): Path

  /** Gets a new [Path] where we can store the result of fetching the file. */
  protected fun getNewWritablePath() = createTempFile(tmpDir)

  private fun Path.transform(
    cachedPath: Path?,
    transform: ((InputStream) -> InputStream)?,
    start: TimeMark,
  ): Pair<Path, FetchStats> {
    // Don't bother with the transform if we want to serve the cached file. This means we found out
    // authoritatively the file wasn't modified (e.g. HTTP 403) and despite being stale, we can
    // still serve it.
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
        val transformedPath = getNewWritablePath()
        try {
          Files.newInputStream(this).use {
            Files.copy(transform(it), transformedPath, StandardCopyOption.REPLACE_EXISTING)
          }
        } catch (e: Exception) {
          transformedPath.deleteIfExists()
          // Nothing was cached because we threw.
          val stats = FetchStats(start.elapsedNow(), numBytesFetched = numBytesFetched)
          throw RemoteFileCacheException(stats, e)
        }
        cachedPath?.deleteIfExists()
        // Only delete the receiver if it's managed by us (in our temporary directory).
        if (startsWith(tmpDir)) deleteIfExists()
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

  private fun Path.isFresh(maxAge: Duration): Boolean {
    if (!exists()) return false
    val age = clock.now() - getLastModifiedTime().toInstant().toKotlinInstant()
    return age < maxAge
  }

  override fun dispose() {
    tmpDir.toFile().deleteRecursively()
  }
}
