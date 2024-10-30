/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.flags.Flag
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
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
class FlagBasedDevServicesCompatibilityProviderTest : BasePlatformTestCase() {
  @Test
  fun `build older than 2 years returns UNSUPPORTED`() {
    val provider = FlagBasedDevServicesCompatibilityProvider(
      clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"))
    )
    setApplicationInfoWithBuildDate(LocalDateTime.of(2021, 1, 1, 0, 0))

    val actual = FlagOverride(StudioFlags.DEV_SERVICES_SUPPORTED_V1, true).use {
      FlagOverride(StudioFlags.DEV_SERVICES_DEPRECATED_V1, true).use {
        provider.currentSupportStatus()
      }
    }

    assertThat(actual).isEqualTo(DevServicesSupportStatus.UNSUPPORTED)
  }

  @Test
  fun `supported flag false returns UNSUPPORTED`() {
    val provider = FlagBasedDevServicesCompatibilityProvider(
      clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))
    )
    setApplicationInfoWithBuildDate(LocalDateTime.now())

    val actual = FlagOverride(StudioFlags.DEV_SERVICES_SUPPORTED_V1, false).use {
      // deprecatedFlag value doesn't matter in this case
      FlagOverride(StudioFlags.DEV_SERVICES_DEPRECATED_V1, true).use {
        provider.currentSupportStatus()
      }
    }

    assertThat(actual).isEqualTo(DevServicesSupportStatus.UNSUPPORTED)
  }

  @Test
  fun `deprecated flag true returns DEPRECATED`() {
    val provider = FlagBasedDevServicesCompatibilityProvider(
      clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))
    )
    setApplicationInfoWithBuildDate(LocalDateTime.now().minusMonths(6))

    val actual = FlagOverride(StudioFlags.DEV_SERVICES_SUPPORTED_V1, true).use {
      FlagOverride(StudioFlags.DEV_SERVICES_DEPRECATED_V1, true).use {
        provider.currentSupportStatus()
      }
    }

    assertThat(actual).isEqualTo(DevServicesSupportStatus.DEPRECATED)
  }

  @Test
  fun `default SUPPORTED scenario`() {
    val provider = FlagBasedDevServicesCompatibilityProvider(
      clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))
    )
    setApplicationInfoWithBuildDate(LocalDateTime.now())

    val actual = FlagOverride(StudioFlags.DEV_SERVICES_SUPPORTED_V1, true).use {
      FlagOverride(StudioFlags.DEV_SERVICES_DEPRECATED_V1, false).use {
        provider.currentSupportStatus()
      }
    }

    assertThat(actual).isEqualTo(DevServicesSupportStatus.SUPPORTED)
  }

  fun setApplicationInfoWithBuildDate(buildDate: LocalDateTime) {
    ApplicationManager.getApplication().replaceService(
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

private class FlagOverride(private val flag: Flag<Boolean>, private val value: Boolean) : AutoCloseable {
  init {
    flag.override(value)
  }

  override fun close() {
    flag.clearOverride()
  }
}