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

import com.android.tools.idea.concurrency.waitForCondition
import com.android.utils.PathUtils
import com.intellij.testFramework.ApplicationRule
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

class GMavenIndexRepositoryTest {
  private val LOCALHOST = "127.0.0.1"
  private lateinit var server: HttpServer
  private lateinit var url: String
  private lateinit var cacheDir: Path
  private lateinit var cacheFile: Path

  @get:Rule
  val rule = ApplicationRule()

  @Before
  fun setUp() {
    cacheDir = Files.createTempDirectory("tempCacheDir")
    cacheFile = cacheDir.resolve("v0.1/classes-v0.1.json")

    server = HttpServer.create()
    with(server) {
      bind(InetSocketAddress(LOCALHOST, 0), 0)
      start()
      url = "http://$LOCALHOST:${address.port}/example"
    }
  }

  @After
  fun tearDown() {
    try {
      PathUtils.deleteRecursivelyIfExists(cacheDir)
    }
    finally {
      server.stop(0)
    }
  }

  @Test
  fun testRefreshDiskCache_hasModificationSinceLast() {
    val gMavenIndexRepository = GMavenIndexRepository(url, cacheDir, Duration.ofMillis(500))
    createContext(
      path = "/",
      content = "This is for unit test",
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_OK
    )

    waitForCondition(2, TimeUnit.SECONDS) {
      gMavenIndexRepository.loadIndexFromDisk().toText() == "This is for unit test"
    }

    cleanContext("/")

    createContext(
      path = "/",
      content = "[updated]This is for unit test",
      eTag = "84509f",
      rCode = HttpURLConnection.HTTP_OK
    )
    waitForCondition(2, TimeUnit.SECONDS) {
      gMavenIndexRepository.loadIndexFromDisk().toText() == "[updated]This is for unit test"
    }
  }

  @Test
  fun testRefreshDiskCache_error() {
    val gMavenIndexRepository = GMavenIndexRepository(url, cacheDir, Duration.ofMillis(500))
    createContext(
      path = "/",
      content = "This is for unit test",
      eTag = "843fc7",
      rCode = HttpURLConnection.HTTP_OK
    )

    waitForCondition(2, TimeUnit.SECONDS) {
      gMavenIndexRepository.loadIndexFromDisk().toText() == "This is for unit test"
    }

    cleanContext("/")
    createContext(
      path = "/",
      content = "[updated]This is for unit test",
      eTag = "84509f",
      rCode = HttpURLConnection.HTTP_CLIENT_TIMEOUT,
      rLen = "Http client timeout".toByteCnt()
    )

    waitForCondition(2, TimeUnit.SECONDS) {
      gMavenIndexRepository.loadIndexFromDisk().toText() == "This is for unit test"
    }
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
}