/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.datastore.energy

import com.android.tools.profiler.proto.EnergyProfiler
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit

class BatteryModelTest {
  companion object {
    private val SAMPLE_INTERVAL_NS = TimeUnit.MILLISECONDS.toNanos(200)
  }

  /**
   * Given some time [timeNs], skip past [numSamples] sample buckets, returning the next time that
   * can be used to fetch the next sample after that.
   */
  private fun fastForward(timeNs: Long, numSamples: Int): Long {
    return timeNs + (numSamples * SAMPLE_INTERVAL_NS)
  }

  private fun BatteryModel.getNSamplesStartingAt(timeNs: Long, numSamples: Int): List<EnergyProfiler.EnergySample> {
    return getSamplesBetween(timeNs, fastForward(timeNs, numSamples))
  }

  private fun assertNoPowerUsage(sample: EnergyProfiler.EnergySample) {
    assertThat(sample.cpuUsage).isEqualTo(0)
    assertThat(sample.networkUsage).isEqualTo(0)
  }

  @Test
  fun alignmentWorks() {
    assertThat(BatteryModel.align(193, 200)).isEqualTo(200)
    assertThat(BatteryModel.align(299, 200)).isEqualTo(200)
    assertThat(BatteryModel.align(301, 200)).isEqualTo(400)
    assertThat(BatteryModel.align(1234, 200)).isEqualTo(1200)

    assertThat(BatteryModel.align(299, 1)).isEqualTo(299)
    assertThat(BatteryModel.align(299, 600)).isEqualTo(0)
  }

  @Test
  fun newBatteryModelReturnsDefaultSamples() {
    val batteryModel = BatteryModel(PowerProfile.DefaultPowerProfile(), SAMPLE_INTERVAL_NS)
    val timeStartNs = TimeUnit.SECONDS.toNanos(123)
    val samples = batteryModel.getNSamplesStartingAt(timeStartNs, 2)

    assertThat(samples).hasSize(2)
    assertThat(samples[0].timestamp).isEqualTo(timeStartNs)
    assertNoPowerUsage(samples[0])

    assertThat(samples[1].timestamp).isEqualTo(samples[0].timestamp + SAMPLE_INTERVAL_NS)
    assertNoPowerUsage(samples[1])
  }

  @Test
  fun newBatteryConvertsOneEventIntoRepeatedSamples() {
    val batteryModel = BatteryModel(PowerProfile.DefaultPowerProfile(), SAMPLE_INTERVAL_NS)
    var timeCurrNs = TimeUnit.SECONDS.toNanos(321)

    run {
      val samples = batteryModel.getNSamplesStartingAt(timeCurrNs, 2)

      assertThat(samples[0].timestamp).isEqualTo(timeCurrNs)
      assertNoPowerUsage(samples[0])

      assertThat(samples[1].timestamp).isGreaterThan(samples[0].timestamp)
      assertNoPowerUsage(samples[1])

      timeCurrNs = fastForward(timeCurrNs, samples.size)
    }


    run {
      batteryModel.handleEvent(timeCurrNs, BatteryModel.Event.CPU_USAGE, 0.5)
      val samples = batteryModel.getNSamplesStartingAt(timeCurrNs, 3)

      assertThat(samples[0].timestamp).isEqualTo(timeCurrNs)
      assertThat(samples[1].cpuUsage).isGreaterThan(0)

      assertThat(samples[1].timestamp).isGreaterThan(samples[0].timestamp)
      assertThat(samples[1].cpuUsage).isEqualTo(samples[0].cpuUsage)

      assertThat(samples[2].timestamp).isGreaterThan(samples[1].timestamp)
      assertThat(samples[2].cpuUsage).isEqualTo(samples[0].cpuUsage)

      timeCurrNs = fastForward(timeCurrNs, samples.size)
      batteryModel.handleEvent(timeCurrNs, BatteryModel.Event.CPU_USAGE, 0.0)
    }

    run {
      val downloadStartNs = timeCurrNs
      val uploadStartNs = fastForward(timeCurrNs, 2)

      batteryModel.handleEvent(downloadStartNs, BatteryModel.Event.NETWORK_TYPE_CHANGED, PowerProfile.NetworkType.WIFI)
      batteryModel.handleEvent(downloadStartNs, BatteryModel.Event.NETWORK_DOWNLOAD, true)
      batteryModel.handleEvent(uploadStartNs, BatteryModel.Event.NETWORK_DOWNLOAD, false)
      batteryModel.handleEvent(uploadStartNs, BatteryModel.Event.NETWORK_UPLOAD, true)

      val samples = batteryModel.getNSamplesStartingAt(timeCurrNs, 4)

      assertThat(samples[0].timestamp).isEqualTo(timeCurrNs)
      assertThat(samples[0].networkUsage).isGreaterThan(0)

      assertThat(samples[1].timestamp).isGreaterThan(samples[0].timestamp)
      assertThat(samples[1].networkUsage).isEqualTo(samples[0].networkUsage)

      assertThat(samples[2].timestamp).isGreaterThan(samples[1].timestamp)
      assertThat(samples[2].networkUsage).isGreaterThan(0)

      assertThat(samples[3].timestamp).isGreaterThan(samples[2].timestamp)
      assertThat(samples[3].networkUsage).isEqualTo(samples[2].networkUsage)

      timeCurrNs = fastForward(timeCurrNs, samples.size)

      batteryModel.handleEvent(timeCurrNs, BatteryModel.Event.NETWORK_UPLOAD, false)
      batteryModel.handleEvent(timeCurrNs, BatteryModel.Event.NETWORK_TYPE_CHANGED, PowerProfile.NetworkType.NONE)
    }


    // Final assert should check that the battery model could be returned back to default state.
    run {
      val samples = batteryModel.getNSamplesStartingAt(timeCurrNs, 1)
      assertNoPowerUsage(samples[0])
    }
  }

  @Test
  fun samplesAtOrAroundTheSameTimeAreMergedIntoTheSameBucket() {
    val batteryModel = BatteryModel(PowerProfile.DefaultPowerProfile(), SAMPLE_INTERVAL_NS)
    val timeCurrNs = TimeUnit.SECONDS.toNanos(9999)

    batteryModel.handleEvent(timeCurrNs, BatteryModel.Event.NETWORK_TYPE_CHANGED, PowerProfile.NetworkType.WIFI)
    batteryModel.handleEvent(timeCurrNs + 1, BatteryModel.Event.NETWORK_DOWNLOAD, true)
    batteryModel.handleEvent(timeCurrNs + 2, BatteryModel.Event.NETWORK_DOWNLOAD, false)
    batteryModel.handleEvent(timeCurrNs + 3, BatteryModel.Event.NETWORK_TYPE_CHANGED, PowerProfile.NetworkType.NONE)
    batteryModel.handleEvent(timeCurrNs + 3, BatteryModel.Event.CPU_USAGE, 1.0)

    val sample = batteryModel.getNSamplesStartingAt(timeCurrNs, 1)[0]

    assertThat(sample.timestamp).isEqualTo(timeCurrNs)
    assertThat(sample.networkUsage).isEqualTo(0)
    assertThat(sample.cpuUsage).isGreaterThan(0)
  }
}