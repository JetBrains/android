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
package com.android.tools.idea.imports

import com.android.io.CancellableFileIo
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import com.sun.net.httpserver.HttpServer
import java.io.InputStream
import java.lang.Thread.sleep
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Test

private const val LOCALHOST = "127.0.0.1"
private const val CONTEXT_PATH = "/v0.1/classes-v0.1.json.gz"

class GMavenIndexRepositoryTest {
  private val cacheDir = createInMemoryFileSystemAndFolder("tempCacheDir")
  private val cacheFile = cacheDir.resolve("v0.1/classes-v0.1.json")

  private val server =
    HttpServer.create().apply {
      bind(InetSocketAddress(LOCALHOST, 0), 0)
      start()
    }
  private val url = "http://$LOCALHOST:${server.address.port}"

  private val testScheduler = TestCoroutineScheduler()
  private val testDispatcher = StandardTestDispatcher(testScheduler)
  private val testScope = TestScope(testDispatcher)

  private val gMavenIndexRepository =
    GMavenIndexRepository(url, cacheDir, testScope, testDispatcher)

  @After
  fun tearDown() {
    server.stop(0)
  }

  @Test
  fun testRefreshDiskCache_hasModificationSinceLast() {
    createContext(
      path = CONTEXT_PATH,
      content = "This is for unit test",
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_OK,
    )

    testScheduler.advanceTimeBy(5.hours)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText())
      .isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")

    cleanContext(CONTEXT_PATH)
    createContext(
      path = CONTEXT_PATH,
      content = "[updated]This is for unit test",
      eTag = "84509f",
      rCode = HttpURLConnection.HTTP_OK,
    )

    testScheduler.advanceTimeBy(1.days)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText())
      .isEqualTo("[updated]This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("84509f")
  }

  @Test
  fun testRefreshDiskCache_noModificationSinceLast() {
    val gMavenIndexRepository = GMavenIndexRepository(url, cacheDir, testScope, testDispatcher)
    createContext(
      path = CONTEXT_PATH,
      content = "This is for unit test",
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_OK,
    )

    testScheduler.advanceTimeBy(5.hours)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText())
      .isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")
    val lastModifiedTimestamp = CancellableFileIo.getLastModifiedTime(cacheFile)

    cleanContext(CONTEXT_PATH)
    createContext(
      path = CONTEXT_PATH,
      content = "This is for unit test", // Content here doesn't matter as
      // `HttpURLConnection.HTTP_NOT_MODIFIED`.
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_NOT_MODIFIED,
      rLen = "Not Modified".toByteCnt(),
    )

    testScheduler.advanceTimeBy(1.days)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText())
      .isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")
    assertThat(CancellableFileIo.getLastModifiedTime(cacheFile)).isEqualTo(lastModifiedTimestamp)
  }

  @Test
  fun testRefreshDiskCache_error() {
    createContext(
      path = CONTEXT_PATH,
      content = "This is for unit test",
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_OK,
    )

    testScheduler.advanceTimeBy(1.hours)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText())
      .isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")

    cleanContext(CONTEXT_PATH)
    createContext(
      path = CONTEXT_PATH,
      content = "[updated]This is for unit test",
      eTag = "84509f",
      rCode = HttpURLConnection.HTTP_CLIENT_TIMEOUT,
      rLen = "Http client timeout".toByteCnt(),
    )

    testScheduler.advanceTimeBy(1.days)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText())
      .isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")
  }

  @Test
  fun testDiskCacheWithNoETag() {
    createContext(
      path = CONTEXT_PATH,
      content = "This is for unit test",
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_OK,
    )

    testScheduler.advanceTimeBy(5.hours)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText())
      .isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")

    // Delete the sibling etag cache file.
    assertThat(Files.deleteIfExists(getETagFile(cacheFile))).isTrue()

    testScheduler.advanceTimeBy(1.days)
    assertThat(getETagFile(cacheFile)).exists()
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText())
      .isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")
  }

  @Test
  fun testNoDiskCacheWithETag() {
    createContext(
      path = CONTEXT_PATH,
      content = "This is for unit test",
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_OK,
    )

    testScheduler.advanceTimeBy(5.hours)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText())
      .isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")

    cleanContext(CONTEXT_PATH)
    createContext(
      path = CONTEXT_PATH,
      content = "This is for unit test",
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_OK,
    )

    // Delete the cached file, but leave the sibling etag cache file.
    assertThat(Files.deleteIfExists(cacheFile)).isTrue()

    testScheduler.advanceTimeBy(1.days)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText())
      .isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")
  }

  @Test
  fun testRefreshDiskCache_withRetries_succeeded_httpClientTimeout() {
    createContext(
      path = CONTEXT_PATH,
      content = "This is for unit test",
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_OK,
    )

    testScheduler.advanceTimeBy(1.hours)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText())
      .isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")

    cleanContext(CONTEXT_PATH)
    createContext(
      path = CONTEXT_PATH,
      content = "[updated]This is for unit test",
      eTag = "84509f",
      rCode = HttpURLConnection.HTTP_CLIENT_TIMEOUT,
      rLen = "Http client timeout".toByteCnt(),
    )

    testScheduler.advanceTimeBy(1.days)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText())
      .isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")

    testScheduler.advanceTimeBy(3.hours)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText())
      .isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")

    cleanContext(CONTEXT_PATH)
    createContext(
      path = CONTEXT_PATH,
      content = "[updated]This is for unit test",
      eTag = "84509f",
      rCode = HttpURLConnection.HTTP_OK,
    )

    // Refresh successfully at last.
    testScheduler.advanceTimeBy(8.hours)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText())
      .isEqualTo("[updated]This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("84509f")
  }

  @Test
  fun testRefreshDiskCache_withRetries_succeeded_socketTimeout() {
    createContext(
      path = CONTEXT_PATH,
      content = "This is for unit test",
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_OK,
    )

    testScheduler.advanceTimeBy(1.hours)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText())
      .isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")

    cleanContext(CONTEXT_PATH)
    // Mimic timeout scenario.
    synchronized(server) {
      server.createContext(CONTEXT_PATH) { exchange ->
        sleep(3100)
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
        exchange.close()
      }
    }

    testScheduler.advanceTimeBy(1.days)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText())
      .isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")

    cleanContext(CONTEXT_PATH)
    createContext(
      path = CONTEXT_PATH,
      content = "[updated]This is for unit test",
      eTag = "84509f",
      rCode = HttpURLConnection.HTTP_OK,
    )

    // Refresh successfully at the second retry.
    testScheduler.advanceTimeBy(2.hours)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText())
      .isEqualTo("[updated]This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("84509f")
  }

  @Test
  fun testRefreshDiskCache_withRetries_failed() {
    createContext(
      path = CONTEXT_PATH,
      content = "This is for unit test",
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_OK,
    )

    testScheduler.advanceTimeBy(1.hours)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText())
      .isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")

    cleanContext(CONTEXT_PATH)

    testScheduler.advanceTimeBy(1.days)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText())
      .isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")

    // Refresh failed after maximum attempts.
    testScheduler.advanceTimeBy(16.hours)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText())
      .isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")
  }

  private fun createContext(
    path: String,
    content: String,
    eTag: String,
    rCode: Int,
    rLen: Long = 0,
  ) {
    synchronized(server) {
      server.createContext(path) { exchange ->
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.set("ETag", eTag)
        exchange.sendResponseHeaders(rCode, rLen)
        exchange.responseBody.write(gzip(content.toByteArray(UTF_8)))
        exchange.close()
      }
    }
  }

  private fun cleanContext(path: String) {
    synchronized(server) { server.removeContext(path) }
  }

  private fun InputStream.toText(): String {
    return bufferedReader(UTF_8).use { it.readText() }
  }

  private fun String.toByteCnt(): Long {
    return toByteArray(UTF_8).size.toLong()
  }

  private fun getETagForFile(file: Path): String? {
    val eTagFile = getETagFile(file)
    return try {
      val properties =
        Properties().apply {
          CancellableFileIo.newInputStream(eTagFile).use { inputStream -> this.load(inputStream) }
        }
      properties.getProperty("etag")
    } catch (ignore: Exception) {
      null
    }
  }

  private fun getETagFile(file: Path): Path {
    return file.resolveSibling("${file.fileName}.properties")
  }
}
