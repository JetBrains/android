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
import com.android.tools.idea.serverflags.protos.ServerFlagTest
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import junit.framework.TestCase
import java.nio.file.Path

private const val VERSION = "4.2.0.0"
private val EXPERIMENTS = listOf("boolean", "int")
private val TEST_PROTO = ServerFlagTest.newBuilder().apply {
  content = "content"
}.build()

class ServerFlagInitializerTest : TestCase() {
  lateinit var testDirectoryPath: Path
  lateinit var localPath: Path

  override fun setUp() {
    super.setUp()
    testDirectoryPath = FileUtil.createTempDirectory("ServerFlagInitializerTest", null).toPath()
    localPath = testDirectoryPath.resolve("local")
  }

  override fun tearDown() {
    FileUtils.deleteRecursivelyIfExists(testDirectoryPath.toFile())
    super.tearDown()
  }

  fun testInitializeFromLocal() {
    val expected = serverFlagTestData
    saveServerFlagList(expected, localPath, VERSION)
    testServerFlagInitializer(expected)
  }

  fun testFileNotPresent() {
    ServerFlagInitializer.initializeService(localPath, VERSION, EXPERIMENTS)

    ServerFlagService.instance.apply {
      assertThat(getBoolean("boolean")).isNull()
      assertThat(getInt("int")).isNull()
      assertThat(getFloat("float")).isNull()
      assertThat(getString("string")).isNull()
    }
  }

  fun testPercentEnabled() {
    val expected = serverFlagTestData
    saveServerFlagList(expected, localPath, VERSION)
    ServerFlagInitializer.initializeService(localPath, VERSION, emptyList())

    ServerFlagService.instance.apply {
      assertThat(getBoolean("boolean")).isNull()
      assertThat(getInt("int")).isNull()
      assertThat(getFloat("float")).isEqualTo(1f)
      assertThat(getString("string")).isEqualTo("foo")
    }
  }

  private fun testServerFlagInitializer(expected: ServerFlagList) {
    ServerFlagInitializer.initializeService(localPath, VERSION, EXPERIMENTS)

    ServerFlagService.instance.apply {
      assertThat(getBoolean("boolean")).isEqualTo(true)
      assertThat(getInt("int")).isEqualTo(1)
      assertThat(getFloat("float")).isNull()
      assertThat(getFloat("string")).isNull()
      assertThat(getProto(name, TEST_PROTO).content).isEqualTo("content")
    }

    val actual = loadServerFlagList(localPath, VERSION)
    assertThat(actual).isEqualTo(expected)
  }
}