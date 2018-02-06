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
import com.android.tools.profilers.*
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.android.tools.profilers.stacktrace.ContextMenuItem
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CpuProfilerStageViewTest {

  private val myProfilerService = FakeProfilerService()

  private val myComponents = FakeIdeProfilerComponents()

  private val myIdeServices = FakeIdeProfilerServices()

  private val myCpuService = FakeCpuService()

  @get:Rule
  val myGrpcChannel = FakeGrpcChannel("CpuCaptureViewTestChannel", myCpuService, myProfilerService,
      FakeMemoryService(), FakeEventService(), FakeNetworkService.newBuilder().build())

  private lateinit var myStage: CpuProfilerStage

  private lateinit var myProfilersView: StudioProfilersView

  @Before
  fun setUp() {
    val timer = FakeTimer()
    val profilers = StudioProfilers(myGrpcChannel.client, myIdeServices, timer)
    // One second must be enough for new devices (and processes) to be picked up
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)

    myStage = CpuProfilerStage(profilers)
    myStage.studioProfilers.stage = myStage
    myProfilersView = StudioProfilersView(profilers, myComponents)
  }

  @Test
  fun contextMenuShouldBeInstalled() {
    // Enable the export trace flag
    myIdeServices.enableExportTrace(true)
    // Clear any context menu items added to the service to make sure we'll have only the items created in CpuProfilerStageView
    myComponents.clearContextMenuItems()
    // Create a CpuProfilerStageView. We don't need its value, so we don't store it in a variable.
    CpuProfilerStageView(myProfilersView, myStage)

    var items = myComponents.allContextMenuItems
    assertThat(items).hasSize(7)

    // Check we add CPU specific actions first.
    assertThat(items[0].text).isEqualTo("Export trace...")
    assertThat(items[1]).isEqualTo(ContextMenuItem.SEPARATOR)

    // Check the common menu items are added only after the "export trace" action
    checkCommonProfilersMenuItems(items, 2)

    // Disable the export trace flag
    myIdeServices.enableExportTrace(false)
    myComponents.clearContextMenuItems()
    CpuProfilerStageView(myProfilersView, myStage)

    items = myComponents.allContextMenuItems
    assertThat(items).hasSize(5)

    // Check the common menu items are added first
    checkCommonProfilersMenuItems(items, 0)
  }

  /**
   * Checks that the menu items common to all profilers are installed in the CPU profiler context menu.
   * They should be at the bottom of the context menu, starting at a given index.
   */
  private fun checkCommonProfilersMenuItems(items: List<ContextMenuItem>, startIndex: Int) {
    var index = startIndex
    assertThat(items[index++].text).isEqualTo("Attach to Live")
    assertThat(items[index++].text).isEqualTo("Detach from Live")
    assertThat(items[index++]).isEqualTo(ContextMenuItem.SEPARATOR)
    assertThat(items[index++].text).isEqualTo("Zoom in")
    assertThat(items[index].text).isEqualTo("Zoom out")
  }
}