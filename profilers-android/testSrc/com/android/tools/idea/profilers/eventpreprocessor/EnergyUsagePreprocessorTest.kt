/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.profilers.eventpreprocessor

import com.android.tools.datastore.FakeLogService
import com.android.tools.datastore.energy.BatteryModel
import com.android.tools.datastore.energy.PowerProfile
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.Energy
import com.android.tools.profiler.proto.Network
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit

class EnergyUsagePreprocessorTest {
  companion object {
    private const val PID = 321
    private val SAMPLE_INTERVAL_NS = TimeUnit.MILLISECONDS.toNanos(200)
    private val ONE_MS = TimeUnit.MILLISECONDS.toNanos(1)
    private val CPU_CORE_CONFIG_EVENT = Common.Event.newBuilder()
      .setPid(PID)
      .setKind(Common.Event.Kind.CPU_CORE_CONFIG)
      .setCpuCoreConfig(Cpu.CpuCoreConfigData.newBuilder()
                          .addCoreConfigs(Cpu.CpuCoreConfig.newBuilder()
                                            .setCore(0)
                                            .setMinFrequencyInKhz(300000)
                                            .setMaxFrequencyInKhz(2457600)))
      .setTimestamp(0)
      .build()
    private val CPU_USAGE_EVENTS = arrayOf(
      Common.Event.newBuilder()
        .setPid(PID)
        .setKind(Common.Event.Kind.CPU_USAGE)
        .setCpuUsage(Cpu.CpuUsageData.getDefaultInstance())
        .setTimestamp(0)
        .build(),
      Common.Event.newBuilder()
        .setPid(PID)
        .setKind(Common.Event.Kind.CPU_USAGE)
        .setCpuUsage(Cpu.CpuUsageData.getDefaultInstance())
        .setTimestamp(ONE_MS * 200)
        .build(),
      Common.Event.newBuilder()
        .setPid(PID)
        .setKind(Common.Event.Kind.CPU_USAGE)
        .setCpuUsage(Cpu.CpuUsageData.getDefaultInstance())
        .setTimestamp(ONE_MS * 400)
        .build())
    private val NETWORK_TYPE_EVENT = Common.Event.newBuilder()
      .setPid(PID)
      .setKind(Common.Event.Kind.NETWORK_TYPE)
      .setNetworkType(Network.NetworkTypeData.newBuilder().setNetworkType(Network.NetworkTypeData.NetworkType.WIFI))
      .setTimestamp(0)
      .build()
    private val NETWORK_SPEED_EVENTS = arrayOf(
      Common.Event.newBuilder()
        .setPid(PID)
        .setGroupId(Common.Event.EventGroupIds.NETWORK_RX_VALUE.toLong())
        .setKind(Common.Event.Kind.NETWORK_SPEED)
        .setNetworkSpeed(Network.NetworkSpeedData.newBuilder().setThroughput(1))
        .setTimestamp(ONE_MS * 0)
        .build(),
      Common.Event.newBuilder()
        .setPid(PID)
        .setKind(Common.Event.Kind.NETWORK_SPEED)
        .setNetworkSpeed(Network.NetworkSpeedData.getDefaultInstance())
        .setTimestamp(ONE_MS * 400)
        .build())
    private val LOCATION_EVENTS = arrayOf(
      Common.Event.newBuilder()
        .setTimestamp(0)
        .setPid(PID)
        .setGroupId(1)
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setLocationChanged(
          Energy.LocationChanged.newBuilder().setLocation(Energy.Location.newBuilder().setProvider("gps"))
        ))
        .build(),

      Common.Event.newBuilder()
        .setTimestamp(ONE_MS * 200)
        .setPid(PID)
        .setGroupId(1)
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setLocationChanged(
          Energy.LocationChanged.newBuilder().setLocation(Energy.Location.newBuilder().setProvider("gps"))
        ))
        .build(),

      Common.Event.newBuilder()
        .setTimestamp(ONE_MS * 400)
        .setPid(PID)
        .setGroupId(1)
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setLocationChanged(
          Energy.LocationChanged.newBuilder().setLocation(Energy.Location.newBuilder().setProvider("gps"))
        ))
        .build()
    )
  }

  private class FakePowerProfile : PowerProfile {
    // Totally fake values, but chosen to make sure that different categories of power
    // will never overlap by accident.
    companion object {
      const val NETWORK_USAGE = 1
      const val GPS_USAGE = 10
      const val GPS_ACQUIRE_USAGE = 50
      const val CPU_USAGE = 100
      const val WIFI_ACTIVE = 1000
      const val RADIO_ACTIVE = 10000
    }

    override fun getCpuUsage(usages: Array<PowerProfile.CpuCoreUsage>) = CPU_USAGE

    override fun getNetworkUsage(stats: PowerProfile.NetworkStats): Int {
      return when (stats.myNetworkType) {
        PowerProfile.NetworkType.WIFI ->
          if (stats.myReceivingBps > 0 || stats.mySendingBps > 0) WIFI_ACTIVE else 0
        PowerProfile.NetworkType.RADIO ->
          if (stats.myReceivingBps > 0 || stats.mySendingBps > 0) RADIO_ACTIVE else 0
        else -> 0
      }
    }

    override fun getLocationUsage(locationStats: PowerProfile.LocationStats): Int {
      return when (locationStats.myLocationType) {
        PowerProfile.LocationType.GPS -> GPS_USAGE
        PowerProfile.LocationType.GPS_ACQUIRE -> GPS_ACQUIRE_USAGE
        PowerProfile.LocationType.NETWORK -> NETWORK_USAGE
        else -> 0
      }
    }
  }

  private val energyUsagePreprocessor = EnergyUsagePreprocessor(
    FakeLogService(),
    BatteryModel(FakePowerProfile(), SAMPLE_INTERVAL_NS),
    SAMPLE_INTERVAL_NS)

  @Test
  fun shouldOnlyPreprocessEnergyImpactingEvents() {
    val echoEvent = Common.Event.newBuilder().setKind(Common.Event.Kind.ECHO).build()

    assertThat(energyUsagePreprocessor.shouldPreprocess(CPU_USAGE_EVENTS[0])).isTrue()
    assertThat(energyUsagePreprocessor.shouldPreprocess(NETWORK_SPEED_EVENTS[0])).isTrue()
    assertThat(energyUsagePreprocessor.shouldPreprocess(LOCATION_EVENTS[0])).isTrue()
    assertThat(energyUsagePreprocessor.shouldPreprocess(echoEvent)).isFalse()
  }

  @Test
  fun preprocessCpuEvents() {
    energyUsagePreprocessor.preprocessEvent(CPU_CORE_CONFIG_EVENT)
    val generatedEvents = mutableListOf<Common.Event>()
    for (event in CPU_USAGE_EVENTS) generatedEvents.addAll(energyUsagePreprocessor.preprocessEvent(event))

    val expectedEnergyUsageEvents = arrayOf(
      Common.Event.newBuilder()
        .setPid(PID)
        .setKind(Common.Event.Kind.ENERGY_USAGE)
        .setEnergyUsage(Energy.EnergyUsageData.getDefaultInstance())
        .setTimestamp(0)
        .build(),
      Common.Event.newBuilder()
        .setPid(PID)
        .setKind(Common.Event.Kind.ENERGY_USAGE)
        .setEnergyUsage(Energy.EnergyUsageData.newBuilder().setCpuUsage(FakePowerProfile.CPU_USAGE))
        .setTimestamp(ONE_MS * 200)
        .build())
    assertThat(generatedEvents).containsExactlyElementsIn(expectedEnergyUsageEvents)
  }

  @Test
  fun preprocessNetworkEvents() {
    energyUsagePreprocessor.preprocessEvent(NETWORK_TYPE_EVENT)
    val generatedEvents = mutableListOf<Common.Event>()
    for (event in NETWORK_SPEED_EVENTS) generatedEvents.addAll(energyUsagePreprocessor.preprocessEvent(event))

    val expectedEnergyUsageEvents = arrayOf(
      Common.Event.newBuilder()
        .setPid(PID)
        .setKind(Common.Event.Kind.ENERGY_USAGE)
        .setEnergyUsage(Energy.EnergyUsageData.newBuilder().setNetworkUsage(FakePowerProfile.WIFI_ACTIVE))
        .setTimestamp(0)
        .build(),
      Common.Event.newBuilder()
        .setPid(PID)
        .setKind(Common.Event.Kind.ENERGY_USAGE)
        .setEnergyUsage(Energy.EnergyUsageData.newBuilder().setNetworkUsage(FakePowerProfile.WIFI_ACTIVE))
        .setTimestamp(ONE_MS * 200)
        .build())
    assertThat(generatedEvents).containsExactlyElementsIn(expectedEnergyUsageEvents)
  }

  @Test
  fun preprocessLocationEvents() {
    val generatedEvents = mutableListOf<Common.Event>()
    for (event in LOCATION_EVENTS) generatedEvents.addAll(energyUsagePreprocessor.preprocessEvent(event))

    val expectedEnergyUsageEvents = arrayOf(
      Common.Event.newBuilder()
        .setPid(PID)
        .setKind(Common.Event.Kind.ENERGY_USAGE)
        .setEnergyUsage(Energy.EnergyUsageData.newBuilder().setLocationUsage(FakePowerProfile.GPS_USAGE))
        .setTimestamp(0)
        .build(),
      Common.Event.newBuilder()
        .setPid(PID)
        .setKind(Common.Event.Kind.ENERGY_USAGE)
        .setEnergyUsage(Energy.EnergyUsageData.newBuilder().setLocationUsage(FakePowerProfile.GPS_USAGE))
        .setTimestamp(ONE_MS * 200)
        .build())
    assertThat(generatedEvents).containsExactlyElementsIn(expectedEnergyUsageEvents)
  }
}