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

import com.android.tools.idea.downloads.UrlFileCache.FetchStats
import com.google.common.truth.Truth.assertThat
import com.intellij.util.io.HttpRequests.HttpStatusException
import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.test.assertFailsWith
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private const val LOCALHOST = "127.0.0.1"
private const val LAST_MODIFIED = "last modified header value"
private const val ETAG = "etag header value"
private val FILES =
  listOf(
    "/path/to/file1" to "Hey these are the great contents of my file.",
    "/path/to/file2" to "This file isn't quite as great.",
  )
private val FETCH_DURATION = 37.seconds
private val SUBSEQUENT_FETCH_DURATION = 22.seconds

/** Tests the [UrlFileCache] class. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class UrlFileCacheTest {
  private lateinit var server: HttpServer
  private lateinit var url: String

  private val testScheduler = TestCoroutineScheduler()
  private val testIoDispatcher = StandardTestDispatcher(testScheduler)
  private val unconfinedDispatcher = UnconfinedTestDispatcher(testScheduler)
  private val testTimeSource = TestTimeSource()
  private val testClock = TestClock()

  private val urlFileCache =
    UrlFileCache(TestScope(unconfinedDispatcher), testIoDispatcher, testTimeSource, testClock)

  @Before
  fun setUp() {
    if (!::server.isInitialized) {
      server = HttpServer.create()
      server.bind(InetSocketAddress(LOCALHOST, 0), 1)
    }
    server.start()
    url = "http://$LOCALHOST:${server.address.port}"

    Dispatchers.setMain(unconfinedDispatcher)
  }

  @After
  fun tearDown() {
    if (::server.isInitialized) {
      server.stop(0)
    }
  }

  /**
   * Most of the tests just call getWithStats since it calls the same code. This makes sure the
   * basic "get" works correctly.
   */
  @Test
  fun get_fresh_basic() = runTest {
    serveTextFile(FILES[0])

    val deferred = urlFileCache.get(url + FILES[0].first)

    assertThat(deferred.isCompleted).isFalse()

    testTimeSource += FETCH_DURATION
    advanceUntilIdle()

    assertThat(deferred.isCompleted).isTrue()
    val path = deferred.getCompleted()
    assertThat(path).isNotEmpty()
    assertThat(path.exists()).isTrue()
    assertThat(path.isRegularFile()).isTrue()
    assertThat(path.parent.isDirectory()).isTrue()
    assertThat(path.parent.listDirectoryEntries()).containsExactly(path)
    assertThat(String(path.readBytes())).isEqualTo(FILES[0].second)
  }

  @Test
  fun getWithStats_fresh_basic() = runTest {
    serveTextFile(FILES[0])

    val deferred = urlFileCache.getWithStats(url + FILES[0].first)

    assertThat(deferred.isCompleted).isFalse()

    testTimeSource += FETCH_DURATION
    advanceUntilIdle()

    assertThat(deferred.isCompleted).isTrue()
    val (path, stats) = deferred.getCompleted()
    assertThat(path).isNotEmpty()
    assertThat(path.exists()).isTrue()
    assertThat(path.isRegularFile()).isTrue()
    assertThat(path.parent.isDirectory()).isTrue()
    assertThat(path.parent.listDirectoryEntries()).containsExactly(path)
    assertThat(String(path.readBytes())).isEqualTo(FILES[0].second)

    val fileSize = FILES[0].second.numBytesAsLong()
    assertThat(stats)
      .isEqualTo(FetchStats(FETCH_DURATION, numBytesFetched = fileSize, numBytesCached = fileSize))
  }

  @Test
  fun getWithStats_fresh_transform() = runTest {
    serveTextFile(FILES[0])

    val deferred =
      urlFileCache.getWithStats(url + FILES[0].first) {
        // "Transform" it into the other file.
        ByteArrayInputStream(FILES[1].second.reversed().toByteArray())
      }

    assertThat(deferred.isCompleted).isFalse()

    testTimeSource += FETCH_DURATION
    advanceUntilIdle()

    assertThat(deferred.isCompleted).isTrue()

    val (path, stats) = deferred.getCompleted()
    assertThat(path).isNotEmpty()
    assertThat(path.exists()).isTrue()
    assertThat(path.isRegularFile()).isTrue()
    assertThat(path.parent.isDirectory()).isTrue()
    assertThat(path.parent.listDirectoryEntries()).containsExactly(path)
    assertThat(String(path.readBytes())).isEqualTo(FILES[1].second.reversed())

    assertThat(stats)
      .isEqualTo(
        FetchStats(
          FETCH_DURATION,
          numBytesFetched = FILES[0].second.numBytesAsLong(),
          numBytesCached = FILES[1].second.numBytesAsLong(),
        )
      )
  }

  @Test
  fun getWithStats_fresh_multiple() = runTest {
    FILES.forEach { serveTextFile(it) }

    val deferreds = FILES.map { urlFileCache.getWithStats(url + it.first) }

    assertThat(deferreds.any { it.isCompleted }).isFalse()

    testTimeSource += FETCH_DURATION
    advanceUntilIdle()

    assertThat(deferreds.all { it.isCompleted }).isTrue()

    val paths = deferreds.map { it.getCompleted().first }
    val stats = deferreds.map { it.getCompleted().second }
    paths.forEachIndexed { i, path ->
      assertThat(path).isNotEmpty()
      assertThat(path.exists()).isTrue()
      assertThat(String(path.readBytes())).isEqualTo(FILES[i].second)
    }
    val parents = paths.map(Path::getParent).distinct()
    assertThat(parents).hasSize(1)
    assertThat(parents.first().isDirectory()).isTrue()
    assertThat(parents.first().listDirectoryEntries()).containsExactlyElementsIn(paths)
    assertThat(stats)
      .isEqualTo(
        FILES.map {
          FetchStats(
            FETCH_DURATION,
            numBytesFetched = it.second.numBytesAsLong(),
            numBytesCached = it.second.numBytesAsLong(),
          )
        }
      )
  }

  @Test
  fun getWithStats_repeated_noCaching() = runTest {
    serveTextFile(FILES[0])

    val initialDeferred = urlFileCache.getWithStats(url + FILES[0].first)
    assertThat(initialDeferred.isCompleted).isFalse()
    testTimeSource += FETCH_DURATION
    advanceUntilIdle()
    assertThat(initialDeferred.isCompleted).isTrue()
    val (initialPath, initialStats) = initialDeferred.getCompleted()
    val fileSize = FILES[0].second.numBytesAsLong()
    assertThat(initialStats)
      .isEqualTo(FetchStats(FETCH_DURATION, numBytesFetched = fileSize, numBytesCached = fileSize))

    val repeatedDeferred = urlFileCache.getWithStats(url + FILES[0].first)
    assertThat(repeatedDeferred.isCompleted).isFalse()
    testTimeSource += SUBSEQUENT_FETCH_DURATION
    advanceUntilIdle()
    assertThat(repeatedDeferred.isCompleted).isTrue()
    val (repeatedPath, repeatedStats) = repeatedDeferred.getCompleted()

    assertThat(repeatedPath.parent).isEqualTo(initialPath.parent)
    assertThat(initialPath.exists()).isFalse() // Should have been cleaned up.
    assertThat(repeatedPath.exists()).isTrue()
    assertThat(repeatedPath.isRegularFile()).isTrue()
    assertThat(repeatedPath.parent.isDirectory()).isTrue()
    assertThat(repeatedPath.parent.listDirectoryEntries()).containsExactly(repeatedPath)
    assertThat(String(repeatedPath.readBytes())).isEqualTo(FILES[0].second)
    assertThat(repeatedStats)
      .isEqualTo(
        FetchStats(SUBSEQUENT_FETCH_DURATION, numBytesFetched = fileSize, numBytesCached = fileSize)
      )
  }

  @Test
  fun getWithStats_repeated_withCaching() = runTest {
    serveTextFile(FILES[0])

    val initialDeferred = urlFileCache.getWithStats(url + FILES[0].first)
    assertThat(initialDeferred.isCompleted).isFalse()
    testTimeSource += FETCH_DURATION
    advanceUntilIdle()
    assertThat(initialDeferred.isCompleted).isTrue()
    val (initialPath, initialStats) = initialDeferred.getCompleted()
    val initialWrittenInstant = testClock.now().toJavaInstant()
    assertThat(initialPath.getLastModifiedTime().toInstant()).isEqualTo(initialWrittenInstant)
    val fileSize = FILES[0].second.numBytesAsLong()
    assertThat(initialStats)
      .isEqualTo(FetchStats(FETCH_DURATION, numBytesFetched = fileSize, numBytesCached = fileSize))

    testClock += 5.minutes

    val repeatedDeferred = urlFileCache.getWithStats(url + FILES[0].first, Duration.INFINITE)
    // This happens instantly (no coroutine) because it is cached. No time to update the
    // testTimeSource.
    assertThat(repeatedDeferred.isCompleted).isTrue()
    val (repeatedPath, repeatedStats) = repeatedDeferred.getCompleted()

    assertThat(repeatedPath).isEqualTo(initialPath)
    assertThat(initialPath.exists()).isTrue()
    assertThat(initialPath.isRegularFile()).isTrue()
    assertThat(initialPath.parent.isDirectory()).isTrue()
    assertThat(initialPath.parent.listDirectoryEntries()).containsExactly(initialPath)
    assertThat(String(initialPath.readBytes())).isEqualTo(FILES[0].second)
    // We should not have updated the timestamp because we haven't heard form the server.
    assertThat(initialPath.getLastModifiedTime().toInstant()).isEqualTo(initialWrittenInstant)

    assertThat(repeatedStats).isEqualTo(FetchStats(Duration.ZERO, cacheHit = true))
  }

  @Test
  fun getWithStats_repeated_notModifiedHeader_noCache() = runTest {
    server.createContext(FILES[0].first) {
      it.sendResponseHeaders(HttpURLConnection.HTTP_NOT_MODIFIED, -1)
      it.close()
    }

    val e =
      assertFailsWith<UrlFileCache.UrlFileCacheException> {
        val job = urlFileCache.getWithStats(url + FILES[0].first)
        testTimeSource += FETCH_DURATION
        job.await()
      }
    assertThat(e.cause).isInstanceOf(HttpStatusException::class.java)
    assertThat(e.fetchStats)
      .isEqualTo(FetchStats(FETCH_DURATION, success = false, notModified = true))
  }

  @Test
  fun get_repeated_notModifiedHeader_noCache_throwsOriginalException() = runTest {
    server.createContext(FILES[0].first) {
      it.sendResponseHeaders(HttpURLConnection.HTTP_NOT_MODIFIED, -1)
      it.close()
    }

    assertFailsWith<HttpStatusException> {
      val job = urlFileCache.get(url + FILES[0].first)
      testTimeSource += FETCH_DURATION
      job.await()
    }
  }

  @Test
  fun getWithStats_repeated_notModifiedHeader_withCache() = runTest {
    // Load up the cache.
    serveTextFile(FILES[0])
    val initialDeferred = urlFileCache.getWithStats(url + FILES[0].first)
    assertThat(initialDeferred.isCompleted).isFalse()
    testTimeSource += FETCH_DURATION
    advanceUntilIdle()
    assertThat(initialDeferred.isCompleted).isTrue()
    val (initialPath, initialStats) = initialDeferred.getCompleted()
    assertThat(initialPath.getLastModifiedTime().toInstant())
      .isEqualTo(testClock.now().toJavaInstant())
    val fileSize = FILES[0].second.numBytesAsLong()
    assertThat(initialStats)
      .isEqualTo(FetchStats(FETCH_DURATION, numBytesFetched = fileSize, numBytesCached = fileSize))

    testClock += 5.minutes

    server.removeContext(FILES[0].first)
    server.createContext(FILES[0].first) {
      it.sendResponseHeaders(HttpURLConnection.HTTP_NOT_MODIFIED, -1)
      it.close()
    }

    val repeatedDeferred = urlFileCache.getWithStats(url + FILES[0].first)
    assertThat(repeatedDeferred.isCompleted).isFalse()
    testTimeSource += SUBSEQUENT_FETCH_DURATION
    advanceUntilIdle()
    assertThat(repeatedDeferred.isCompleted).isTrue()
    val (repeatedPath, repeatedStats) = repeatedDeferred.getCompleted()

    assertThat(repeatedPath).isEqualTo(initialPath)
    assertThat(repeatedPath.exists()).isTrue()
    assertThat(repeatedPath.isRegularFile()).isTrue()
    assertThat(repeatedPath.parent.isDirectory()).isTrue()
    assertThat(repeatedPath.parent.listDirectoryEntries()).containsExactly(repeatedPath)
    assertThat(String(repeatedPath.readBytes())).isEqualTo(FILES[0].second)
    // The timestamp should have updated since we heard from the server.
    assertThat(repeatedPath.getLastModifiedTime().toInstant())
      .isEqualTo(testClock.now().toJavaInstant())

    assertThat(repeatedStats).isEqualTo(FetchStats(SUBSEQUENT_FETCH_DURATION, notModified = true))
  }

  @Test
  fun getWithStats_repeated_notModifiedHeader_withCache_transform() = runTest {
    // Load up the cache.
    serveTextFile(FILES[0])
    val initialDeferred =
      urlFileCache.getWithStats(url + FILES[0].first) {
        ByteArrayInputStream(FILES[1].second.reversed().toByteArray())
      }
    assertThat(initialDeferred.isCompleted).isFalse()
    testTimeSource += FETCH_DURATION
    advanceUntilIdle()
    assertThat(initialDeferred.isCompleted).isTrue()
    val (initialPath, initialStats) = initialDeferred.getCompleted()
    assertThat(initialPath.getLastModifiedTime().toInstant())
      .isEqualTo(testClock.now().toJavaInstant())
    assertThat(initialStats)
      .isEqualTo(
        FetchStats(
          FETCH_DURATION,
          numBytesFetched = FILES[0].second.numBytesAsLong(),
          numBytesCached = FILES[1].second.numBytesAsLong(),
        )
      )

    server.removeContext(FILES[0].first)
    server.createContext(FILES[0].first) {
      it.sendResponseHeaders(HttpURLConnection.HTTP_NOT_MODIFIED, -1)
      it.close()
    }

    // The transform should not be repeated
    val repeatedDeferred =
      urlFileCache.getWithStats(url + FILES[0].first) { fail("Should not even be called") }
    assertThat(repeatedDeferred.isCompleted).isFalse()
    testTimeSource += SUBSEQUENT_FETCH_DURATION
    advanceUntilIdle()
    assertThat(repeatedDeferred.isCompleted).isTrue()
    val (repeatedPath, repeatedStats) = repeatedDeferred.getCompleted()

    assertThat(repeatedPath).isEqualTo(initialPath)
    assertThat(repeatedPath.exists()).isTrue()
    assertThat(repeatedPath.isRegularFile()).isTrue()
    assertThat(String(repeatedPath.readBytes())).isEqualTo(FILES[1].second.reversed())
    // The timestamp should have updated since we heard from the server.
    assertThat(repeatedPath.getLastModifiedTime().toInstant())
      .isEqualTo(testClock.now().toJavaInstant())

    assertThat(repeatedStats).isEqualTo(FetchStats(SUBSEQUENT_FETCH_DURATION, notModified = true))
  }

  @Test
  fun getWithStats_repeated_usesLastModifiedHeaders() = runTest {
    // Load up the cache.
    serveTextFile(
      FILES[0],
      responseHeaders = mapOf("Last-Modified" to LAST_MODIFIED, "ETag" to ETAG),
    )

    urlFileCache.get(url + FILES[0].first)
    advanceUntilIdle()
    server.removeContext(FILES[0].first)

    var requestHeaders = Headers()
    server.createContext(FILES[0].first) {
      requestHeaders = it.requestHeaders
      it.responseHeaders.add("Content-Type", "text/plain")
      it.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
      it.responseBody.write(5)
      it.close()
    }

    urlFileCache.get(url + FILES[0].first)
    advanceUntilIdle()
    assertThat(requestHeaders["If-Modified-Since"]).containsExactly(LAST_MODIFIED)
    assertThat(requestHeaders["If-None-Match"]).containsExactly(ETAG)

    // The last response didn't have the headers so we should not have those on the next request.
    urlFileCache.get(url + FILES[0].first)
    advanceUntilIdle()
    assertThat(requestHeaders["If-Modified-Since"]).isNull()
    assertThat(requestHeaders["If-None-Match"]).isNull()
  }

  @Test
  fun dispose() = runTest {
    FILES.forEach { serveTextFile(it) }
    val paths = FILES.map { urlFileCache.get(url + it.first).await() }
    val parents = paths.map(Path::getParent).distinct()

    urlFileCache.dispose()

    paths.forEach { assertThat(it.exists()).isFalse() }
    parents.forEach { assertThat(it.exists()).isFalse() }
  }

  private fun serveTextFile(
    pathAndContent: Pair<String, String>,
    responseHeaders: Map<String, String> = mapOf(),
  ) {
    serveFile(pathAndContent.first, pathAndContent.second.toByteArray(), responseHeaders)
  }

  private fun serveFile(
    path: String,
    content: ByteArray,
    responseHeaders: Map<String, String> = mapOf(),
  ) {
    server.createContext(path) { ex ->
      responseHeaders.forEach { ex.responseHeaders.add(it.key, it.value) }
      ex.responseHeaders.add("Content-Type", "text/plain")
      ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
      ex.responseBody.write(content)
      ex.close()
    }
  }

  private fun String.numBytesAsLong() = toByteArray().size.toLong()

  private class TestClock(epochMillis: Long = 0L) : Clock {
    private var currentInstant = Instant.fromEpochMilliseconds(epochMillis)

    override fun now() = currentInstant

    operator fun plusAssign(duration: Duration) {
      currentInstant += duration
    }
  }
}
