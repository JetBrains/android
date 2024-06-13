/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator

import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceInfo
import com.android.sdklib.deviceprovisioner.testing.DeviceProvisionerRule
import com.android.sdklib.deviceprovisioner.testing.FakeAdbDeviceProvisionerPlugin.FakeDeviceHandle
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.delayUntilCondition
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TestDataProvider
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import kotlin.time.Duration.Companion.seconds

private const val SETTINGS_BUTTON_TEXT = "Common Android Settings"
private const val ITERATION_DELAY_MS = 5L
private val TIMEOUT = 10.seconds

class EmulatorAdbReadyServiceTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val emulatorRule = FakeEmulatorRule()
  private val deviceProvisionerRule = DeviceProvisionerRule()

  @get:Rule
  val ruleChain = RuleChain(projectRule, emulatorRule, deviceProvisionerRule)

  private val project: Project
    get() = projectRule.project

  private val disposable: Disposable
    get() = projectRule.testRootDisposable

  @Before
  fun before() {
    HeadlessDataManager.fallbackToProductionDataManager(disposable) // Necessary to properly update toolbar button states.
    (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider(TestDataProvider(project), disposable)
    val deviceProvisionerService: DeviceProvisionerService = mock()
    project.replaceService(DeviceProvisionerService::class.java, deviceProvisionerService, disposable)
    whenever(deviceProvisionerService.deviceProvisioner).thenReturn(deviceProvisionerRule.deviceProvisioner)
  }

  @Test
  fun testReadyService() = runBlocking {
    val serialNumber1 = "serial1"
    val serialNumber2 = "serial2"
    val deviceHandle1 = deviceProvisionerRule.deviceProvisionerPlugin.addNewDevice(serialNumber1)
    val deviceHandle2 = deviceProvisionerRule.deviceProvisionerPlugin.addNewDevice(serialNumber2)

    assertThat(isReadyForAdbCommands(project, serialNumber1)).isFalse()
    assertThat(isReadyForAdbCommands(project, serialNumber2)).isFalse()

    deviceHandle1.connectToMockDevice(serialNumber1)
    delayUntilCondition(ITERATION_DELAY_MS, TIMEOUT) { isReadyForAdbCommands(project, serialNumber1) }
    assertThat(isReadyForAdbCommands(project, serialNumber2)).isFalse()

    deviceHandle2.connectToMockDevice(serialNumber2)
    delayUntilCondition(ITERATION_DELAY_MS, TIMEOUT) { isReadyForAdbCommands(project, serialNumber2) }
    assertThat(isReadyForAdbCommands(project, serialNumber1)).isTrue()

    deviceHandle1.disconnect()
    delayUntilCondition(ITERATION_DELAY_MS, TIMEOUT) { !isReadyForAdbCommands(project, serialNumber1) }
    assertThat(isReadyForAdbCommands(project, serialNumber2)).isTrue()

    deviceHandle2.disconnect()
    delayUntilCondition(ITERATION_DELAY_MS, TIMEOUT) { !isReadyForAdbCommands(project, serialNumber2) }
    assertThat(isReadyForAdbCommands(project, serialNumber1)).isFalse()
  }

  @Test
  fun testMainToolbarUpdateOnConnect() = runBlocking {
    val emulator = createFakeEmulator()
    val panel = createWindowPanel()
    val ui = runInEdtAndGet { createUi(panel, emulator) }

    val button = ui.getComponent<ActionButton> { it.action.templateText == SETTINGS_BUTTON_TEXT }
    assertThat(button.isEnabled).isFalse()

    val serialNumber = panel.emulator.emulatorId.serialNumber
    val deviceHandle = deviceProvisionerRule.deviceProvisionerPlugin.addNewDevice(serialNumber)
    deviceProvisionerRule.deviceProvisionerPlugin.addNewDevice(serialNumber)
    deviceHandle.connectToMockDevice(serialNumber)
    delayUntilCondition(ITERATION_DELAY_MS, TIMEOUT) { button.isEnabled }
  }

  private fun FakeDeviceHandle.connectToMockDevice(serialNumber: String) {
    mock<ConnectedDevice>().also { mockDevice ->
      whenever(mockDevice.deviceInfoFlow)
        .thenReturn(MutableStateFlow(DeviceInfo(serialNumber, com.android.adblib.DeviceState.ONLINE)))
      stateFlow.update { com.android.sdklib.deviceprovisioner.DeviceState.Connected(it.properties, mockDevice) }
    }
  }

  private fun FakeDeviceHandle.disconnect() {
    stateFlow.update { com.android.sdklib.deviceprovisioner.DeviceState.Disconnected(it.properties) }
  }

  private fun createFakeEmulator(): FakeEmulator {
    val avdFolder = FakeEmulator.createPhoneAvd(emulatorRule.avdRoot, api = 34)
    val emulator = emulatorRule.newEmulator(avdFolder)
    emulator.start()
    return emulator
  }

  private fun createWindowPanel(): EmulatorToolWindowPanel {
    val catalog = RunningEmulatorCatalog.getInstance()
    val emulators = catalog.updateNow().get()
    assertThat(emulators).hasSize(1)
    val emulatorController = emulators.first()
    return EmulatorToolWindowPanel(disposable, project, emulatorController)
  }

  private fun createUi(panel: EmulatorToolWindowPanel, fakeEmulator: FakeEmulator): FakeUi {
    val ui = FakeUi(panel, createFakeWindow = true, parentDisposable = disposable)
    panel.createContent(true)
    panel.size = Dimension(200, 400)
    ui.layoutAndDispatchEvents()
    val content = ContentFactory.getInstance().createContent(panel, "Emulator", false)
    val toolWindow =  ToolWindowManager.getInstance(project).getToolWindow(RUNNING_DEVICES_TOOL_WINDOW_ID)!!
    toolWindow.contentManager.addContent(content)
    val emulatorView = panel.primaryDisplayView!!
    val frameNumber = emulatorView.frameNumber
    assertThat(frameNumber).isEqualTo(0u)
    waitForFrame(ui, emulatorView, fakeEmulator, panel)
    return ui
  }

  private fun waitForFrame(ui: FakeUi, view: EmulatorView, fakeEmulator: FakeEmulator, panel: EmulatorToolWindowPanel) {
    waitForCondition(TIMEOUT) {
      view.emulator.connectionState == EmulatorController.ConnectionState.CONNECTED &&
      view.displayOrientationQuadrants == fakeEmulator.displayRotation.number &&
      view.currentPosture?.posture == fakeEmulator.devicePosture
      fakeEmulator.frameNumber > 0u && renderAndGetFrameNumber(ui, view) == fakeEmulator.frameNumber &&
      settingsButtonIsVisible(ui, panel)
    }
  }

  private fun renderAndGetFrameNumber(fakeUi: FakeUi, view: EmulatorView): UInt {
    fakeUi.render() // The frame number may get updated as a result of rendering.
    return view.frameNumber
  }

  private fun settingsButtonIsVisible(fakeUi: FakeUi, panel: EmulatorToolWindowPanel): Boolean {
    panel.updateMainToolbar()
    return fakeUi.findComponent<ActionButton> { it.action.templateText == SETTINGS_BUTTON_TEXT } != null
  }
}
