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

import com.android.flags.junit.FlagRule
import com.android.testutils.delayUntilCondition
import com.android.testutils.waitForCondition
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.serverflags.DynamicServerFlagService
import com.android.tools.idea.serverflags.FakeDynamicServerFlagService
import com.android.tools.idea.serverflags.FakeServerFlagService
import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.Date
import com.android.tools.idea.serverflags.protos.DevServicesDeprecationMetadata
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import com.intellij.util.text.DateTimeFormatManager
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ServerFlagBasedDevServicesDeprecationDataProviderTest : BasePlatformTestCase() {

  @get:Rule val flagRule = FlagRule(StudioFlags.USE_POLICY_WITH_DEPRECATE, true)
  private val fakeServerFlagService = FakeServerFlagService()
  private val fakeDynamicServerFlagService = FakeDynamicServerFlagService { emptyMap() }
  private lateinit var provider: ServerFlagBasedDevServicesDeprecationDataProvider

  @Before
  fun setup() {
    provider =
      ServerFlagBasedDevServicesDeprecationDataProvider(testRootDisposable.createCoroutineScope())
    application.replaceService(
      ServerFlagService::class.java,
      fakeServerFlagService,
      testRootDisposable,
    )
    application.replaceService(
      DynamicServerFlagService::class.java,
      fakeDynamicServerFlagService,
      testRootDisposable,
    )
  }

  @Test
  fun `proto missing in ServerFlag returns SUPPORTED`() {
    assertThat(
        ServerFlagService.instance.getProtoOrNull(
          "service",
          DevServicesDeprecationMetadata.getDefaultInstance(),
        )
      )
      .isNull()

    val deprecationData = provider.getCurrentDeprecationData("service")
    assertThat(deprecationData.status).isEqualTo(DevServicesDeprecationStatus.SUPPORTED)
  }

  @Test
  fun `proto available in ServerFlag returns DEPRECATED`() {
    StudioFlags.USE_POLICY_WITH_DEPRECATE.override(false)
    registerServiceProto(
      DevServicesDeprecationMetadata.newBuilder().apply { header = "header" }.build()
    )
    assertThat(
        ServerFlagService.instance.getProtoOrNull(
          "dev_services/service",
          DevServicesDeprecationMetadata.getDefaultInstance(),
        )
      )
      .isNotNull()

    val deprecationData = provider.getCurrentDeprecationData("service")
    assertThat(deprecationData.status).isEqualTo(DevServicesDeprecationStatus.UNSUPPORTED)
  }

  @Test
  fun `proto with missing values returns DEPRECATED`() {
    StudioFlags.USE_POLICY_WITH_DEPRECATE.override(false)
    assertThat(
        ServerFlagService.instance.getProto(
          "dev_services/service",
          DevServicesDeprecationMetadata.getDefaultInstance(),
        )
      )
      .isNotNull()

    registerServiceProto(
      DevServicesDeprecationMetadata.newBuilder().apply { header = "header" }.build()
    )

    val deprecationData = provider.getCurrentDeprecationData("service")
    assertThat(deprecationData.status).isEqualTo(DevServicesDeprecationStatus.UNSUPPORTED)
  }

  @Test
  fun `proto description returns substituted string`() {
    val serviceProto =
      DevServicesDeprecationMetadata.newBuilder()
        .apply {
          description = "<service_name> will be substituted. So will <date>"
          deprecationDate =
            Date.newBuilder()
              .apply {
                year = 2025
                month = 1
                day = 1
              }
              .build()
        }
        .build()
    registerServiceProto(serviceProto)

    var deprecationData = provider.getCurrentDeprecationData("service")
    assertThat(deprecationData.description)
      .isEqualTo("This service will be substituted. So will ${deprecationData.formattedDate()}")

    deprecationData = provider.getCurrentDeprecationData("service", "UserFriendlyName")
    assertThat(deprecationData.description)
      .isEqualTo("UserFriendlyName will be substituted. So will ${deprecationData.formattedDate()}")

    registerServiceProto(
      DevServicesDeprecationMetadata.newBuilder()
        .apply { description = "<service_name> will be substituted. So will <date>" }
        .build()
    )

    deprecationData = provider.getCurrentDeprecationData("service")
    assertThat(deprecationData.description).isEqualTo("This service will be substituted. So will ")
  }

  @Test
  fun `proto header returns substituted string`() {
    val serviceProto =
      DevServicesDeprecationMetadata.newBuilder()
        .apply {
          header = "<service_name> will be substituted. So will <date>"
          deprecationDate =
            Date.newBuilder()
              .apply {
                year = 2025
                month = 1
                day = 1
              }
              .build()
        }
        .build()
    registerServiceProto(serviceProto)

    var deprecationData = provider.getCurrentDeprecationData("service")
    assertThat(deprecationData.header)
      .isEqualTo("This service will be substituted. So will ${deprecationData.formattedDate()}")

    deprecationData = provider.getCurrentDeprecationData("service", "UserFriendlyName")
    assertThat(deprecationData.header)
      .isEqualTo("UserFriendlyName will be substituted. So will ${deprecationData.formattedDate()}")

    registerServiceProto(
      DevServicesDeprecationMetadata.newBuilder()
        .apply { header = "<service_name> will be substituted. So will <date>" }
        .build()
    )

    deprecationData = provider.getCurrentDeprecationData("service")
    assertThat(deprecationData.header).isEqualTo("This service will be substituted. So will ")
  }

  @Test
  fun `service proto takes precedence over studio proto`() {
    registerServiceProto(
      DevServicesDeprecationMetadata.newBuilder().apply { header = "ServiceProto" }.build()
    )
    registerStudioProto(
      DevServicesDeprecationMetadata.newBuilder().apply { header = "StudioProto" }.build()
    )

    val deprecationData = provider.getCurrentDeprecationData("service")
    assertThat(deprecationData.header).isEqualTo("ServiceProto")
  }

  @Test
  fun `studio proto returned when service proto not available`() {
    registerStudioProto(
      DevServicesDeprecationMetadata.newBuilder().apply { header = "StudioProto" }.build()
    )

    val deprecationData = provider.getCurrentDeprecationData("service")
    assertThat(deprecationData.header).isEqualTo("StudioProto")
  }

  @Test
  fun `date is formatted to user locale`() {
    val studioProto =
      DevServicesDeprecationMetadata.newBuilder()
        .apply {
          description = "<date>"
          deprecationDate =
            Date.newBuilder()
              .apply {
                year = 2025
                month = 1
                day = 15
              }
              .build()
        }
        .build()

    withDateFormatOverride("dd MMM yyyy").use {
      registerStudioProto(studioProto)
      val deprecationData = provider.getCurrentDeprecationData("service")
      assertThat(deprecationData.description).isEqualTo("15 Jan 2025")
    }

    withDateFormatOverride("dd/MM/yyyy").use {
      registerStudioProto(studioProto)
      val deprecationData = provider.getCurrentDeprecationData("service")
      assertThat(deprecationData.description).isEqualTo("15/01/2025")
    }

    withDateFormatOverride("MM/dd/yy").use {
      registerStudioProto(studioProto)
      val deprecationData = provider.getCurrentDeprecationData("service")
      assertThat(deprecationData.description).isEqualTo("01/15/25")
    }
  }

  @Test
  fun `deprecation data contains default url when moreInfoUrl not provided`() {
    registerStudioProto(
      DevServicesDeprecationMetadata.newBuilder().apply { header = "StudioProto" }.build()
    )

    val deprecationData = provider.getCurrentDeprecationData("service")
    assertThat(deprecationData.moreInfoUrl).isEqualTo(StudioFlags.DEFAULT_MORE_INFO_URL.get())
  }

  @Test
  fun `registered listener gets notified on register`() = runTest {
    registerStudioProto(
      DevServicesDeprecationMetadata.newBuilder().apply { header = "StudioProto" }.build()
    )

    val stateFlow = provider.registerServiceForChange("service", disposable = testRootDisposable)
    assertThat(stateFlow.value).isEqualTo(DevServicesDeprecationData.EMPTY)
    delayUntilCondition(200) { stateFlow.value.header == "StudioProto" }
  }

  @Test
  fun `registered listener gets notified on changes`() = runTest {
    // Pass test scope to provider to skip delays
    provider = ServerFlagBasedDevServicesDeprecationDataProvider(this)
    val disposable = Disposer.newDisposable()
    registerStudioProto(
      DevServicesDeprecationMetadata.newBuilder().apply { header = "StudioProto" }.build()
    )

    val stateFlow = provider.registerServiceForChange("service", disposable = disposable)

    delayUntilCondition(200) { stateFlow.value != DevServicesDeprecationData.EMPTY }
    assertThat(stateFlow.value.header).isEqualTo("StudioProto")

    registerStudioProto(
      DevServicesDeprecationMetadata.newBuilder().apply { header = "StudioProtoChanged" }.build()
    )
    delayUntilCondition(200) { stateFlow.value.header == "StudioProtoChanged" }
    // Need to dispose the disposable because checkJob running in test scope prevents the test from
    // finishing
    Disposer.dispose(disposable)
  }

  @Test
  fun `listener unregistered when disposable is disposed`() = runTest {
    // Pass test scope to provider to skip delays
    provider = ServerFlagBasedDevServicesDeprecationDataProvider(this)
    val disposable = Disposer.newDisposable()
    registerStudioProto(
      DevServicesDeprecationMetadata.newBuilder().apply { header = "StudioProto" }.build()
    )

    val stateFlow = provider.registerServiceForChange("service", disposable = disposable)
    delayUntilCondition(200) { stateFlow.value.header == "StudioProto" }

    Disposer.dispose(disposable)
    registerStudioProto(
      DevServicesDeprecationMetadata.newBuilder().apply { header = "StudioProtoChanged" }.build()
    )

    try {
      waitForCondition(1.seconds) { stateFlow.value.header == "StudioProtoChanged" }
      fail("Statflow value should not have changed")
    } catch (_: TimeoutException) {}
  }

  private fun registerServiceProto(flag: Any) {
    registerFlag("dev_services/service", flag)
  }

  private fun registerStudioProto(flag: Any) {
    registerFlag("dev_services/studio", flag)
  }

  private fun registerFlag(name: String, flag: Any) {
    fakeDynamicServerFlagService.registerFlag(name, flag)
    fakeServerFlagService.registerFlag(name, flag)
  }

  @Suppress("UnstableApiUsage")
  private fun withDateFormatOverride(override: String) =
    object : AutoCloseable {
      private val originalPattern = DateTimeFormatManager.getInstance().dateFormatPattern
      private val originalSystemDateFormatOverride =
        DateTimeFormatManager.getInstance().isOverrideSystemDateFormat

      init {
        DateTimeFormatManager.getInstance().resetFormats()
        DateTimeFormatManager.getInstance().isOverrideSystemDateFormat = true
        DateTimeFormatManager.getInstance().dateFormatPattern = override
      }

      override fun close() {
        DateTimeFormatManager.getInstance().isOverrideSystemDateFormat =
          originalSystemDateFormatOverride
        DateTimeFormatManager.getInstance().dateFormatPattern = originalPattern
      }
    }
}
