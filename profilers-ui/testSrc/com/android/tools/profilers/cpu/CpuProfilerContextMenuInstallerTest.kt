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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.stdui.ContextMenuItem
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.event.FakeEventService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JPanel

class CpuProfilerContextMenuInstallerTest {

  private val ideServices = FakeIdeProfilerServices()
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)
  private val ideComponents = FakeIdeProfilerComponents()

  @JvmField
  @Rule
  val myGrpcChannel = FakeGrpcChannel(javaClass.simpleName, transportService, FakeEventService())

  private lateinit var stage: CpuProfilerStage

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(myGrpcChannel.channel), ideServices, timer)
    profilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null)

    // One second must be enough for new devices (and processes) to be picked up
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)

    stage = CpuProfilerStage(profilers)
    stage.studioProfilers.stage = stage
    stage.enter()
  }

  @Test
  fun contextMenuShouldBeInstalled() {
    // Clear any context menu items added to the service to make sure we'll have only the items created in CpuProfilerStageView
    ideComponents.clearContextMenuItems()

    CpuProfilerContextMenuInstaller.install(stage, ideComponents, JPanel(), JPanel())

    assertThat(ideComponents.allContextMenuItems.map { it.text }).containsExactly(
      "Record CPU trace",
      ContextMenuItem.SEPARATOR.text,
      "Export trace...",
      ContextMenuItem.SEPARATOR.text
    ).inOrder()
  }

  @Test
  fun recordTraceMenuItemOnlyEnabledInLiveSessions() {
    // Clear any context menu items added to the service to make sure we'll have only the items created in CpuProfilerStageView
    ideComponents.clearContextMenuItems()

    CpuProfilerContextMenuInstaller.install(stage, ideComponents, JPanel(), JPanel())

    var recordTraceEntry = ideComponents.allContextMenuItems[0]
    assertThat(recordTraceEntry.text).isEqualTo("Record CPU trace")
    // As the current session is alive, the item should be enabled.
    assertThat(recordTraceEntry.isEnabled).isTrue()

    stage.studioProfilers.sessionsManager.endCurrentSession()
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)

    // Clear the components again and create a new instance of CpuProfilerStageView to re-add the components.
    ideComponents.clearContextMenuItems()
    CpuProfilerContextMenuInstaller.install(stage, ideComponents, JPanel(), JPanel())

    recordTraceEntry = ideComponents.allContextMenuItems[0]
    assertThat(recordTraceEntry.text).isEqualTo("Record CPU trace")
    // As the current session is dead, the item should be disabled.
    assertThat(recordTraceEntry.isEnabled).isFalse()
  }
}
