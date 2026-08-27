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
package com.android.tools.idea.streaming.device

import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.devices.Abi.ARM64_V8A
import com.google.common.truth.Truth.assertThat
import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import com.intellij.util.ui.EmptyIcon
import kotlin.time.Duration.Companion.days
import org.junit.Test

/** Tests for [BitRateManager]. */
class BitRateManagerTest {

  private val bitRateManager = BitRateManager()
  private val deviceProperties = DeviceProperties.buildForTest {
    manufacturer = "Google"
    model = "Pixel 3a"
    androidVersion = AndroidVersion(30, 0)
    abiList = listOf(ARM64_V8A)
    icon = EmptyIcon.ICON_16
  }

  @Test
  fun testBitRateReduction() {
    assertThat(bitRateManager.getBitRate(deviceProperties)).isEqualTo(0) // Initial state.
    bitRateManager.bitRateReduced(2000000, deviceProperties)
    assertThat(bitRateManager.getBitRate(deviceProperties)).isEqualTo(0) // Not enough bit rate reductions to change the default.
    bitRateManager.bitRateReduced(1000000, deviceProperties)
    assertThat(bitRateManager.getBitRate(deviceProperties)).isEqualTo(0) // Not enough bit rate reductions to change the default.
    bitRateManager.bitRateReduced(5000000, deviceProperties)
    assertThat(bitRateManager.getBitRate(deviceProperties)).isEqualTo(5000000) // Bit rate was reduced to 5000000 or lower 3 times.
    bitRateManager.bitRateReduced(2000000, deviceProperties)
    assertThat(bitRateManager.getBitRate(deviceProperties)).isEqualTo(2000000) // Bit rate was reduced to 2000000 or lower 3 times.
  }

  @Test
  fun testOccasionalRateReduction() {
    assertThat(bitRateManager.getBitRate(deviceProperties)).isEqualTo(0) // Initial state.
    for (i in 0 until 1000) {
      bitRateManager.bitRateReduced(2000000, deviceProperties)
      for (j in 0 until 21) {
        bitRateManager.bitRateStable(5000000, deviceProperties)
      }
    }
    // 21 bitRateStable calls fully compensate a single bitRateReduced call.
    assertThat(bitRateManager.getBitRate(deviceProperties)).isEqualTo(0)

    for (i in 0 until 1000) {
      bitRateManager.bitRateReduced(2000000, deviceProperties)
      if (bitRateManager.getBitRate(deviceProperties) != 0) {
        break
      }
      for (j in 0 until 20) {
        bitRateManager.bitRateStable(5000000, deviceProperties)
      }
    }
    // 20 bitRateStable calls do not fully compensate a single bitRateReduced call.
    assertThat(bitRateManager.getBitRate(deviceProperties)).isEqualTo(2000000)
  }

  @Test
  fun testBitRateAging() {
    bitRateManager.bitRateReduced(1000000, deviceProperties)
    bitRateManager.bitRateReduced(1000000, deviceProperties)
    bitRateManager.bitRateReduced(1000000, deviceProperties)
    assertThat(bitRateManager.getBitRate(deviceProperties)).isEqualTo(1000000)

    val key = bitRateManager.key(deviceProperties)
    val tracker = bitRateManager.bitRateTrackers[key]!!
    val now = System.currentTimeMillis()
    val millis25Hours = 25 * 3600 * 1000L

    // Age by 25 hours -> bit rate increases from 1000000 to 2000000
    tracker.lastModifiedTime = now - millis25Hours
    assertThat(bitRateManager.getBitRate(deviceProperties)).isEqualTo(2000000)
    assertThat(tracker.lastModifiedTime).isGreaterThan(now - 10000L)

    // Age by another 25 hours -> bit rate increases from 2000000 to 5000000
    tracker.lastModifiedTime = now - millis25Hours
    assertThat(bitRateManager.getBitRate(deviceProperties)).isEqualTo(5000000)

    // Age by another 25 hours -> bit rate reaches 10000000 (DEFAULT_BIT_RATE), tracker is removed, getBitRate returns 0
    tracker.lastModifiedTime = now - millis25Hours
    assertThat(bitRateManager.getBitRate(deviceProperties)).isEqualTo(0)
    assertThat(bitRateManager.bitRateTrackers[key]).isNull()
  }

  @Test
  fun testBitRateExpiration() {
    val deviceProperties2 = DeviceProperties.buildForTest {
      manufacturer = "Google"
      model = "Pixel 6"
      androidVersion = AndroidVersion(33, 0)
      abiList = listOf(ARM64_V8A)
      icon = EmptyIcon.ICON_16
    }

    // Set bit rate for device 1.
    bitRateManager.bitRateReduced(2000000, deviceProperties)
    bitRateManager.bitRateReduced(2000000, deviceProperties)
    bitRateManager.bitRateReduced(2000000, deviceProperties)

    // Set bit rate for device 2.
    bitRateManager.bitRateReduced(1000000, deviceProperties2)
    bitRateManager.bitRateReduced(1000000, deviceProperties2)
    bitRateManager.bitRateReduced(1000000, deviceProperties2)
    bitRateManager.bitRateReduced(500000, deviceProperties2)

    val key1 = bitRateManager.key(deviceProperties)
    val key2 = bitRateManager.key(deviceProperties2)
    val now = System.currentTimeMillis()
    // Expire bit rate for device 2.
    bitRateManager.bitRateTrackers[key2]!!.lastModifiedTime = now - 366.days.inWholeMilliseconds

    val reloadedBitRateManager = BitRateManager()
    reloadedBitRateManager.loadState(bitRateManager)
    assertThat(reloadedBitRateManager.getBitRate(deviceProperties)).isEqualTo(2000000)
    assertThat(reloadedBitRateManager.getBitRate(deviceProperties2)).isEqualTo(0)
  }

  @Test
  fun testSerialization() {
    bitRateManager.bitRateReduced(2000000, deviceProperties)
    bitRateManager.bitRateReduced(1000000, deviceProperties)
    bitRateManager.bitRateReduced(5000000, deviceProperties)

    val element = serialize(bitRateManager)!!
    val deserialized = deserialize<BitRateManager>(element)
    assertThat(deserialized).isEqualTo(bitRateManager)
  }
}
