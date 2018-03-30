// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.profilers.energy

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.AxisComponentModel
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.LineChartModel
import com.android.tools.adtui.model.formatter.EnergyAxisFormatter
import com.android.tools.adtui.model.legend.LegendComponentModel
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.EnergyProfiler
import com.android.tools.profilers.*
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EnergyMonitorTest {
  private val service = FakeEnergyService(
      listOf(
          EnergyProfiler.EnergySample.newBuilder().
              setTimestamp(2000).
              setNetworkUsage(20).
              setCpuUsage(30).build()
      ))
  private val profilerService = FakeProfilerService(false)
  private val deviceNougat = Common.Device.newBuilder()
    .setDeviceId("FakeDeviceNougat".hashCode().toLong())
    .setFeatureLevel(AndroidVersion.VersionCodes.N_MR1)
    .setSerial("FakeDeviceNougat")
    .setState(Common.Device.State.ONLINE)
    .build()
  private val deviceOreo = Common.Device.newBuilder()
    .setDeviceId("FakeDeviceOreo".hashCode().toLong())
    .setFeatureLevel(AndroidVersion.VersionCodes.O)
    .setSerial("FakeDeviceOreo")
    .setState(Common.Device.State.ONLINE)
    .build()

  @get:Rule
  val grpcChannel = FakeGrpcChannel("EnergyMonitorTest", profilerService, service)

  private lateinit var monitor: EnergyMonitor
  private lateinit var timer: FakeTimer
  private lateinit var profilers: StudioProfilers

  @Before
  fun setUp() {
    timer = FakeTimer()
    val services = FakeIdeProfilerServices().apply { enableEnergyProfiler(true) }
    profilers = StudioProfilers(grpcChannel.client, services, timer)
    monitor = EnergyMonitor(profilers)
  }

  @Test
  fun testMonitorEnabled() {
    profilerService.addDevice(deviceNougat)
    profilerService.addDevice(deviceOreo)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)

    profilers.device = deviceNougat
    assertThat(profilers.device!!.serial).isEqualTo("FakeDeviceNougat")
    assertThat(monitor.isEnabled).isFalse()
    profilers.device = deviceOreo
    assertThat(profilers.device!!.serial).isEqualTo("FakeDeviceOreo")
    assertThat(monitor.isEnabled).isTrue()

    val sessionPreO = Common.Session.newBuilder()
      .setSessionId(1).setStartTimestamp(FakeTimer.ONE_SECOND_IN_NS).setEndTimestamp(FakeTimer.ONE_SECOND_IN_NS * 2).build()
    val sessionPreOMetadata = Common.SessionMetaData.newBuilder()
      .setSessionId(1).setType(Common.SessionMetaData.SessionType.FULL).setJvmtiEnabled(false).setStartTimestampEpochMs(1).build()
    profilerService.addSession(sessionPreO, sessionPreOMetadata)
    val sessionO = Common.Session.newBuilder()
      .setSessionId(2).setStartTimestamp(FakeTimer.ONE_SECOND_IN_NS).setEndTimestamp(FakeTimer.ONE_SECOND_IN_NS * 2).build()
    val sessionOMetadata = Common.SessionMetaData.newBuilder()
      .setSessionId(2).setType(Common.SessionMetaData.SessionType.FULL).setJvmtiEnabled(true).setStartTimestampEpochMs(1).build()
    profilerService.addSession(sessionO, sessionOMetadata)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)

    profilers.sessionsManager.setSession(sessionPreO)
    assertThat(monitor.isEnabled).isFalse()
    profilers.sessionsManager.setSession(sessionO)
    assertThat(monitor.isEnabled).isTrue()
  }

  @Test
  fun testExpand() {
    assertThat(monitor.profilers.stage).isInstanceOf(NullMonitorStage::class.java)
    // One second is enough for new devices (and processes) to be picked up
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    monitor.expand()
    assertThat(monitor.profilers.stage).isInstanceOf(EnergyProfilerStage::class.java)
  }

  @Test
  fun testEnergyUsage() {
    val dataSeries = monitor.usage.usageDataSeries
    assertThat(dataSeries.series.size).isEqualTo(1)
    assertThat(dataSeries.series[0].x).isEqualTo(2)
    assertThat(dataSeries.series[0].value).isEqualTo(50)
  }

  @Test
  fun testName() {
    assertThat(monitor.name).isEqualTo("ENERGY")
  }

  @Test
  fun testLegends() {
    val legends = monitor.legends
    assertThat(legends.usageLegend.value).isEqualTo(EnergyAxisFormatter.LABELS[0])
  }

  @Test
  fun testRegisterCorrectly() {
    val observer = AspectObserver()
    var usageUpdated = false
    monitor.usage.addDependency(observer).onChange(
        LineChartModel.Aspect.LINE_CHART, { usageUpdated = true })
    var legendUpdated = false
    monitor.legends.addDependency(observer).onChange(
        LegendComponentModel.Aspect.LEGEND, { legendUpdated = true })
    var tooltipLegendUpated = false
    monitor.tooltipLegends.addDependency(observer).onChange(
        LegendComponentModel.Aspect.LEGEND, { tooltipLegendUpated = true})
    var axisUpdated = false
    monitor.axis.addDependency(observer).onChange(
        AxisComponentModel.Aspect.AXIS, { axisUpdated = true })

    monitor.enter()
    timer.tick(1)
    assertThat(usageUpdated).isTrue()
    assertThat(legendUpdated).isTrue()
    assertThat(tooltipLegendUpated).isTrue()
    assertThat(axisUpdated).isTrue()
  }

  @Test
  fun testUnregisterCorrectly() {
    val observer = AspectObserver()
    var usageUpdated = false
    monitor.usage.addDependency(observer).onChange(
        LineChartModel.Aspect.LINE_CHART, { usageUpdated = true })
    var legendUpdated = false
    monitor.legends.addDependency(observer).onChange(
        LegendComponentModel.Aspect.LEGEND, { legendUpdated = true })
    var tooltipLegendUpated = false
    monitor.tooltipLegends.addDependency(observer).onChange(
        LegendComponentModel.Aspect.LEGEND, { tooltipLegendUpated = true})
    var axisUpdated = false
    monitor.axis.addDependency(observer).onChange(
        AxisComponentModel.Aspect.AXIS, { axisUpdated = true })

    monitor.exit()
    timer.tick(1)
    assertThat(usageUpdated).isFalse()
    assertThat(legendUpdated).isFalse()
    assertThat(tooltipLegendUpated).isFalse()
    assertThat(axisUpdated).isFalse()
  }
}
