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

import com.android.tools.idea.ServerFlag
import com.android.tools.idea.ServerFlagData
import com.android.tools.idea.ServerFlagList
import com.android.tools.idea.ServerFlagTest
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Any
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.createFile
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import junit.framework.TestCase
import java.net.InetSocketAddress
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Path
import java.util.concurrent.Executors

private const val FILE_NAME = "serverflaglist.protobuf"
private const val VERSION = "4.2.0.0"
private val EXPERIMENTS = listOf("boolean", "int")
private val TEST_PROTO = ServerFlagTest.newBuilder().apply {
  content = "content"
}.build()

class ServerFlagInitializerTest : TestCase() {
  lateinit var testDirectoryPath: Path
  lateinit var downloadPath: Path
  lateinit var localPath: Path

  override fun setUp() {
    super.setUp()
    testDirectoryPath = FileUtil.createTempDirectory("ServerFlagInitializerTest", null).toPath()
    downloadPath = testDirectoryPath.resolve("download")
    localPath = testDirectoryPath.resolve("local")
  }

  override fun tearDown() {
    FileUtils.deleteRecursivelyIfExists(testDirectoryPath.toFile())
    super.tearDown()
  }

  fun testInitializer() {
    val expected = buildServerFlagList()
    createDownloadFile(expected, downloadPath)
    testServerFlagInitializer(expected)
  }

  fun testInitializeFromLocal() {
    val expected = buildServerFlagList()
    createDownloadFile(expected, localPath)
    testServerFlagInitializer(expected)
  }

  fun testBothFilesPresent() {
    val expected = buildServerFlagList()
    createDownloadFile(expected, downloadPath)

    val local = ServerFlagList.newBuilder().apply {
      configurationVersion = 2
    }.build()
    createDownloadFile(local, localPath)
    testServerFlagInitializer(expected)
  }

  fun testNeitherFilePresent() {
    val baseUrl = downloadPath.toUri().toURL().toString()
    ServerFlagInitializer.initializeService(baseUrl, localPath, VERSION, EXPERIMENTS)

    ServerFlagService.instance.apply {
      assertThat(getBoolean("boolean")).isNull()
      assertThat(getInt("int")).isNull()
      assertThat(getFloat("float")).isNull()
      assertThat(getString("string")).isNull()
    }
  }

  fun testMalformedUrl() {
    val baseUrl = "foo"
    val expected = buildServerFlagList()
    createDownloadFile(expected, localPath)
    ServerFlagInitializer.initializeService(baseUrl, localPath, VERSION, EXPERIMENTS)

    ServerFlagService.instance.apply {
      assertThat(getBoolean("boolean")).isEqualTo(true)
      assertThat(getInt("int")).isEqualTo(1)
      assertThat(getFloat("float")).isNull()
      assertThat(getString("string")).isNull()
    }
  }

  fun testPercentEnabled() {
    val baseUrl = downloadPath.toUri().toURL().toString()
    val expected = buildServerFlagList()
    createDownloadFile(expected, downloadPath)
    ServerFlagInitializer.initializeService(baseUrl, localPath, VERSION, emptyList())

    ServerFlagService.instance.apply {
      assertThat(getBoolean("boolean")).isNull()
      assertThat(getInt("int")).isNull()
      assertThat(getFloat("float")).isEqualTo(1f)
      assertThat(getString("string")).isEqualTo("foo")
    }
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

  private fun testServerError(error: HttpError) {
    val stub = ServerStub(error)
    val baseUrl = stub.url
    val expected = buildServerFlagList()
    createDownloadFile(expected, localPath)

    ServerFlagInitializer.initializeService(baseUrl.toString(), localPath, VERSION, EXPERIMENTS)
    ServerFlagService.instance.apply {
      assertThat(getBoolean("boolean")).isEqualTo(true)
      assertThat(getInt("int")).isEqualTo(1)
      assertThat(getFloat("float")).isNull()
      assertThat(getString("string")).isNull()
    }

    val actual = loadServerFlagList(localPath)
    assertThat(actual.configurationVersion).isEqualTo(expected.configurationVersion)
    assertThat(actual.serverFlagsList).containsExactlyElementsIn(expected.serverFlagsList)
  }

  private fun createDownloadFile(serverFlagList: ServerFlagList, path: Path) {
    val downloadFile = path.resolve("$VERSION/$FILE_NAME")
    downloadFile.createFile()
    downloadFile.toFile().outputStream().use { serverFlagList.writeTo(it) }
  }

  private fun buildServerFlagList(): ServerFlagList {
    val flags = mutableListOf(
      ServerFlag.newBuilder().apply {
        percentEnabled = 0
        booleanValue = true
      }.build(),
      ServerFlag.newBuilder().apply {
        percentEnabled = 0
        intValue = 1
      }.build(),
      ServerFlag.newBuilder().apply {
        percentEnabled = 100
        floatValue = 1f
      }.build(),
      ServerFlag.newBuilder().apply {
        percentEnabled = 100
        stringValue = "foo"
      }.build(),
      ServerFlag.newBuilder().apply {
        percentEnabled = 100
        protoValue = Any.pack(ServerFlagTest.newBuilder().apply {
          content = "content"
        }.build())
      }.build()
    )

    val flagData = listOf(
      makeServerFlagData("boolean", flags[0]),
      makeServerFlagData("int", flags[1]),
      makeServerFlagData("float", flags[2]),
      makeServerFlagData("string", flags[3]),
      makeServerFlagData("proto", flags[4])
    )

    val builder = ServerFlagList.newBuilder().apply {
      configurationVersion = 1
    }
    builder.addAllServerFlags(flagData)
    return builder.build()
  }

  private fun testServerFlagInitializer(expected: ServerFlagList) {
    val baseUrl = downloadPath.toUri().toURL().toString()
    ServerFlagInitializer.initializeService(baseUrl, localPath, VERSION, EXPERIMENTS)

    ServerFlagService.instance.apply {
      assertThat(getBoolean("boolean")).isEqualTo(true)
      assertThat(getInt("int")).isEqualTo(1)
      assertThat(getFloat("float")).isNull()
      assertThat(getFloat("string")).isNull()
      assertThat(getProtoMessage(name, TEST_PROTO).content).isEqualTo("content")
    }

    val actual = loadServerFlagList(localPath)
    assertThat(actual.configurationVersion).isEqualTo(expected.configurationVersion)
    assertThat(actual.serverFlagsList).containsExactlyElementsIn(expected.serverFlagsList)
  }

  private fun loadServerFlagList(localPath: Path): ServerFlagList {
    val filePath = localPath.resolve("$VERSION/$FILE_NAME")
    filePath.toFile().inputStream().use { return ServerFlagList.parseFrom(it) }
  }

  private fun makeServerFlagData(flagName: String, flag: ServerFlag): ServerFlagData {
    return ServerFlagData.newBuilder().apply {
      name = flagName
      serverFlag = flag
    }.build()
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