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

import com.android.tools.analytics.CommonMetricsData.OS_NAME_CHROMIUM
import com.android.tools.analytics.CommonMetricsData.OS_NAME_FREE_BSD
import com.android.tools.analytics.CommonMetricsData.OS_NAME_LINUX
import com.android.tools.analytics.CommonMetricsData.OS_NAME_MAC
import com.android.tools.analytics.CommonMetricsData.OS_NAME_WINDOWS
import com.android.tools.idea.serverflags.protos.Brand
import com.android.tools.idea.serverflags.protos.OSType
import com.android.tools.idea.serverflags.protos.ServerFlagList
import com.android.tools.idea.serverflags.protos.ServerFlagTest
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
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
  var service = ServerFlagServiceEmpty

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
    ServerFlagServiceImpl.initializer = {
      ServerFlagInitializer.initializeService(localPath, VERSION, OS_NAME_MAC, AndroidStudioEvent.IdeBrand.ANDROID_STUDIO, EXPERIMENTS)
    }
    val service = ServerFlagServiceImpl()

    service.apply {
      assertThat(getBoolean("boolean")).isNull()
      assertThat(getInt("int")).isNull()
      assertThat(getFloat("float")).isNull()
      assertThat(getString("string")).isNull()
      assertThat(getString("linux")).isNull()
    }
  }

  fun testPercentEnabled() {
    val expected = serverFlagTestData

    ServerFlagServiceImpl.initializer = { ServerFlagInitializer.initializeService(localPath, VERSION, OS_NAME_MAC, AndroidStudioEvent.IdeBrand.ANDROID_STUDIO, emptyList()) }
    saveServerFlagList(expected, localPath, VERSION)
    val service = ServerFlagServiceImpl()

    service.apply {
      assertThat(getBoolean("boolean")).isNull()
      assertThat(getInt("int")).isNull()
      assertThat(getFloat("float")).isEqualTo(1f)
      assertThat(getString("string")).isEqualTo("foo")
      assertThat(getString("linux")).isNull()
    }
  }

  fun testMac() {
    testOsType(OS_NAME_MAC, OSType.OS_TYPE_MAC)
  }

  fun testWin() {
    testOsType(OS_NAME_WINDOWS, OSType.OS_TYPE_WIN)
  }

  fun testLinux() {
    testOsType(OS_NAME_LINUX, OSType.OS_TYPE_LINUX)
  }

  fun testChromium() {
    testOsType(OS_NAME_CHROMIUM, OSType.OS_TYPE_CHROMIUM)
  }

  fun testFreeBSD() {
    testOsType(OS_NAME_FREE_BSD, OSType.OS_TYPE_FREE_BSD)
  }

  fun testAndroidStudio() {
    testBrand(AndroidStudioEvent.IdeBrand.ANDROID_STUDIO, Brand.BRAND_ANDROID_STUDIO)
  }

  fun testAndroidStudioWithBlaze() {
    testBrand(AndroidStudioEvent.IdeBrand.ANDROID_STUDIO_WITH_BLAZE, Brand.BRAND_ANDROID_STUDIO_WITH_BLAZE)
  }

  private fun testOsType(osName: String, osType: OSType) {
    saveServerFlagList(serverFlagTestDataByOs, localPath, VERSION)

    ServerFlagServiceImpl.initializer = { ServerFlagInitializer.initializeService(localPath, VERSION, osName, AndroidStudioEvent.IdeBrand.ANDROID_STUDIO, emptyList()) }
    val service = ServerFlagServiceImpl()
    assertThat(service.names).containsExactlyElementsIn(listOf(osType.toString()))
  }

  private fun testBrand(brand: AndroidStudioEvent.IdeBrand, filterBy: Brand) {
    saveServerFlagList(serverFlagTestDataByBrand, localPath, VERSION)

    ServerFlagServiceImpl.initializer = { ServerFlagInitializer.initializeService(localPath, VERSION, OS_NAME_MAC, brand, emptyList()) }
    val service = ServerFlagServiceImpl()
    assertThat(service.names).containsExactlyElementsIn(listOf(filterBy.toString()))
  }

  private fun testServerFlagInitializer(expected: ServerFlagList) {
    ServerFlagServiceImpl.initializer = { ServerFlagInitializer.initializeService(localPath, VERSION, OS_NAME_MAC, AndroidStudioEvent.IdeBrand.ANDROID_STUDIO, EXPERIMENTS) }
    val service = ServerFlagServiceImpl()

    service.apply {
      assertThat(getBoolean("boolean")).isEqualTo(true)
      assertThat(getInt("int")).isEqualTo(1)
      assertThat(getFloat("float")).isNull()
      assertThat(getFloat("string")).isNull()
      assertThat(getProto(name, TEST_PROTO).content).isEqualTo("content")
      assertThat(getString("linux")).isNull()
    }

    val actual = loadServerFlagList(localPath, VERSION)
    assertThat(actual).isEqualTo(expected)
  }
}