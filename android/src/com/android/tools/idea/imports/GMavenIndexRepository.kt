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
package com.android.tools.idea.imports

import com.android.annotations.concurrency.Slow
import com.android.io.CancellableFileIo
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.EventDispatcher
import com.intellij.util.application
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.outputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.EventListener
import java.util.Locale
import java.util.Properties
import kotlin.io.path.exists
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly

/** Network connection timeout in milliseconds. */
private const val NETWORK_TIMEOUT_MILLIS = 3000

/** Network retry initial delay in milliseconds. */
private val NETWORK_RETRY_INITIAL_DELAY = 1.hours

/** Network maximum retry times. */
private const val NETWORK_MAXIMUM_RETRY_TIMES = 4

/** Network retry delay factor. */
private const val NETWORK_RETRY_DELAY_FACTOR = 2

/** Scheduled refreshment interval for local disk cache. */
private val REFRESH_INTERVAL = 1.days

private const val GZ_EXT = ".gz"

/** Key used in property list to find ETag value. */
private const val ETAG_KEY = "etag"

private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

/** Key used in cache directories to locate the gmaven.index network cache. */
private const val GMAVEN_INDEX_CACHE_DIR_KEY = "gmaven.index"

/**
 * A repository provides Maven class registry generated from loading local disk cache, which is
 * actively refreshed from network request on GMaven indices on [baseUrl]/[RELATIVE_PATH], on a
 * scheduled basis (daily).
 *
 * The underlying [lastComputedMavenClassRegistry] is for storing the last known value for instant
 * query. The freshness is guaranteed by the [scheduler].
 */
@Service
class GMavenIndexRepository
@TestOnly
internal constructor(
  private val baseUrl: String,
  private val cacheDir: Path,
  coroutineScope: CoroutineScope,
  coroutineDispatcher: CoroutineDispatcher,
) {

  constructor(
    coroutineScope: CoroutineScope
  ) : this(
    BASE_URL,
    Paths.get(PathManager.getSystemPath(), GMAVEN_INDEX_CACHE_DIR_KEY),
    coroutineScope,
    Dispatchers.Default,
  )

  private val relativeCachePath =
    if (RELATIVE_PATH.endsWith(GZ_EXT)) RELATIVE_PATH.dropLast(GZ_EXT.length) else RELATIVE_PATH

  private val listeners = EventDispatcher.create(GMavenIndexRepositoryListener::class.java)

  init {
    coroutineScope.launch(coroutineDispatcher) {
      // Schedules to refresh local disk cache on a daily basis (based on refreshInterval).
      while (true) {
        launch {
          refreshWithRetryStrategy(
            url = "$baseUrl/$RELATIVE_PATH",
            retryDelay = NETWORK_RETRY_INITIAL_DELAY,
            remainingAttempts = NETWORK_MAXIMUM_RETRY_TIMES,
          )
        }

        delay(REFRESH_INTERVAL)
      }
    }
  }

  internal fun addListener(listener: GMavenIndexRepositoryListener, parentDisposable: Disposable) {
    listeners.addListener(listener, parentDisposable)
  }

  /**
   * Refreshes both local disk cache and [lastComputedMavenClassRegistry] if exists with retry
   * strategy.
   */
  @Slow
  private suspend fun refreshWithRetryStrategy(
    url: String,
    retryDelay: Duration,
    remainingAttempts: Int,
  ) {
    val status = refresh(url)
    if (status != RefreshStatus.RETRY || remainingAttempts <= 1) return

    val scheduledTime =
      DATE_FORMAT.format(System.currentTimeMillis() + retryDelay.inWholeMilliseconds)
    thisLogger().info("Scheduled to retry refreshing ${this.javaClass.name} after $scheduledTime.")
    delay(retryDelay)

    refreshWithRetryStrategy(url, retryDelay * NETWORK_RETRY_DELAY_FACTOR, remainingAttempts - 1)
  }

  /** Refreshes both local disk cache and [lastComputedMavenClassRegistry] if exists. */
  @Slow
  private fun refresh(url: String): RefreshStatus {
    val status = refreshDiskCache(url)
    if (status == RefreshStatus.UPDATED) listeners.multicaster.onIndexUpdated()

    return status
  }

  /**
   * Loads the index from the local disk cache if possible. Or it falls back to the built-in index.
   */
  fun loadIndexFromDisk(): InputStream {
    val file = cacheDir.resolve(relativeCachePath)
    try {
      return CancellableFileIo.newInputStream(file)
    } catch (ignore: NoSuchFileException) {}

    // Fallback: Builtin index, used for offline scenarios etc.
    return readDefaultData()
  }

  /**
   * Returns [RefreshStatus.UPDATED] if the disk cache is successfully updated.
   *
   * Or returns [RefreshStatus.UNCHANGED] if it's already up to date. Or returns
   * [RefreshStatus.RETRY] if might be worth retrying after a while. Or returns
   * [RefreshStatus.ERROR] when errors occur.
   *
   * When requesting content, we explicitly store the corresponding ETag values, in a `.properties`
   * file, as a sibling to the local cached content. So, such cached ETag value can be an identifier
   * to determine if there's new changes since the last request, and `304 Not Modified Response` is
   * the expected response if we've already gotten an up to date cache.
   */
  @Slow
  private fun refreshDiskCache(url: String): RefreshStatus {
    try {
      val cacheFile = cacheDir.resolve(relativeCachePath)
      val eTagForCacheFile: String? =
        if (cacheFile.exists()) loadETag(getETagFile(cacheFile)) else null

      val valueWithETag = readUrlData(url, NETWORK_TIMEOUT_MILLIS, eTagForCacheFile)
      if (valueWithETag == null) {
        thisLogger().info("Kept the old disk cache with an old ETag header: $eTagForCacheFile.")
        return RefreshStatus.UNCHANGED
      }

      saveCache(valueWithETag.data, cacheFile)
      saveETag(getETagFile(cacheFile), valueWithETag.eTag)
      thisLogger()
        .info("Refreshed disk cache successfully with a new ETag header: ${valueWithETag.eTag}.")
      return RefreshStatus.UPDATED
    } catch (e: Exception) {
      thisLogger().info("Failed to refresh local disk cache:\n$e")

      return if (isRetryableError(e)) RefreshStatus.RETRY else RefreshStatus.ERROR
    }
  }

  /** Returns true if a retry should be scheduled for later. */
  private fun isRetryableError(e: Exception): Boolean {
    if (e !is IOException) return false

    if (e is SocketTimeoutException || e is UnknownHostException) return true

    val responseCode = (e as? HttpRequests.HttpStatusException)?.statusCode ?: return false
    return responseCode >= 400
  }

  /** Reads the given query URL, with the given time out, and returns the bytes found. */
  @Slow
  private fun readUrlData(url: String, timeoutMillis: Int, eTag: String?): ValueWithETag? {
    return HttpRequests.request(URL(url).toExternalForm())
      .connectTimeout(timeoutMillis)
      .readTimeout(timeoutMillis)
      .tuner { connection -> eTag?.let { connection.setRequestProperty("If-None-Match", it) } }
      .connect { request ->
        val eTagField = request.connection.getHeaderField("ETag")
        val responseCode = (request.connection as HttpURLConnection).responseCode
        if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
          thisLogger()
            .info("HTTP not modified since the last request for URL: $url (etag: $eTagField).")
          return@connect null
        }

        val bytes = request.readBytes(null)
        return@connect ValueWithETag(bytes, eTagField)
      }
  }

  private fun readDefaultData(): InputStream {
    return GMavenIndexRepository::class
      .java
      .classLoader
      .getResourceAsStream("gmavenIndex/$OFFLINE_NAME.json")
      ?: throw Error("Unexpected error when reading resource file: $OFFLINE_NAME.json.")
  }

  private fun saveCache(data: ByteArray, cacheFile: Path) {
    Files.createDirectories(cacheFile.parent!!)
    val tempFile = Files.createTempFile(cacheFile.parent, "${cacheFile.fileName}", ".tmp")

    try {
      ungzip(data).let {
        // Writes the decompressed bytes of the data to the temp file.
        Files.write(tempFile, it)
      }
      Files.move(
        tempFile,
        cacheFile,
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE,
      )
    } catch (e: Exception) {
      Files.deleteIfExists(tempFile)
      throw e
    }
  }

  private fun loadETag(file: Path): String? {
    return try {
      val properties =
        Properties().apply {
          CancellableFileIo.newInputStream(file).use { inputStream -> this.load(inputStream) }
        }
      properties.getProperty(ETAG_KEY)
    } catch (e: Exception) {
      thisLogger().info("Error when loading ETag value:\n$e")
      null
    }
  }

  private fun saveETag(file: Path, eTag: String) {
    Properties().apply {
      setProperty(ETAG_KEY, eTag)
      store(file.outputStream(), "## Metadata for gmaven index cache. Do not modify.")
    }
  }

  private fun getETagFile(file: Path): Path {
    return file.resolveSibling("${file.fileName}.properties")
  }

  /** Status after the local disk cache being refreshed. */
  private enum class RefreshStatus {
    /** Content is updated to the latest. */
    UPDATED,

    /** No changes after refreshing. */
    UNCHANGED,

    /** Worth a retry after a while. */
    RETRY,

    /** Errors happen when refreshing. */
    ERROR,
  }

  private class ValueWithETag(val data: ByteArray, val eTag: String)

  companion object {
    fun getInstance(): GMavenIndexRepository = application.service()
  }
}

/** Listener for events related to the [GMavenIndexRepository] service. */
internal fun interface GMavenIndexRepositoryListener : EventListener {
  fun onIndexUpdated()
}

/**
 * Post-startup activity that kicks of the GMaven index refresh logic in [GMavenIndexRepository].
 */
class AutoRefresherForMavenClassRegistry : ProjectActivity {
  init {
    if (application.isUnitTestMode || application.isHeadlessEnvironment)
      throw ExtensionNotApplicableException.create()
  }

  override suspend fun execute(project: Project) {
    if (
      !IdeInfo.getInstance().isAndroidStudio && !IdeSdks.getInstance().hasConfiguredAndroidSdk()
    ) {
      // IDE must not hit network on startup
      return
    }

    // Start refresher in GMavenIndexRepository at project start-up.
    GMavenIndexRepository.getInstance()
  }
}
