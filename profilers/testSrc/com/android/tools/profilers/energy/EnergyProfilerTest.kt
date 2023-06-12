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
package com.android.tools.profilers.energy

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EnergyProfilerTest {
  @get:Rule
  var grpcChannel = FakeGrpcChannel("EnergyProfilerTest")

  private lateinit var profiler: EnergyProfiler

  @Before
  fun setUp() {
    val services = FakeIdeProfilerServices().apply { enableEnergyProfiler(true) }
    profiler = EnergyProfiler(StudioProfilers(ProfilerClient(grpcChannel.channel), services, FakeTimer()))
  }

  @Test
  fun newMonitor() {
    val monitor = profiler.newMonitor()
    assertThat(monitor).isNotNull()
    assertThat(monitor).isInstanceOf(EnergyMonitor::class.java)
  }
}