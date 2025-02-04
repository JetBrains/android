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
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ServerFlagBasedDevServicesDeprecationDataProviderTest : BasePlatformTestCase() {

  private var isServiceDeprecated = false
  private var protoToReturn: Message =
    DevServicesDeprecationMetadata.newBuilder()
      .apply {
        header = "header"
        description = "description"
        moreInfoUrl = "moreInfo"
        showUpdateAction = true
      }
      .build()

  private val fakeServerFlagService =
    object : ServerFlagService {
      override val configurationVersion: Long = 0
      override val flagAssignments: Map<String, Int> = emptyMap()

      override fun <T : Message> getProtoOrNull(name: String, instance: T) =
        if (isServiceDeprecated) {
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
      testRootDisposable,
    )
  }

  @Test
  fun `proto missing in ServerFlag returns SUPPORTED`() {
    isServiceDeprecated = false
    assertThat(
        ServerFlagService.instance.getProtoOrNull(
          "service",
          DevServicesDeprecationMetadata.getDefaultInstance(),
        )
      )
      .isNull()

    val provider = ServerFlagBasedDevServicesDeprecationDataProvider()

    val deprecationData = provider.getCurrentDeprecationData("service")

    assertThat(deprecationData.status).isEqualTo(DevServicesDeprecationStatus.SUPPORTED)
  }

  @Test
  fun `proto available in ServerFlag returns DEPRECATED`() {
    isServiceDeprecated = true
    assertThat(
        ServerFlagService.instance.getProtoOrNull(
          "service",
          DevServicesDeprecationMetadata.getDefaultInstance(),
        )
      )
      .isNotNull()

    val provider = ServerFlagBasedDevServicesDeprecationDataProvider()

    val deprecationData = provider.getCurrentDeprecationData("service")

    assertThat(deprecationData.status).isEqualTo(DevServicesDeprecationStatus.UNSUPPORTED)
  }

  @Test
  fun `proto with missing values returns DEPRECATED`() {
    isServiceDeprecated = true
    assertThat(
        ServerFlagService.instance.getProtoOrNull(
          "service",
          DevServicesDeprecationMetadata.getDefaultInstance(),
        )
      )
      .isNotNull()

    protoToReturn = DevServicesDeprecationMetadata.newBuilder().apply { header = "header" }.build()
    val provider = ServerFlagBasedDevServicesDeprecationDataProvider()

    val deprecationData = provider.getCurrentDeprecationData("service")

    assertThat(deprecationData.status).isEqualTo(DevServicesDeprecationStatus.UNSUPPORTED)
  }
}
