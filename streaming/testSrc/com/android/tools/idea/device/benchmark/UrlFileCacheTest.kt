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
package com.android.tools.idea.device.benchmark

import com.google.common.truth.Truth.assertThat
import com.intellij.util.io.HttpRequests.HttpStatusException
import com.intellij.util.io.exists
import com.intellij.util.io.isDirectory
import com.intellij.util.io.isFile
import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.test.assertFailsWith
import kotlin.time.Duration

private const val LOCALHOST = "127.0.0.1"
private const val LAST_MODIFIED = "last modified header value"
private const val ETAG = "etag header value"
private val FILES = listOf("/path/to/file1" to "Hey these are the great contents of my file.",
                          "/path/to/file2" to  "This file isn't quite as great.")

/** Tests the [UrlFileCache] class. */
@RunWith(JUnit4::class)
class UrlFileCacheTest {
  private lateinit var server: HttpServer
  private lateinit var url: String

  private val urlFileCache = UrlFileCache()

  @Before
  fun setUp() {
    if (!::server.isInitialized) {
      server = HttpServer.create()
      server.bind(InetSocketAddress(LOCALHOST, 0), 1)
    }
    server.start()
    url = "http://$LOCALHOST:${server.address.port}"
  }

  @After
  fun tearDown() {
    if (::server.isInitialized) {
      server.stop(0)
    }
  }

  @Test
  fun get_fresh_basic() {
    serveTextFile(FILES[0])

    val path = urlFileCache.get(url + FILES[0].first)
    assertThat(path).isNotEmpty()
    assertThat(path.exists()).isTrue()
    assertThat(path.isFile()).isTrue()
    assertThat(path.parent.isDirectory()).isTrue()
    assertThat(path.parent.listDirectoryEntries()).containsExactly(path)
    assertThat(String(path.readBytes())).isEqualTo(FILES[0].second)
  }

  @Test
  fun get_fresh_transform() {
    serveTextFile(FILES[0])

    val path = urlFileCache.get(url + FILES[0].first) {
      ByteArrayInputStream(String(it.readAllBytes()).reversed().toByteArray())
    }
    assertThat(path).isNotEmpty()
    assertThat(path.exists()).isTrue()
    assertThat(path.isFile()).isTrue()
    assertThat(path.parent.isDirectory()).isTrue()
    assertThat(path.parent.listDirectoryEntries()).containsExactly(path)
    assertThat(String(path.readBytes())).isEqualTo(FILES[0].second.reversed())
  }

  @Test
  fun get_fresh_multiple() {
    FILES.forEach { serveTextFile(it) }

    val paths = FILES.map { urlFileCache.get(url + it.first) }
    paths.forEachIndexed { i, path ->
      assertThat(path).isNotEmpty()
      assertThat(path.exists()).isTrue()
      assertThat(String(path.readBytes())).isEqualTo(FILES[i].second)
    }
    val parents = paths.map(Path::getParent).distinct()
    assertThat(parents).hasSize(1)
    assertThat(parents.first().isDirectory()).isTrue()
    assertThat(parents.first().listDirectoryEntries()).containsExactlyElementsIn(paths)
  }

  @Test
  fun get_repeated_noCaching() {
    serveTextFile(FILES[0])

    val initialPath = urlFileCache.get(url + FILES[0].first)
    val repeatedPath = urlFileCache.get(url + FILES[0].first)

    assertThat(repeatedPath.parent).isEqualTo(initialPath.parent)
    assertThat(initialPath.exists()).isFalse()
    assertThat(repeatedPath.exists()).isTrue()
    assertThat(repeatedPath.isFile()).isTrue()
    assertThat(repeatedPath.parent.isDirectory()).isTrue()
    assertThat(repeatedPath.parent.listDirectoryEntries()).containsExactly(repeatedPath)
    assertThat(String(repeatedPath.readBytes())).isEqualTo(FILES[0].second)
  }

  @Test
  fun get_repeated_withCaching() {
    serveTextFile(FILES[0])

    val initialPath = urlFileCache.get(url + FILES[0].first)
    val repeatedPath = urlFileCache.get(url + FILES[0].first, Duration.INFINITE.inWholeMilliseconds)

    assertThat(repeatedPath).isEqualTo(initialPath)
    assertThat(initialPath.exists()).isTrue()
    assertThat(initialPath.isFile()).isTrue()
    assertThat(initialPath.parent.isDirectory()).isTrue()
    assertThat(initialPath.parent.listDirectoryEntries()).containsExactly(initialPath)
    assertThat(String(initialPath.readBytes())).isEqualTo(FILES[0].second)
  }

  @Test
  fun get_repeated_notModifiedHeader_noCache() {
    server.createContext(FILES[0].first) {
      it.sendResponseHeaders(HttpURLConnection.HTTP_NOT_MODIFIED, -1)
      it.close()
    }

    assertFailsWith<HttpStatusException> { urlFileCache.get(url + FILES[0].first) }
  }

  @Test
  fun get_repeated_notModifiedHeader_withCache() {
    // Load up the cache.
    serveTextFile(FILES[0])
    val initialPath = urlFileCache.get(url + FILES[0].first)
    server.removeContext(FILES[0].first)

    server.createContext(FILES[0].first) {
      it.sendResponseHeaders(HttpURLConnection.HTTP_NOT_MODIFIED, -1)
      it.close()
    }

    val repeatedPath = urlFileCache.get(url + FILES[0].first)

    assertThat(repeatedPath).isEqualTo(initialPath)
    assertThat(repeatedPath.exists()).isTrue()
    assertThat(repeatedPath.isFile()).isTrue()
    assertThat(repeatedPath.parent.isDirectory()).isTrue()
    assertThat(repeatedPath.parent.listDirectoryEntries()).containsExactly(repeatedPath)
    assertThat(String(repeatedPath.readBytes())).isEqualTo(FILES[0].second)
  }

  @Test
  fun get_repeated_notModifiedHeader_withCache_transform() {
    // Load up the cache.
    serveTextFile(FILES[0])
    val initialPath = urlFileCache.get(url + FILES[0].first) {
      ByteArrayInputStream(String(it.readAllBytes()).reversed().toByteArray())
    }
    server.removeContext(FILES[0].first)

    server.createContext(FILES[0].first) {
      it.sendResponseHeaders(HttpURLConnection.HTTP_NOT_MODIFIED, -1)
      it.close()
    }

    // The transform should not be repeated
    val repeatedPath = urlFileCache.get(url + FILES[0].first) {
      ByteArrayInputStream(String(it.readAllBytes()).reversed().toByteArray())
    }

    assertThat(repeatedPath).isEqualTo(initialPath)
    assertThat(repeatedPath.exists()).isTrue()
    assertThat(repeatedPath.isFile()).isTrue()
    assertThat(String(repeatedPath.readBytes())).isEqualTo(FILES[0].second.reversed())
  }

  @Test
  fun get_repeated_usesLastModifiedHeaders() {
    // Load up the cache.
    serveTextFile(FILES[0], responseHeaders = mapOf("Last-Modified" to LAST_MODIFIED, "ETag" to ETAG))

    urlFileCache.get(url + FILES[0].first)
    server.removeContext(FILES[0].first)

    lateinit var requestHeaders: Headers
    server.createContext(FILES[0].first) {
      requestHeaders = it.requestHeaders
      it.responseHeaders.add("Content-Type", "text/plain")
      it.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
      it.responseBody.write(5)
      it.close()
    }

    urlFileCache.get(url + FILES[0].first)
    assertThat(requestHeaders["If-Modified-Since"]).containsExactly(LAST_MODIFIED)
    assertThat(requestHeaders["If-None-Match"]).containsExactly(ETAG)
  }

  @Test
  fun dispose() {
    FILES.forEach { serveTextFile(it) }
    val paths = FILES.map { urlFileCache.get(url + it.first) }
    val parents = paths.map(Path::getParent).distinct()

    urlFileCache.dispose()

    paths.forEach { assertThat(it.exists()).isFalse() }
    parents.forEach { assertThat(it.exists()).isFalse() }
  }

  private fun serveTextFile(pathAndContent: Pair<String, String>, responseHeaders: Map<String, String> = mapOf()) {
    serveFile(pathAndContent.first, pathAndContent.second.toByteArray(), responseHeaders)
  }

  private fun serveFile(path: String, content: ByteArray, responseHeaders: Map<String, String> = mapOf()) {
    server.createContext(path) { ex ->
      responseHeaders.forEach {
        ex.responseHeaders.add(it.key, it.value)
      }
      ex.responseHeaders.add("Content-Type", "text/plain")
      ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
      ex.responseBody.write(content)
      ex.close()
    }
  }
}
