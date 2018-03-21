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
import com.android.tools.profilers.FakeGrpcChannel
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.StudioProfilers
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class EnergyProfilerStageTest {

  private val profilerService = FakeProfilerService(true)

  @get:Rule
  var grpcChannel = FakeGrpcChannel("EnergyProfilerStageTest", profilerService, FakeEnergyService())

  private lateinit var timer: FakeTimer
  private lateinit var myStage: EnergyProfilerStage

  @Before
  fun setUp() {
    timer = FakeTimer()
    val services = FakeIdeProfilerServices().apply { enableEnergyProfiler(true) }
    myStage = EnergyProfilerStage(StudioProfilers(grpcChannel.client, services, timer))
    myStage.studioProfilers.timeline.viewRange.set(TimeUnit.SECONDS.toMicros(0).toDouble(), TimeUnit.SECONDS.toMicros(5).toDouble())
    myStage.studioProfilers.stage = myStage
  }

  @Test
  fun getLegends() {
    val energyLegends = myStage.legends
    assertThat(energyLegends.cpuLegend.name).isEqualTo("CPU")
    assertThat(energyLegends.networkLegend.name).isEqualTo("NETWORK")

    assertThat(energyLegends.legends).hasSize(2)
  }

  @Test
  fun hasUserUsedSelection() {
    assertThat(myStage.instructionsEaseOutModel.percentageComplete).isWithin(0f).of(0f)
    assertThat(myStage.hasUserUsedEnergySelection()).isFalse()
    myStage.selectionModel.setSelectionEnabled(true)
    myStage.selectionModel.set(0.0, 100.0)
    assertThat(myStage.instructionsEaseOutModel.percentageComplete).isWithin(0f).of(1f);
    assertThat(myStage.hasUserUsedEnergySelection()).isTrue()
  }

  @Test
  fun setUsageTooltip() {
    myStage.enter()
    myStage.tooltip = EnergyUsageTooltip(myStage)
    assertThat(myStage.tooltip).isInstanceOf(EnergyUsageTooltip::class.java)
    val tooltip = myStage.tooltip as EnergyUsageTooltip
    assertThat(tooltip.legends.legends).hasSize(2)
    assertThat(tooltip.legends.cpuLegend.name).isEqualTo("CPU")
    assertThat(tooltip.legends.networkLegend.name).isEqualTo("NETWORK")
  }
}
