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
import com.android.testutils.MockitoKt.mockStatic
import com.android.testutils.VirtualTimeScheduler
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.util.concurrency.AppExecutorUtil
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.MockedStatic
import java.io.InputStream
import java.lang.Thread.sleep
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit

class GMavenIndexRepositoryTest {
  private val LOCALHOST = "127.0.0.1"
  private val CONTEXT_PATH = "/v0.1/classes-v0.1.json.gz"
  private lateinit var server: HttpServer
  private lateinit var url: String
  private lateinit var cacheDir: Path
  private lateinit var cacheFile: Path
  private lateinit var virtualExecutor: VirtualTimeScheduler
  private lateinit var appExecutorUtilMock: MockedStatic<AppExecutorUtil>
  private lateinit var gMavenIndexRepository: GMavenIndexRepository

  @get:Rule
  val rule = ApplicationRule()

  @Before
  fun setUp() {
    // Mock AppExecutorUtil.
    virtualExecutor = VirtualTimeScheduler()
    appExecutorUtilMock = mockStatic()
    appExecutorUtilMock.`when`<Any> {
      AppExecutorUtil.createBoundedScheduledExecutorService("MavenClassRegistry Refresher", 1)
    }.thenReturn(virtualExecutor)
    // Create cache directory.
    cacheDir = createInMemoryFileSystemAndFolder("tempCacheDir")
    cacheFile = cacheDir.resolve("v0.1/classes-v0.1.json")

    // Set up server.
    server = HttpServer.create()
    with(server) {
      bind(InetSocketAddress(LOCALHOST, 0), 0)
      start()
      url = "http://$LOCALHOST:${address.port}"
    }

    // Create a new repository.
    gMavenIndexRepository = GMavenIndexRepository(url, cacheDir, Duration.ofDays(1))
  }

  @After
  fun tearDown() {
    Disposer.dispose(gMavenIndexRepository)
    server.stop(0)
    appExecutorUtilMock.close()
  }

  @Test
  fun testRefreshDiskCache_hasModificationSinceLast() {
    createContext(
      path = CONTEXT_PATH,
      content = "This is for unit test",
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_OK
    )

    virtualExecutor.advanceBy(5, TimeUnit.HOURS)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText()).isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")

    cleanContext(CONTEXT_PATH)
    createContext(
      path = CONTEXT_PATH,
      content = "[updated]This is for unit test",
      eTag = "84509f",
      rCode = HttpURLConnection.HTTP_OK
    )

    virtualExecutor.advanceBy(1, TimeUnit.DAYS)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText()).isEqualTo("[updated]This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("84509f")
  }

  @Test
  fun testRefreshDiskCache_noModificationSinceLast() {
    val gMavenIndexRepository = GMavenIndexRepository(url, cacheDir, Duration.ofDays(1))
    createContext(
      path = CONTEXT_PATH,
      content = "This is for unit test",
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_OK
    )

    virtualExecutor.advanceBy(5, TimeUnit.HOURS)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText()).isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")
    val lastModifiedTimestamp = CancellableFileIo.getLastModifiedTime(cacheFile)

    cleanContext(CONTEXT_PATH)
    createContext(
      path = CONTEXT_PATH,
      content = "This is for unit test", // Content here doesn't matter as `HttpURLConnection.HTTP_NOT_MODIFIED`.
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_NOT_MODIFIED,
      rLen = "Not Modified".toByteCnt()
    )

    virtualExecutor.advanceBy(1, TimeUnit.DAYS)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText()).isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")
    assertThat(CancellableFileIo.getLastModifiedTime(cacheFile)).isEqualTo(lastModifiedTimestamp)
  }

  @Test
  fun testRefreshDiskCache_error() {
    createContext(
      path = CONTEXT_PATH,
      content = "This is for unit test",
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_OK
    )

    virtualExecutor.advanceBy(1, TimeUnit.HOURS)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText()).isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")

    cleanContext(CONTEXT_PATH)
    createContext(
      path = CONTEXT_PATH,
      content = "[updated]This is for unit test",
      eTag = "84509f",
      rCode = HttpURLConnection.HTTP_CLIENT_TIMEOUT,
      rLen = "Http client timeout".toByteCnt()
    )

    virtualExecutor.advanceBy(1, TimeUnit.DAYS)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText()).isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")
  }

  @Test
  fun testDiskCacheWithNoETag() {
    createContext(
      path = CONTEXT_PATH,
      content = "This is for unit test",
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_OK
    )

    virtualExecutor.advanceBy(5, TimeUnit.HOURS)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText()).isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")

    // Delete the sibling etag cache file.
    assertThat(Files.deleteIfExists(getETagFile(cacheFile))).isTrue()

    virtualExecutor.advanceBy(1, TimeUnit.DAYS)
    assertThat(getETagFile(cacheFile)).exists()
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText()).isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")
  }

  @Test
  fun testNoDiskCacheWithETag() {
    createContext(
      path = CONTEXT_PATH,
      content = "This is for unit test",
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_OK
    )

    virtualExecutor.advanceBy(5, TimeUnit.HOURS)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText()).isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")

    cleanContext(CONTEXT_PATH)
    createContext(
      path = CONTEXT_PATH,
      content = "This is for unit test",
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_OK
    )

    // Delete the cached file, but leave the sibling etag cache file.
    assertThat(Files.deleteIfExists(cacheFile)).isTrue()

    virtualExecutor.advanceBy(1, TimeUnit.DAYS)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText()).isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")
  }

  @Test
  fun testRefreshDiskCache_withRetries_succeeded_httpClientTimeout() {
    createContext(
      path = CONTEXT_PATH,
      content = "This is for unit test",
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_OK
    )

    virtualExecutor.advanceBy(1, TimeUnit.HOURS)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText()).isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")

    cleanContext(CONTEXT_PATH)
    createContext(
      path = CONTEXT_PATH,
      content = "[updated]This is for unit test",
      eTag = "84509f",
      rCode = HttpURLConnection.HTTP_CLIENT_TIMEOUT,
      rLen = "Http client timeout".toByteCnt()
    )

    virtualExecutor.advanceBy(1, TimeUnit.DAYS)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText()).isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")

    virtualExecutor.advanceBy(3, TimeUnit.HOURS)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText()).isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")

    cleanContext(CONTEXT_PATH)
    createContext(
      path = CONTEXT_PATH,
      content = "[updated]This is for unit test",
      eTag = "84509f",
      rCode = HttpURLConnection.HTTP_OK
    )

    // Refresh successfully at last.
    virtualExecutor.advanceBy(8, TimeUnit.HOURS)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText()).isEqualTo("[updated]This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("84509f")
  }

  @Test
  fun testRefreshDiskCache_withRetries_succeeded_socketTimeout() {
    createContext(
      path = CONTEXT_PATH,
      content = "This is for unit test",
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_OK
    )

    virtualExecutor.advanceBy(1, TimeUnit.HOURS)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText()).isEqualTo("This is for unit test")
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

    virtualExecutor.advanceBy(1, TimeUnit.DAYS)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText()).isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")

    cleanContext(CONTEXT_PATH)
    createContext(
      path = CONTEXT_PATH,
      content = "[updated]This is for unit test",
      eTag = "84509f",
      rCode = HttpURLConnection.HTTP_OK
    )

    // Refresh successfully at the second retry.
    virtualExecutor.advanceBy(2, TimeUnit.HOURS)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText()).isEqualTo("[updated]This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("84509f")
  }

  @Test
  fun testRefreshDiskCache_withRetries_failed() {
    createContext(
      path = CONTEXT_PATH,
      content = "This is for unit test",
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_OK
    )

    virtualExecutor.advanceBy(1, TimeUnit.HOURS)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText()).isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")

    cleanContext(CONTEXT_PATH)

    virtualExecutor.advanceBy(1, TimeUnit.DAYS)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText()).isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")


    // Refresh failed after maximum attempts.
    virtualExecutor.advanceBy(16, TimeUnit.HOURS)
    assertThat(gMavenIndexRepository.loadIndexFromDisk().toText()).isEqualTo("This is for unit test")
    assertThat(getETagForFile(cacheFile)).isEqualTo("843fc7")
  }

  private fun createContext(
    path: String,
    content: String,
    eTag: String,
    rCode: Int,
    rLen: Long = 0
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
    synchronized(server) {
      server.removeContext(path)
    }
  }

  private fun InputStream.toText(): String {
    return bufferedReader(UTF_8).use {
      it.readText()
    }
  }

  private fun String.toByteCnt(): Long {
    return toByteArray(UTF_8).size.toLong()
  }

  private fun getETagForFile(file: Path): String? {
    val eTagFile = getETagFile(file)
    return try {
      val properties = Properties().apply {
        CancellableFileIo.newInputStream(eTagFile).use { inputStream ->
          this.load(inputStream)
        }
      }
      properties.getProperty("etag")
    }
    catch (ignore: Exception) {
      null
    }
  }

  private fun getETagFile(file: Path): Path {
    return file.resolveSibling("${file.fileName}.properties")
  }
}