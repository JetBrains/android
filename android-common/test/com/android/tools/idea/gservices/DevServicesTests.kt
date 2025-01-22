/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gservices

import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.DevServicesDeprecationMetadata
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Message
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.GregorianCalendar

@RunWith(JUnit4::class)
class ServerFlagBasedDevServicesDeprecationDataProviderTest : BasePlatformTestCase() {

  private var isServiceDeprecated = false
  private var protoToReturn: Message = DevServicesDeprecationMetadata.newBuilder().apply {
    header = "header"
    description = "description"
    moreInfoUrl = "moreInfo"
    showUpdateAction = true
  }.build()

  private val fakeServerFlagService = object : ServerFlagService {
    override val configurationVersion: Long = 0
    override val flagAssignments: Map<String, Int> = emptyMap()

    override fun <T : Message> getProtoOrNull(name: String, instance: T) = if (isServiceDeprecated) {
      protoToReturn as T
    } else {
      null
    }
  }

  @Before
  fun setup() {
    application.replaceService(
      ServerFlagService::class.java,
      fakeServerFlagService,
      testRootDisposable
    )
  }

  @Test
  fun `build older than 2 years returns DEPRECATED`() {
    // Service still active or flag not updated for service
    isServiceDeprecated = false
    val provider = ServerFlagBasedDevServicesDeprecationDataProvider(
      clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"))
    )
    setApplicationInfoWithBuildDate(LocalDateTime.of(2021, 1, 1, 0, 0))

    val deprecationData = provider.getCurrentDeprecationData("service")

    assertThat(deprecationData.status).isEqualTo(DevServicesDeprecationStatus.UNSUPPORTED)
  }

  @Test
  fun `proto missing in ServerFlag returns SUPPORTED`() {
    isServiceDeprecated = false
    assertThat(ServerFlagService.instance.getProtoOrNull("service", DevServicesDeprecationMetadata.getDefaultInstance())).isNull()

    val provider = ServerFlagBasedDevServicesDeprecationDataProvider(
      clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))
    )
    setApplicationInfoWithBuildDate(LocalDateTime.now())

    val deprecationData = provider.getCurrentDeprecationData("service")

    assertThat(deprecationData.status).isEqualTo(DevServicesDeprecationStatus.SUPPORTED)
  }

  @Test
  fun `proto available in ServerFlag returns DEPRECATED`() {
    isServiceDeprecated = true
    assertThat(ServerFlagService.instance.getProtoOrNull("service", DevServicesDeprecationMetadata.getDefaultInstance())).isNotNull()

    val provider = ServerFlagBasedDevServicesDeprecationDataProvider(
      clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))
    )
    setApplicationInfoWithBuildDate(LocalDateTime.now().minusMonths(6))

    val deprecationData = provider.getCurrentDeprecationData("service")

    assertThat(deprecationData.status).isEqualTo(DevServicesDeprecationStatus.UNSUPPORTED)
  }

  @Test
  fun `proto with missing values returns DEPRECATED`() {
    isServiceDeprecated = true
    assertThat(ServerFlagService.instance.getProtoOrNull("service", DevServicesDeprecationMetadata.getDefaultInstance())).isNotNull()

    protoToReturn = DevServicesDeprecationMetadata.newBuilder().apply { header = "header" }.build()
    val provider = ServerFlagBasedDevServicesDeprecationDataProvider(
      clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))
    )
    setApplicationInfoWithBuildDate(LocalDateTime.now())

    val deprecationData = provider.getCurrentDeprecationData("service")

    assertThat(deprecationData.status).isEqualTo(DevServicesDeprecationStatus.UNSUPPORTED)
  }

  fun setApplicationInfoWithBuildDate(buildDate: LocalDateTime) {
    application.replaceService(
      ApplicationInfo::class.java,
      FakeApplicationInfo(buildDate),
      testRootDisposable
    )
  }
}

class FakeApplicationInfo(val fakeBuildDate: LocalDateTime) : ApplicationInfo() {
  override fun getBuildDate(): Calendar {
    return GregorianCalendar(fakeBuildDate.year, fakeBuildDate.monthValue - 1, fakeBuildDate.dayOfMonth)
  }

  override fun getBuildTime(): ZonedDateTime {
    return fakeBuildDate.atZone(ZoneId.systemDefault())
  }

  override fun getBuild(): BuildNumber = BuildNumber("AI", 2023, 2, 3, 4)

  override fun getApiVersion() = "231"

  override fun getMajorVersion() = "2023"

  override fun getMinorVersion() = "2"

  override fun getMicroVersion() = "1"

  override fun getPatchVersion() = "1234567"

  override fun getVersionName() = "Android Studio Flamingo | 2022.3.1 Patch 2"

  override fun getCompanyName() = "Google"

  override fun getShortCompanyName() = "Google"

  override fun getCompanyURL() = "www.google.com"

  @Suppress("UnstableApiUsage")
  override fun getProductUrl() = null

  @Suppress("UnstableApiUsage")
  override fun getJetBrainsTvUrl() = null

  override fun hasHelp() = false

  override fun hasContextHelp() = false

  override fun getFullVersion() = "1.2.3.4"

  override fun getStrictVersion() = "1.2.3.4"

  override fun getFullApplicationName() = "fake.ide"

  override fun isEssentialPlugin(pluginId: String) = false

  override fun isEssentialPlugin(pluginId: PluginId) = false
}