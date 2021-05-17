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
package com.android.tools.idea.serverflags

import com.android.tools.idea.serverflags.protos.ServerFlagList
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.createFile
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import junit.framework.TestCase
import java.io.File
import java.net.InetSocketAddress
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Path
import java.util.concurrent.Executors

private const val VERSION = "4.2.0.0"
private const val TEMP_FILE = "ServerFlagTempFile"

class ServerFlagDownloaderTest : TestCase() {
  lateinit var testDirectoryPath: Path
  lateinit var downloadPath: Path
  lateinit var localPath: Path
  lateinit var tempFilePath: Path

  override fun setUp() {
    super.setUp()
    testDirectoryPath = FileUtil.createTempDirectory("ServerFlagDownloaderTest", null).toPath()
    downloadPath = testDirectoryPath.resolve("download")
    localPath = testDirectoryPath.resolve("local")
    tempFilePath = testDirectoryPath.resolve(TEMP_FILE)
  }

  override fun tearDown() {
    FileUtils.deleteRecursivelyIfExists(testDirectoryPath.toFile())
    super.tearDown()
  }

  fun testDownloader() {
    val expected = serverFlagTestData
    saveServerFlagList(expected, downloadPath, VERSION)
    testServerFlagDownloader(expected)
  }

  fun testBothFilesPresent() {
    val expected = serverFlagTestData
    saveServerFlagList(expected, downloadPath, VERSION)

    val local = ServerFlagList.newBuilder().apply {
      configurationVersion = 2
    }.build()
    saveServerFlagList(local, localPath, VERSION)

    testServerFlagDownloader(expected)
  }

  fun testMalformedUrl() {
    val expected = serverFlagTestData
    saveServerFlagList(expected, localPath, VERSION)

    val baseUrl = "foo"
    ServerFlagDownloader.downloadServerFlagList(baseUrl, localPath, VERSION) { createTempFile() }

    val actual = loadServerFlagList(localPath, VERSION)
    assertThat(actual).isEqualTo(expected)
    assertTempFileDeleted()
  }

  fun testInternalServerError() {
    testServerError(HttpError.HTTP_INTERNAL_SERVER_ERROR)
  }

  fun testAccessForbiddenError() {
    testServerError(HttpError.HTTP_ACCESS_FORBIDDEN)
  }

  fun testBadRequestError() {
    testServerError(HttpError.HTTP_BAD_REQUEST)
  }

  private fun createTempFile(): File {
    tempFilePath.createFile()
    return tempFilePath.toFile()
  }

  private fun testServerError(error: HttpError) {
    val stub = ServerStub(error)
    val baseUrl = stub.url
    val expected = serverFlagTestData
    saveServerFlagList(expected, localPath, VERSION)

    ServerFlagDownloader.downloadServerFlagList(baseUrl.toString(), localPath, VERSION) { createTempFile() }

    val actual = loadServerFlagList(localPath, VERSION)
    assertThat(actual).isEqualTo(expected)
    assertTempFileDeleted()
  }

  private fun testServerFlagDownloader(expected: ServerFlagList) {
    val baseUrl = downloadPath.toUri().toURL().toString()
    ServerFlagDownloader.downloadServerFlagList(baseUrl, localPath, VERSION) { createTempFile() }

    val actual = loadServerFlagList(localPath, VERSION)
    assertThat(actual).isEqualTo(expected)

    assertTempFileDeleted()
  }

  private fun assertTempFileDeleted() {
    val tempFile = tempFilePath.toFile()
    assertThat(tempFile.exists()).isFalse()
  }

  private enum class HttpError(val value: Int) {
    HTTP_ACCESS_FORBIDDEN(403),
    HTTP_BAD_REQUEST(404),
    HTTP_INTERNAL_SERVER_ERROR(500),
  }

  private class ServerStub(private val error: HttpError) : HttpHandler, AutoCloseable {
    private val address: InetSocketAddress
    private val server: HttpServer = HttpServer.create(InetSocketAddress(0), 0)

    val url: URL
      @Throws(MalformedURLException::class)
      get() = URL(String.format("http://localhost:%d/serverflags", address.port))

    init {
      server.createContext("/", this)
      server.executor = Executors.newSingleThreadExecutor()
      server.start()

      this.address = server.address
    }

    override fun close() {
      server.stop(0)
    }

    override fun handle(httpExchange: HttpExchange) {
      val response = "Internal Server Error".toByteArray(Charsets.UTF_8)
      httpExchange.sendResponseHeaders(error.value, response.size.toLong())
      val body = httpExchange.responseBody
      body.write(response)
    }
  }
}