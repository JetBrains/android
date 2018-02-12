/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.cpu

import com.android.sdklib.AndroidVersion
import com.android.tools.profiler.proto.Common.Device
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartRequest
import com.android.tools.profilers.FakeGrpcChannel
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CpuProfilerConfigModelTest {

  private val myServices = FakeIdeProfilerServices()
  private var myProfilers: StudioProfilers? = null
  private var myProfilerStage: CpuProfilerStage? = null
  private var model: CpuProfilerConfigModel? = null

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("CpuProfilerConfigModelTest", FakeCpuService(), FakeProfilerService(),
      FakeMemoryService(), FakeEventService(), FakeNetworkService.newBuilder().build())

  @Before
  fun setup() {
    myProfilers = StudioProfilers(myGrpcChannel.client, myServices)
    myProfilerStage = CpuProfilerStage(myProfilers!!)
    model = CpuProfilerConfigModel(myProfilers!!, myProfilerStage)
  }

  @Test
  fun defaultProfilingConfigsReturnsOnlyDeviceSupported() {
    myServices.enableAtrace(true)
    myServices.enableSimpleperf(true)
    setDevice(AndroidVersion.VersionCodes.LOLLIPOP)
    model!!.updateProfilingConfigurations()

    var realConfigs = model!!.defaultProfilingConfigurations
    assertThat(realConfigs).hasSize(2)
    // First actual configuration should be ART Sampled
    assertThat(realConfigs[0].profilerType).isEqualTo(CpuProfilerType.ART)
    assertThat(realConfigs[0].mode).isEqualTo(CpuProfilingAppStartRequest.Mode.SAMPLED)
    assertThat(realConfigs[0].name).isEqualTo(ProfilingConfiguration.ART_SAMPLED)
    assertThat(realConfigs[0].isDefault).isTrue()
    // Second actual configuration should be ART Instrumented
    assertThat(realConfigs[1].profilerType).isEqualTo(CpuProfilerType.ART)
    assertThat(realConfigs[1].mode).isEqualTo(CpuProfilingAppStartRequest.Mode.INSTRUMENTED)
    assertThat(realConfigs[1].name).isEqualTo(ProfilingConfiguration.ART_INSTRUMENTED)
    assertThat(realConfigs[1].isDefault).isTrue()

    setDevice(AndroidVersion.VersionCodes.O)
    model!!.updateProfilingConfigurations()

    realConfigs = model!!.defaultProfilingConfigurations

    assertThat(realConfigs).hasSize(4)
    // First actual configuration should be ART Sampled
    assertThat(realConfigs[0].profilerType).isEqualTo(CpuProfilerType.ART)
    assertThat(realConfigs[0].mode).isEqualTo(CpuProfilingAppStartRequest.Mode.SAMPLED)
    assertThat(realConfigs[0].name).isEqualTo(ProfilingConfiguration.ART_SAMPLED)
    assertThat(realConfigs[0].isDefault).isTrue()
    // Second actual configuration should be ART Instrumented
    assertThat(realConfigs[1].profilerType).isEqualTo(CpuProfilerType.ART)
    assertThat(realConfigs[1].mode).isEqualTo(CpuProfilingAppStartRequest.Mode.INSTRUMENTED)
    assertThat(realConfigs[1].name).isEqualTo(ProfilingConfiguration.ART_INSTRUMENTED)
    assertThat(realConfigs[1].isDefault).isTrue()
    // Second actual configuration should be ART Instrumented
    assertThat(realConfigs[2].profilerType).isEqualTo(CpuProfilerType.SIMPLEPERF)
    assertThat(realConfigs[2].name).isEqualTo(ProfilingConfiguration.SIMPLEPERF)
    assertThat(realConfigs[2].isDefault).isTrue()
    // Second actual configuration should be ART Instrumented
    assertThat(realConfigs[3].profilerType).isEqualTo(CpuProfilerType.ATRACE)
    assertThat(realConfigs[3].name).isEqualTo(ProfilingConfiguration.ATRACE)
    assertThat(realConfigs[3].isDefault).isTrue()
  }

  @Test
  fun flagsFilterConfigsFromDefault() {
    myServices.enableAtrace(false)
    myServices.enableSimpleperf(false)

    setDevice(AndroidVersion.VersionCodes.O)
    model!!.updateProfilingConfigurations()

    val realConfigs = model!!.defaultProfilingConfigurations
    assertThat(realConfigs).hasSize(2)
    // First actual configuration should be ART Sampled
    assertThat(realConfigs[0].profilerType).isEqualTo(CpuProfilerType.ART)
    assertThat(realConfigs[0].mode).isEqualTo(CpuProfilingAppStartRequest.Mode.SAMPLED)
    assertThat(realConfigs[0].name).isEqualTo(ProfilingConfiguration.ART_SAMPLED)
    assertThat(realConfigs[0].isDefault).isTrue()
    assertThat(realConfigs[0].requiredDeviceLevel).isEqualTo(0)
    // Second actual configuration should be ART Instrumented
    assertThat(realConfigs[1].profilerType).isEqualTo(CpuProfilerType.ART)
    assertThat(realConfigs[1].mode).isEqualTo(CpuProfilingAppStartRequest.Mode.INSTRUMENTED)
    assertThat(realConfigs[1].name).isEqualTo(ProfilingConfiguration.ART_INSTRUMENTED)
    assertThat(realConfigs[1].isDefault).isTrue()
    assertThat(realConfigs[1].requiredDeviceLevel).isEqualTo(0)
  }

  @Test
  fun customProfilingConfigsDeviceFiltering() {
    myServices.enableAtrace(true)
    myServices.enableSimpleperf(true)
    setDevice(AndroidVersion.VersionCodes.LOLLIPOP)

    myServices.addCustomProfilingConfiguration("Art", CpuProfilerType.ART)
    myServices.addCustomProfilingConfiguration("Atrace", CpuProfilerType.ATRACE)
    myServices.addCustomProfilingConfiguration("Simpleperf", CpuProfilerType.SIMPLEPERF)
    model!!.updateProfilingConfigurations()

    val customConfigs = model!!.customProfilingConfigurations
    assertThat(customConfigs).hasSize(3)
    assertThat(customConfigs[0].profilerType).isEqualTo(CpuProfilerType.ART)
    assertThat(customConfigs[0].name).isEqualTo("Art")
    assertThat(customConfigs[0].requiredDeviceLevel).isEqualTo(0)
    assertThat(customConfigs[1].profilerType).isEqualTo(CpuProfilerType.ATRACE)
    assertThat(customConfigs[1].name).isEqualTo("Atrace")
    assertThat(customConfigs[1].requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.O)
    assertThat(customConfigs[2].profilerType).isEqualTo(CpuProfilerType.SIMPLEPERF)
    assertThat(customConfigs[2].name).isEqualTo("Simpleperf")
    assertThat(customConfigs[2].requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.O)

    // ART and simpleperf are only supported from Android 8.0 (O)
    val customConfigsDeviceFilter = model!!.customProfilingConfigurationsDeviceFiltered
    assertThat(customConfigsDeviceFilter).hasSize(1)
    assertThat(customConfigsDeviceFilter[0].profilerType).isEqualTo(CpuProfilerType.ART)
    assertThat(customConfigsDeviceFilter[0].name).isEqualTo("Art")
    assertThat(customConfigsDeviceFilter[0].requiredDeviceLevel).isEqualTo(0)
  }

  @Test
  fun flagsFilterConfigsFromCustom() {
    // First, we try with the flags enabled
    myServices.enableAtrace(true)
    myServices.enableSimpleperf(true)

    myServices.addCustomProfilingConfiguration("Art", CpuProfilerType.ART)
    myServices.addCustomProfilingConfiguration("Atrace", CpuProfilerType.ATRACE)
    myServices.addCustomProfilingConfiguration("Simpleperf", CpuProfilerType.SIMPLEPERF)
    model!!.updateProfilingConfigurations()

    var customConfigs = model!!.customProfilingConfigurations
    assertThat(customConfigs).hasSize(3)
    assertThat(customConfigs[0].profilerType).isEqualTo(CpuProfilerType.ART)
    assertThat(customConfigs[0].name).isEqualTo("Art")
    assertThat(customConfigs[1].profilerType).isEqualTo(CpuProfilerType.ATRACE)
    assertThat(customConfigs[1].name).isEqualTo("Atrace")
    assertThat(customConfigs[2].profilerType).isEqualTo(CpuProfilerType.SIMPLEPERF)
    assertThat(customConfigs[2].name).isEqualTo("Simpleperf")

    var customConfigsDeviceFilter = model!!.customProfilingConfigurationsDeviceFiltered
    assertThat(customConfigsDeviceFilter).hasSize(3)
    assertThat(customConfigsDeviceFilter[0].profilerType).isEqualTo(CpuProfilerType.ART)
    assertThat(customConfigsDeviceFilter[0].name).isEqualTo("Art")
    assertThat(customConfigsDeviceFilter[1].profilerType).isEqualTo(CpuProfilerType.ATRACE)
    assertThat(customConfigsDeviceFilter[1].name).isEqualTo("Atrace")
    assertThat(customConfigsDeviceFilter[2].profilerType).isEqualTo(CpuProfilerType.SIMPLEPERF)
    assertThat(customConfigsDeviceFilter[2].name).isEqualTo("Simpleperf")

    // Now disable the flags
    myServices.enableAtrace(false)
    myServices.enableSimpleperf(false)
    model!!.updateProfilingConfigurations()

    customConfigs = model!!.customProfilingConfigurations
    assertThat(customConfigs).hasSize(1)
    assertThat(customConfigs[0].profilerType).isEqualTo(CpuProfilerType.ART)
    assertThat(customConfigs[0].name).isEqualTo("Art")

    customConfigsDeviceFilter = model!!.customProfilingConfigurationsDeviceFiltered
    assertThat(customConfigsDeviceFilter).hasSize(1)
    assertThat(customConfigs[0].requiredDeviceLevel).isEqualTo(0)
    assertThat(customConfigsDeviceFilter[0].profilerType).isEqualTo(CpuProfilerType.ART)
    assertThat(customConfigsDeviceFilter[0].name).isEqualTo("Art")
  }

  @Test
  fun profilingConfigIsNotCustomByDefault() {
    myServices.addCustomProfilingConfiguration("FakeConfig", CpuProfilerType.ART)
    model!!.updateProfilingConfigurations()
    assertThat(model!!.profilingConfiguration.isDefault).isTrue()

    val customConfigs = model!!.customProfilingConfigurations
    assertThat(customConfigs).hasSize(1)
    assertThat(customConfigs[0].name).isEqualTo("FakeConfig")
    assertThat(customConfigs[0].profilerType).isEqualTo(CpuProfilerType.ART)
    assertThat(customConfigs[0].requiredDeviceLevel).isEqualTo(0)
    assertThat(customConfigs[0].isDefault).isFalse()
  }

  private fun setDevice(featureLevel: Int) {
    val device = Device.newBuilder().setFeatureLevel(featureLevel).setSerial("TestSerial").setState(Device.State.ONLINE).build()
    myProfilers?.device = device
  }
}
