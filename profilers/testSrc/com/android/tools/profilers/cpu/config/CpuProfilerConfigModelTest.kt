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
package com.android.tools.profilers.cpu.config

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common.Device
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.CpuProfilerAspect
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CpuProfilerConfigModelTest {
  private val myTimer = FakeTimer()
  private val myServices = FakeIdeProfilerServices()
  private var myProfilers: StudioProfilers? = null
  private var myProfilerStage: CpuProfilerStage? = null
  private var model: CpuProfilerConfigModel? = null

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("CpuProfilerConfigModelTest", FakeCpuService(), FakeTransportService(myTimer),
                                      FakeProfilerService(myTimer), FakeMemoryService(), FakeEventService())

  @Before
  fun setup() {
    myProfilers = StudioProfilers(ProfilerClient(myGrpcChannel.channel), myServices, myTimer)
    myProfilerStage = CpuProfilerStage(myProfilers!!)
    model = CpuProfilerConfigModel(myProfilers!!, myProfilerStage!!)
  }

  @Test
  fun defaultProfilingConfigsReturnsOnlyDeviceSupported() {
    setDevice(AndroidVersion.VersionCodes.LOLLIPOP)
    model!!.updateProfilingConfigurations()

    var realConfigs = model!!.defaultProfilingConfigurations
    assertThat(realConfigs).hasSize(2)
    // First actual configuration should be ART Sampled
    assertThat(realConfigs[0].traceType).isEqualTo(TraceType.ART)
    assertThat(realConfigs[0]).isInstanceOf(ArtSampledConfiguration::class.java)
    assertThat(realConfigs[0].name).isEqualTo(FakeIdeProfilerServices.FAKE_ART_SAMPLED_NAME)
    assertThat(isDefault(realConfigs[0])).isTrue()
    // Second actual configuration should be ART Instrumented
    assertThat(realConfigs[1].traceType).isEqualTo(TraceType.ART)
    assertThat(realConfigs[1]).isInstanceOf(ArtInstrumentedConfiguration::class.java)
    assertThat(realConfigs[1].name).isEqualTo(FakeIdeProfilerServices.FAKE_ART_INSTRUMENTED_NAME)
    assertThat(isDefault(realConfigs[1])).isTrue()

    setDevice(AndroidVersion.VersionCodes.O)
    model!!.updateProfilingConfigurations()

    realConfigs = model!!.defaultProfilingConfigurations

    assertThat(realConfigs).hasSize(4)
    // First actual configuration should be ART Sampled
    assertThat(realConfigs[0].traceType).isEqualTo(TraceType.ART)
    assertThat(realConfigs[0]).isInstanceOf(ArtSampledConfiguration::class.java)
    assertThat(realConfigs[0].name).isEqualTo(FakeIdeProfilerServices.FAKE_ART_SAMPLED_NAME)
    assertThat(isDefault(realConfigs[0])).isTrue()
    // Second actual configuration should be ART Instrumented
    assertThat(realConfigs[1].traceType).isEqualTo(TraceType.ART)
    assertThat(realConfigs[1]).isInstanceOf(ArtInstrumentedConfiguration::class.java)
    assertThat(realConfigs[1].name).isEqualTo(FakeIdeProfilerServices.FAKE_ART_INSTRUMENTED_NAME)
    assertThat(isDefault(realConfigs[1])).isTrue()
    // Second actual configuration should be ART Instrumented
    assertThat(realConfigs[2].traceType).isEqualTo(TraceType.SIMPLEPERF)
    assertThat(realConfigs[2].name).isEqualTo(FakeIdeProfilerServices.FAKE_SIMPLEPERF_NAME)
    assertThat(isDefault(realConfigs[2])).isTrue()
    // Second actual configuration should be ART Instrumented
    assertThat(realConfigs[3].traceType).isEqualTo(TraceType.ATRACE)
    assertThat(realConfigs[3].name).isEqualTo(FakeIdeProfilerServices.FAKE_ATRACE_NAME)
    assertThat(isDefault(realConfigs[3])).isTrue()
  }

  @Test
  fun customProfilingConfigsDeviceFiltering() {
    setDevice(AndroidVersion.VersionCodes.LOLLIPOP)

    myServices.addCustomProfilingConfiguration("Art", TraceType.ART)
    myServices.addCustomProfilingConfiguration("System Trace", TraceType.ATRACE)
    myServices.addCustomProfilingConfiguration("Simpleperf", TraceType.SIMPLEPERF)
    model!!.updateProfilingConfigurations()

    val customConfigs = model!!.customProfilingConfigurations
    assertThat(customConfigs).hasSize(3)
    assertThat(customConfigs[0].traceType).isEqualTo(TraceType.ART)
    assertThat(customConfigs[0].name).isEqualTo("Art")
    assertThat(customConfigs[0].requiredDeviceLevel).isEqualTo(0)
    assertThat(customConfigs[1].traceType).isEqualTo(TraceType.ATRACE)
    assertThat(customConfigs[1].name).isEqualTo("System Trace")
    assertThat(customConfigs[1].requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.N)
    assertThat(customConfigs[2].traceType).isEqualTo(TraceType.SIMPLEPERF)
    assertThat(customConfigs[2].name).isEqualTo("Simpleperf")
    assertThat(customConfigs[2].requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.O)

    // ART and simpleperf are only supported from Android 8.0 (O)
    val customConfigsDeviceFilter = model!!.customProfilingConfigurationsDeviceFiltered
    assertThat(customConfigsDeviceFilter).hasSize(1)
    assertThat(customConfigsDeviceFilter[0].traceType).isEqualTo(TraceType.ART)
    assertThat(customConfigsDeviceFilter[0].name).isEqualTo("Art")
    assertThat(customConfigsDeviceFilter[0].requiredDeviceLevel).isEqualTo(0)
  }

  @Test
  fun atraceFlagFilterConfigsFromCustom() {
    myServices.addCustomProfilingConfiguration("Art", TraceType.ART)
    myServices.addCustomProfilingConfiguration("System Trace", TraceType.ATRACE)
    myServices.addCustomProfilingConfiguration("Simpleperf", TraceType.SIMPLEPERF)
    model!!.updateProfilingConfigurations()

    var customConfigs = model!!.customProfilingConfigurations
    assertThat(customConfigs).hasSize(3)
    assertThat(customConfigs[0].traceType).isEqualTo(TraceType.ART)
    assertThat(customConfigs[0].name).isEqualTo("Art")
    assertThat(customConfigs[1].traceType).isEqualTo(TraceType.ATRACE)
    assertThat(customConfigs[1].name).isEqualTo("System Trace")
    assertThat(customConfigs[2].traceType).isEqualTo(TraceType.SIMPLEPERF)
    assertThat(customConfigs[2].name).isEqualTo("Simpleperf")

    var customConfigsDeviceFilter = model!!.customProfilingConfigurationsDeviceFiltered
    assertThat(customConfigsDeviceFilter).hasSize(3)
    assertThat(customConfigsDeviceFilter[0].traceType).isEqualTo(TraceType.ART)
    assertThat(customConfigsDeviceFilter[0].name).isEqualTo("Art")
    assertThat(customConfigsDeviceFilter[1].traceType).isEqualTo(TraceType.ATRACE)
    assertThat(customConfigsDeviceFilter[1].name).isEqualTo("System Trace")
    assertThat(customConfigsDeviceFilter[2].traceType).isEqualTo(TraceType.SIMPLEPERF)
    assertThat(customConfigsDeviceFilter[2].name).isEqualTo("Simpleperf")
  }

  @Test
  fun profilingConfigIsNotCustomByDefault() {
    myServices.addCustomProfilingConfiguration("FakeConfig", TraceType.ART)
    model!!.updateProfilingConfigurations()
    assertThat(isDefault(model!!.profilingConfiguration)).isTrue()

    val customConfigs = model!!.customProfilingConfigurations
    assertThat(customConfigs).hasSize(1)
    assertThat(customConfigs[0].name).isEqualTo("FakeConfig")
    assertThat(customConfigs[0].traceType).isEqualTo(TraceType.ART)
    assertThat(customConfigs[0].requiredDeviceLevel).isEqualTo(0)
    assertThat(isDefault(customConfigs[0])).isFalse()
  }

  @Test
  fun aspectFiredWhenSettingProfilingConfig() {
    val observer = AspectObserver()
    var aspectCalled = false
    myProfilerStage!!.aspect.addDependency(observer).onChange(
      CpuProfilerAspect.PROFILING_CONFIGURATION, { aspectCalled = true })
    model!!.profilingConfiguration = ArtSampledConfiguration("cfg")
    assertThat(aspectCalled).isTrue()
  }

  private fun isDefault(configuration: ProfilingConfiguration) = myServices
    .getDefaultCpuProfilerConfigs(0)
    .any { configuration.name == it.name }

  private fun setDevice(featureLevel: Int) {
    val device = Device.newBuilder().setFeatureLevel(featureLevel).setSerial("TestSerial").setState(Device.State.ONLINE).build()
    myProfilers?.setProcess(device, null)
  }
}
