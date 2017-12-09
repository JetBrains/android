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
import com.android.tools.profiler.proto.Profiler
import com.android.tools.profilers.*
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class EnergyProfilerStageViewTest {
  private val profilerService = FakeProfilerService(true)
  private val energyService = FakeEnergyService()

  @get:Rule
  var grpcChannel = FakeGrpcChannel(EnergyProfilerStageViewTest::class.java.simpleName, profilerService, energyService)

  private lateinit var timer: FakeTimer
  private lateinit var view: StudioProfilersView

  @Before
  fun setUp() {
    timer = FakeTimer()
    val services = FakeIdeProfilerServices().apply { enableEnergyProfiler(true) }
    val profilers = StudioProfilers(grpcChannel.client, services, timer)
    profilerService.setAgentStatus(Profiler.AgentStatusResponse.Status.ATTACHED)
    timer.tick(TimeUnit.SECONDS.toNanos(1))

    // StudioProfilersView initialization needs to happen after the tick, as during setDevice/setProcess the StudioMonitorStage is
    // constructed. If the StudioMonitorStageView is constructed as well, grpc exceptions will be thrown due to lack of various services
    // in the channel, and the tick loop would not complete properly to set the process and agent status.
    profilers.stage = EnergyProfilerStage(profilers)
    // Initialize the view after the stage, otherwise it will create the views for the monitoring stage.
    view = StudioProfilersView(profilers, FakeIdeProfilerComponents())
  }

  @Test
  fun expectedStageViewIsCreated() {
    assertThat(view.stageView).isInstanceOf(EnergyProfilerStageView::class.java)
  }
}
