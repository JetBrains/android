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
import com.google.common.truth.Truth.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import javax.swing.JPanel

class CpuProfilerContextMenuInstallerTest {

  private val ideServices = FakeIdeProfilerServices()

  private val ideComponents = FakeIdeProfilerComponents()

  @JvmField
  @Rule
  val myGrpcChannel = FakeGrpcChannel(
    "CpuProfilerContextMenuInstallerTest", FakeCpuService(), FakeProfilerService(),
    FakeMemoryService(), FakeEventService(), FakeNetworkService.newBuilder().build()
  )

  private lateinit var stage: CpuProfilerStage

  @Before
  fun setUp() {
    val timer = FakeTimer()
    val profilers = StudioProfilers(myGrpcChannel.client, ideServices, timer)
    profilers.setPreferredProcess(FakeProfilerService.FAKE_DEVICE_NAME, FakeProfilerService.FAKE_PROCESS_NAME, null)

    // One second must be enough for new devices (and processes) to be picked up
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)

    stage = CpuProfilerStage(profilers)
    stage.studioProfilers.stage = stage
    stage.enter()
  }

  @Test
  fun contextMenuShouldBeInstalled() {
    // Enable the export trace flag
    ideServices.enableExportTrace(true)
    // Clear any context menu items added to the service to make sure we'll have only the items created in CpuProfilerStageView
    ideComponents.clearContextMenuItems()

    CpuProfilerContextMenuInstaller.install(stage, ideComponents, JPanel(), JPanel())

    assertThat(ideComponents.allContextMenuItems.map { it.text }).containsExactly(
      "Record CPU trace",
      ContextMenuItem.SEPARATOR.text,
      "Export trace...",
      ContextMenuItem.SEPARATOR.text,
      "Next capture",
      "Previous capture",
      ContextMenuItem.SEPARATOR.text
    ).inOrder()

    // Disable the export trace flag
    ideServices.enableExportTrace(false)
    ideComponents.clearContextMenuItems()
    CpuProfilerContextMenuInstaller.install(stage, ideComponents, JPanel(), JPanel())

    assertThat(ideComponents.allContextMenuItems.map { it.text }).containsExactly(
      "Record CPU trace",
      ContextMenuItem.SEPARATOR.text,
      "Next capture",
      "Previous capture",
      ContextMenuItem.SEPARATOR.text
    ).inOrder()
  }

  @Test
  fun contextMenuShouldBeDisabledInImportTraceMode() {
    // Enable the export trace flag because we are going to test if the export menu item is enabled/disabled.
    ideServices.enableExportTrace(true)
    // Clear any context menu items added to the service to make sure we'll have only the items created in CpuProfilerStageView
    ideComponents.clearContextMenuItems()
    CpuProfilerContextMenuInstaller.install(stage, ideComponents, JPanel(), JPanel())
    assertThat(stage.isImportTraceMode).isFalse()

    var items = ideComponents.allContextMenuItems
    assertThat(items).hasSize(7)

    // Check we add CPU specific actions first.
    assertThat(items[0].text).isEqualTo("Record CPU trace")
    assertThat(items[0].isEnabled).isTrue()

    assertThat(items[2].text).isEqualTo("Export trace...")
    assertThat(items[2].isEnabled).isTrue()

    stage.traceIdsIterator.addTrace(123)  // add a fake trace
    assertThat(items[4].text).isEqualTo("Next capture")
    assertThat(items[4].isEnabled).isTrue()
    assertThat(items[5].text).isEqualTo("Previous capture")
    assertThat(items[5].isEnabled).isTrue()

    // Enable import trace and sessions view, both of which are required for import-trace-mode.
    ideServices.enableImportTrace(true)
    ideServices.enableSessionsView(true)
    stage = CpuProfilerStage(stage.studioProfilers, File("FakePathToTraceFile.trace"))
    stage.enter()
    // Clear any context menu items added to the service to make sure we'll have only the items created in CpuProfilerStageView
    ideComponents.clearContextMenuItems()
    // Create a CpuProfilerStageView. We don't need its value, so we don't store it in a variable.
    CpuProfilerContextMenuInstaller.install(stage, ideComponents, JPanel(), JPanel())
    assertThat(stage.isImportTraceMode).isTrue()

    items = ideComponents.allContextMenuItems
    assertThat(items).hasSize(7)

    // Check we add CPU specific actions first.
    assertThat(items[0].text).isEqualTo("Record CPU trace")
    assertThat(items[0].isEnabled).isFalse()

    assertThat(items[2].text).isEqualTo("Export trace...")
    assertThat(items[2].isEnabled).isFalse()

    stage.traceIdsIterator.addTrace(123)  // add a fake trace
    assertThat(items[4].text).isEqualTo("Next capture")
    assertThat(items[4].isEnabled).isFalse()
    assertThat(items[5].text).isEqualTo("Previous capture")
    assertThat(items[5].isEnabled).isFalse()
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
    // Clear the components again and create a new instance of CpuProfilerStageView to re-add the components.
    ideComponents.clearContextMenuItems()
    CpuProfilerContextMenuInstaller.install(stage, ideComponents, JPanel(), JPanel())

    recordTraceEntry = ideComponents.allContextMenuItems[0]
    assertThat(recordTraceEntry.text).isEqualTo("Record CPU trace")
    // As the current session is dead, the item should be disabled.
    assertThat(recordTraceEntry.isEnabled).isFalse()
  }
}
