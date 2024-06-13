/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.metrics.statistics

import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorSession
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

class AttachStatisticsTest {
  @Test
  fun testAttachDurationOnSuccess() {
    val startTime = Instant.EPOCH
    val mutableClock = MutableClock(startTime, ZoneOffset.UTC)

    val attachStatistics =
      AttachStatistics(
        DynamicLayoutInspectorAttachToProcess.ClientType.APP_INSPECTION_CLIENT,
        multipleProjectsOpen = { false },
        isAutoConnectEnabled = { false },
        isEmbeddedLayoutInspector = { false },
        clock = mutableClock,
      )
    attachStatistics.start()

    mutableClock.advanceTimeBySeconds(2)

    attachStatistics.attachSuccess()

    val data = DynamicLayoutInspectorSession.newBuilder()
    attachStatistics.save { data.attachBuilder }

    assertThat(data.attachBuilder.attachDurationMs).isEqualTo(2000)
  }

  @Test
  fun testAttachDurationOnError() {
    val startTime = Instant.EPOCH
    val mutableClock = MutableClock(startTime, ZoneOffset.UTC)

    val attachStatistics =
      AttachStatistics(
        DynamicLayoutInspectorAttachToProcess.ClientType.APP_INSPECTION_CLIENT,
        multipleProjectsOpen = { false },
        isAutoConnectEnabled = { false },
        isEmbeddedLayoutInspector = { false },
        clock = mutableClock,
      )
    attachStatistics.start()

    mutableClock.advanceTimeBySeconds(2)

    attachStatistics.attachError(
      DynamicLayoutInspectorErrorInfo.AttachErrorCode.TRANSPORT_UNKNOWN_ERROR
    )

    val data = DynamicLayoutInspectorSession.newBuilder()
    attachStatistics.save { data.attachBuilder }

    assertThat(data.attachBuilder.attachDurationMs).isEqualTo(2000)
  }

  @Test
  fun testAttachDurationOnAttachCancel() {
    val startTime = Instant.EPOCH
    val mutableClock = MutableClock(startTime, ZoneOffset.UTC)

    val attachStatistics =
      AttachStatistics(
        DynamicLayoutInspectorAttachToProcess.ClientType.APP_INSPECTION_CLIENT,
        multipleProjectsOpen = { false },
        isAutoConnectEnabled = { false },
        isEmbeddedLayoutInspector = { false },
        clock = mutableClock,
      )
    attachStatistics.start()

    mutableClock.advanceTimeBySeconds(2)

    val data = DynamicLayoutInspectorSession.newBuilder()
    attachStatistics.save { data.attachBuilder }

    assertThat(data.attachBuilder.attachDurationMs).isEqualTo(2000)
  }
}

private class MutableClock(private var currentInstant: Instant, private val zone: ZoneId) :
  Clock() {
  fun advanceTimeBySeconds(seconds: Long) {
    currentInstant = currentInstant.plusSeconds(seconds)
  }

  override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)

  override fun getZone(): ZoneId = zone

  override fun instant(): Instant = currentInstant
}
