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
import com.android.tools.profiler.proto.Profiler
import com.android.tools.profilers.FakeGrpcChannel
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.android.tools.profiler.proto.CpuProfiler
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CpuProfilerConfigModelTest {

  val myServices = FakeIdeProfilerServices()
  var myProfilers: StudioProfilers? = null
  var myProfilerStage: CpuProfilerStage? = null
  var model: CpuProfilerConfigModel? = null

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("CpuProfilerConfigModelTest", FakeCpuService(), FakeProfilerService(),
      FakeMemoryService(), FakeEventService(), FakeNetworkService.newBuilder().build())

  @Before
  fun setup() {
    myProfilers = StudioProfilers(myGrpcChannel.getClient(), myServices)
    myProfilerStage = CpuProfilerStage(myProfilers!!)
    model = CpuProfilerConfigModel(myProfilers!!, myProfilerStage)
  }

  @Test
  fun defaultProfilingConfigsReturnsOnlyDeviceSupported() {
    myServices.enableAtrace(true)
    myServices.enableSimplePerf(true)
    setDevice(AndroidVersion.VersionCodes.LOLLIPOP)
    model!!.updateProfilingConfigurations()

    var realConfigs = model!!.defaultProfilingConfigurations
    assertThat(realConfigs).hasSize(2)
    // First actual configuration should be ART Sampled
    assertThat(realConfigs.get(0).getProfilerType()).isEqualTo(com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType.ART)
    assertThat(realConfigs.get(0).getMode()).isEqualTo(com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED)
    assertThat(realConfigs.get(0).getName()).isEqualTo(ProfilingConfiguration.ART_SAMPLED)
    assertThat(realConfigs.get(0).isDefault()).isTrue()
    // Second actual configuration should be ART Instrumented
    assertThat(realConfigs.get(1).getProfilerType()).isEqualTo(com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType.ART)
    assertThat(realConfigs.get(1).getMode()).isEqualTo(com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartRequest.Mode.INSTRUMENTED)
    assertThat(realConfigs.get(1).getName()).isEqualTo(ProfilingConfiguration.ART_INSTRUMENTED)
    assertThat(realConfigs.get(1).isDefault()).isTrue()

    setDevice(AndroidVersion.VersionCodes.O)
    model!!.updateProfilingConfigurations()

    realConfigs = model!!.defaultProfilingConfigurations

    assertThat(realConfigs).hasSize(4)
    // First actual configuration should be ART Sampled
    assertThat(realConfigs.get(0).getProfilerType()).isEqualTo(com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType.ART)
    assertThat(realConfigs.get(0).getMode()).isEqualTo(com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED)
    assertThat(realConfigs.get(0).getName()).isEqualTo(ProfilingConfiguration.ART_SAMPLED)
    assertThat(realConfigs.get(0).isDefault()).isTrue()
    // Second actual configuration should be ART Instrumented
    assertThat(realConfigs.get(1).getProfilerType()).isEqualTo(com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType.ART)
    assertThat(realConfigs.get(1).getMode()).isEqualTo(com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartRequest.Mode.INSTRUMENTED)
    assertThat(realConfigs.get(1).getName()).isEqualTo(ProfilingConfiguration.ART_INSTRUMENTED)
    assertThat(realConfigs.get(1).isDefault()).isTrue()
    // Second actual configuration should be ART Instrumented
    assertThat(realConfigs.get(2).getProfilerType()).isEqualTo(com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType.SIMPLEPERF)
    assertThat(realConfigs.get(2).getName()).isEqualTo(ProfilingConfiguration.SIMPLEPERF)
    assertThat(realConfigs.get(2).isDefault()).isTrue()
    // Second actual configuration should be ART Instrumented
    assertThat(realConfigs.get(3).getProfilerType()).isEqualTo(com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType.ATRACE)
    assertThat(realConfigs.get(3).getName()).isEqualTo(ProfilingConfiguration.ATRACE)
    assertThat(realConfigs.get(3).isDefault()).isTrue()
  }

  @Test
  fun flagsFilterConfigsFromDefault() {
    myServices.enableAtrace(false)
    myServices.enableSimplePerf(false)

    setDevice(AndroidVersion.VersionCodes.O)
    model!!.updateProfilingConfigurations()

    var realConfigs = model!!.defaultProfilingConfigurations
    assertThat(realConfigs).hasSize(2)
    // First actual configuration should be ART Sampled
    assertThat(realConfigs.get(0).getProfilerType()).isEqualTo(com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType.ART)
    assertThat(realConfigs.get(0).getMode()).isEqualTo(com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED)
    assertThat(realConfigs.get(0).getName()).isEqualTo(ProfilingConfiguration.ART_SAMPLED)
    assertThat(realConfigs.get(0).isDefault()).isTrue()
    assertThat(realConfigs.get(0).getRequiredDeviceLevel()).isEqualTo(0);
    // Second actual configuration should be ART Instrumented
    assertThat(realConfigs.get(1).getProfilerType()).isEqualTo(com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType.ART)
    assertThat(realConfigs.get(1).getMode()).isEqualTo(com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartRequest.Mode.INSTRUMENTED)
    assertThat(realConfigs.get(1).getName()).isEqualTo(ProfilingConfiguration.ART_INSTRUMENTED)
    assertThat(realConfigs.get(1).isDefault()).isTrue()
    assertThat(realConfigs.get(1).getRequiredDeviceLevel()).isEqualTo(0);
  }

  @Test
  fun getAllConfigsReturnsNonDeviceSupportedConfigs() {
    val model = CpuProfilerConfigModel(myProfilers!!, myProfilerStage)
    myServices.enableSimplePerf(true);
    myServices.enableAtrace(true);
    // Set a device that doesn't support simpleperf, or atrace
    setDevice(AndroidVersion.VersionCodes.LOLLIPOP)

    var realConfigs = model.getAllProfilingConfigurations();
    assertThat(realConfigs).hasSize(4);
    // First actual configuration should be ART Sampled
    assertThat(realConfigs.get(0).getProfilerType()).isEqualTo(com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType.ART);
    assertThat(realConfigs.get(0).getMode()).isEqualTo(com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED);
    assertThat(realConfigs.get(0).getName()).isEqualTo(ProfilingConfiguration.ART_SAMPLED);
    assertThat(realConfigs.get(0).isDefault()).isTrue()
    assertThat(realConfigs.get(0).getRequiredDeviceLevel()).isEqualTo(0);
    // Second actual configuration should be ART Instrumented
    assertThat(realConfigs.get(1).getProfilerType()).isEqualTo(com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType.ART);
    assertThat(realConfigs.get(1).getMode()).isEqualTo(com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartRequest.Mode.INSTRUMENTED);
    assertThat(realConfigs.get(1).getName()).isEqualTo(ProfilingConfiguration.ART_INSTRUMENTED);
    assertThat(realConfigs.get(1).isDefault()).isTrue()
    assertThat(realConfigs.get(1).getRequiredDeviceLevel()).isEqualTo(0);

    // Third configuration should be simpleperf
    assertThat(realConfigs.get(2).getProfilerType()).isEqualTo(com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType.SIMPLEPERF);
    assertThat(realConfigs.get(2).getMode()).isEqualTo(com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED);
    assertThat(realConfigs.get(2).getName()).isEqualTo(ProfilingConfiguration.SIMPLEPERF);
    assertThat(realConfigs.get(2).isDefault()).isTrue()
    assertThat(realConfigs.get(2).getRequiredDeviceLevel()).isEqualTo(AndroidVersion.VersionCodes.O);
    // Fourth should be atrace
    assertThat(realConfigs.get(3).getProfilerType()).isEqualTo(CpuProfiler.CpuProfilerType.ATRACE);
    assertThat(realConfigs.get(3).isDefault()).isTrue()
    assertThat(realConfigs.get(3).getRequiredDeviceLevel()).isEqualTo(AndroidVersion.VersionCodes.O);
  }

  @Test
  fun profilingConfigIsNotCustomByDefault() {
    myServices.addCustomProfilingConfiguration("FakeConfig")
    model!!.updateProfilingConfigurations()
    assertThat(model!!.profilingConfiguration.isDefault).isTrue()

    val customConfigs = model!!.customProfilingConfigurations
    assertThat(customConfigs).hasSize(1)
    assertThat(customConfigs.get(0).name).isEqualTo("FakeConfig")
    assertThat(customConfigs.get(0).isDefault).isFalse()
  }

  fun setDevice(featureLevel: Int) {
    val device = Profiler.Device.newBuilder().setFeatureLevel(featureLevel).setSerial("TestSerial").setState(Profiler.Device.State.ONLINE).build()
    myProfilers?.setDevice(device)
  }
}
