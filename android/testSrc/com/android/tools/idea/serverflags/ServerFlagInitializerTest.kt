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
import com.android.tools.idea.serverflags.protos.FlagValue
import com.android.tools.idea.serverflags.protos.MultiValueServerFlag
import com.android.tools.idea.serverflags.protos.OSType
import com.android.tools.idea.serverflags.protos.ServerFlagList
import com.android.tools.idea.serverflags.protos.ServerFlagTest
import com.android.tools.idea.testing.TemporaryDirectoryRule
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Any
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Path
import junit.framework.TestCase
import org.junit.Rule
import org.junit.Test

private const val VERSION = "4.2.0.0"
private val EXPERIMENTS = mapOf("boolean" to 0, "int" to 0)
private val TEST_PROTO = ServerFlagTest.newBuilder().apply { content = "content" }.build()

class ServerFlagInitializerTest : TestCase() {
  private lateinit var testDirectoryPath: Path
  private lateinit var localPath: Path

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
      ServerFlagInitializer.initializeService(
        localPath,
        VERSION,
        OS_NAME_MAC,
        AndroidStudioEvent.IdeBrand.ANDROID_STUDIO,
        EXPERIMENTS,
        false,
      )
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

    ServerFlagServiceImpl.initializer = {
      ServerFlagInitializer.initializeService(
        localPath,
        VERSION,
        OS_NAME_MAC,
        AndroidStudioEvent.IdeBrand.ANDROID_STUDIO,
        emptyMap(),
        false,
      )
    }
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

  fun testOverrideFlags() {
    saveServerFlagList(serverFlagTestData, localPath, VERSION)
    with(
      ServerFlagInitializer.initializeService(
        localPath,
        VERSION,
        OS_NAME_MAC,
        AndroidStudioEvent.IdeBrand.ANDROID_STUDIO,
        mapOf("boolean" to 0, "string" to 0),
        false,
      ) {
        0
      }
    ) {
      assertThat(flags).hasSize(2)
      assertThat(flags["boolean"]?.booleanValue).isTrue()
      assertThat(flags["string"]?.stringValue).isEqualTo("foo")
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
    testBrand(
      AndroidStudioEvent.IdeBrand.ANDROID_STUDIO_WITH_BLAZE,
      Brand.BRAND_ANDROID_STUDIO_WITH_BLAZE,
    )
  }

  private fun testOsType(osName: String, osType: OSType) {
    saveServerFlagList(serverFlagTestDataByOs, localPath, VERSION)

    ServerFlagServiceImpl.initializer = {
      ServerFlagInitializer.initializeService(
        localPath,
        VERSION,
        osName,
        AndroidStudioEvent.IdeBrand.ANDROID_STUDIO,
        emptyMap(),
        false,
      )
    }
    val service = ServerFlagServiceImpl()
    assertThat(service.names).containsExactlyElementsIn(listOf(osType.toString()))
  }

  private fun testBrand(brand: AndroidStudioEvent.IdeBrand, filterBy: Brand) {
    saveServerFlagList(serverFlagTestDataByBrand, localPath, VERSION)

    ServerFlagServiceImpl.initializer = {
      ServerFlagInitializer.initializeService(
        localPath,
        VERSION,
        OS_NAME_MAC,
        brand,
        emptyMap(),
        false,
      )
    }
    val service = ServerFlagServiceImpl()
    assertThat(service.names).containsExactlyElementsIn(listOf(filterBy.toString()))
  }

  private fun testServerFlagInitializer(expected: ServerFlagList) {
    ServerFlagServiceImpl.initializer = {
      ServerFlagInitializer.initializeService(
        localPath,
        VERSION,
        OS_NAME_MAC,
        AndroidStudioEvent.IdeBrand.ANDROID_STUDIO,
        EXPERIMENTS,
        false,
      )
    }
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

class MultiValueServerFlagInitializerTest {

  @get:Rule val dirRule = TemporaryDirectoryRule()

  private val multiValueFlag: List<MultiValueServerFlag> =
    listOf(
      MultiValueServerFlag.newBuilder()
        .apply {
          addAllFlagValues(
            listOf(
              FlagValue.newBuilder()
                .apply {
                  percentEnabled = 20
                  booleanValue = true
                }
                .build(),
              FlagValue.newBuilder()
                .apply {
                  percentEnabled = 20
                  intValue = 10
                }
                .build(),
              FlagValue.newBuilder()
                .apply {
                  percentEnabled = 20
                  floatValue = 2f
                }
                .build(),
              FlagValue.newBuilder()
                .apply {
                  percentEnabled = 20
                  stringValue = "flagValue"
                }
                .build(),
              FlagValue.newBuilder()
                .apply {
                  percentEnabled = 20
                  protoValue =
                    Any.pack(
                      ServerFlagTest.newBuilder().apply { content = "flagValueContent" }.build()
                    )
                }
                .build(),
            )
          )
        }
        .build(),
      MultiValueServerFlag.newBuilder()
        .apply {
          addAllFlagValues(
            listOf(
              FlagValue.newBuilder()
                .apply {
                  percentEnabled = 20
                  booleanValue = true
                }
                .build()
            )
          )
        }
        .build(),
      MultiValueServerFlag.newBuilder()
        .apply {
          addAllFlagValues(
            listOf(
              FlagValue.newBuilder()
                .apply {
                  percentEnabled = 20
                  stringValue = "flagValue"
                }
                .build(),
              FlagValue.newBuilder()
                .apply {
                  percentEnabled = 20
                  stringValue = "flagValue2"
                }
                .build(),
              FlagValue.newBuilder()
                .apply {
                  percentEnabled = 20
                  stringValue = "flagValue3"
                }
                .build(),
            )
          )
        }
        .build(),
    )

  private fun makeFlagList(): ServerFlagList {
    val flagData =
      listOf(
        makeServerFlagData("invalid", multiValueFlag[0]),
        makeServerFlagData("singleValue", multiValueFlag[1]),
        makeServerFlagData("multiValue", multiValueFlag[2]),
      )

    val builder = ServerFlagList.newBuilder().apply { configurationVersion = 1 }
    builder.addAllServerFlags(flagData)
    return builder.build()
  }

  @Test
  fun `check value types of multi value flags`() {
    val localPath = dirRule.newPath()
    saveServerFlagList(makeFlagList(), localPath, VERSION)
    with(
      ServerFlagInitializer.initializeService(
        localPath,
        VERSION,
        OS_NAME_MAC,
        AndroidStudioEvent.IdeBrand.ANDROID_STUDIO,
        emptyMap(),
        true,
      ) {
        0
      }
    ) {
      assertThat(flags).hasSize(2)
      assertThat(flags["invalid"]).isNull()
    }
  }

  @Test
  fun `verify flag assigned based on percentEnabled`() {
    val localPath = dirRule.newPath()
    var hash = 15
    val hashFunction = { _: String -> hash }

    ServerFlagServiceImpl.initializer = {
      ServerFlagInitializer.initializeService(
        localPath,
        VERSION,
        OS_NAME_MAC,
        AndroidStudioEvent.IdeBrand.ANDROID_STUDIO,
        emptyMap(),
        true,
        hashFunction,
      )
    }
    saveServerFlagList(makeFlagList(), localPath, VERSION)

    hash = 15
    with(ServerFlagServiceImpl()) {
      assertThat(getString("multiValue")).isEqualTo("flagValue")
      assertThat(getBoolean("singleValue")).isTrue()
    }
    hash = 21
    with(ServerFlagServiceImpl()) {
      assertThat(getString("multiValue")).isEqualTo("flagValue2")
      assertThat(getBoolean("singleValue")).isNull()
    }
    hash = 59
    with(ServerFlagServiceImpl()) {
      assertThat(getString("multiValue")).isEqualTo("flagValue3")
      assertThat(getBoolean("singleValue")).isNull()
    }
    hash = 66
    with(ServerFlagServiceImpl()) {
      assertThat(getString("multiValue1")).isNull()
      assertThat(getBoolean("singleValue")).isNull()
    }
    hash = 99
    with(ServerFlagServiceImpl()) {
      assertThat(getString("multiValue1")).isNull()
      assertThat(getBoolean("singleValue")).isNull()
    }
  }

  @Test
  fun `verify override param`() {
    val localPath = dirRule.newPath()
    saveServerFlagList(makeFlagList(), localPath, VERSION)
    with(
      ServerFlagInitializer.initializeService(
        localPath,
        VERSION,
        OS_NAME_MAC,
        AndroidStudioEvent.IdeBrand.ANDROID_STUDIO,
        mapOf("multiValue" to 1, "singleValue" to 0),
        true,
      )
    ) {
      assertThat(flags).hasSize(2)
      assertThat(flags["multiValue"]?.hasStringValue()).isTrue()
      assertThat(flags["singleValue"]?.hasBooleanValue()).isTrue()
    }
  }
}
