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
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val CONNECT_TIMEOUT = 5.seconds
private val READ_TIMEOUT = 2.minutes

private fun Path.isFresh(age: Duration) =
  exists() && System.currentTimeMillis() - getLastModifiedTime().toMillis() < age.inWholeMilliseconds

/** Read-through, on-disk cache of downloaded files. */
@Service(Service.Level.PROJECT)
class UrlFileCache : Disposable {
  private val files = mutableMapOf<String, Path>()
  private val lastModified = mutableMapOf<String, String>()
  private val eTags = mutableMapOf<String, String>()
  private val tmpDir = createTempDirectory()

  /**
   * Downloads a file at the given [url] and returns a [Path] to the downloaded file.
   *
   * If a [transform] is provided, this transform is applied to the file before writing to disk. If
   * the file is already downloaded, not more than [maxFileAge] old, or the server indicates the
   * file has not changed, the cached copy will be provided and the [transform] will NOT be
   * re-applied.
   */
  fun get(
    url: String,
    maxFileAge: Duration = Duration.ZERO,
    indicator: ProgressIndicator? = null,
    transform: ((InputStream) -> InputStream)? = null,
  ): Path {
    indicator?.isIndeterminate = true
    indicator?.text = "Checking cached downloads"
    // Check the cache first
    val existing = files[url]?.also { if (it.isFresh(maxFileAge)) return it }

    indicator?.text = "Downloading from ${URL(url).host}"
    val file: Path =
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
            if (existing != null && existing.exists()) return@connect existing
            throw HttpRequests.HttpStatusException(
              "Received NOT_MODIFIED (304) but nothing in the cache.",
              304,
              url,
            )
          }
          val newFile = createTempFile(tmpDir)
          try {
            request.saveToFile(newFile, indicator)
          } catch (e: IOException) {
            newFile.deleteIfExists()
            throw e
          }
          request.connection.getHeaderField("Last-Modified").let {
            if (it != null) lastModified[url] = it else lastModified.remove(url)
          }
          request.connection.getHeaderField("ETag").let {
            if (it != null) eTags[url] = it else eTags.remove(url)
          }
          return@connect newFile
        }
        .transform(files[url], transform)

    if (existing != file) existing?.deleteIfExists()
    files[url] = file
    return file
  }

  private fun Path.transform(cachedPath: Path?, transform: ((InputStream) -> InputStream)?): Path {
    // Don't bother if there is no transform or if we want to serve the cached file.
    if (transform == null || cachedPath == this) return this
    val transformedPath = createTempFile(tmpDir)
    try {
      Files.newInputStream(this).use {
        Files.copy(transform(it), transformedPath, StandardCopyOption.REPLACE_EXISTING)
      }
    } catch (e: Exception) {
      transformedPath.deleteIfExists()
      throw e
    }
    cachedPath?.deleteIfExists()
    deleteIfExists()
    return transformedPath
  }

  override fun dispose() {
    tmpDir.toFile().deleteRecursively()
  }

  companion object {
    @JvmStatic fun getInstance(project: Project): UrlFileCache = project.service()
  }
}
