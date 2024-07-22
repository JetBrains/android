/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.wearwhs.action

import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceInfo
import com.android.adblib.DeviceState.ONLINE
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.testing.DeviceProvisionerRule
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.retryUntilPassing
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.emulator.EMULATOR_CONTROLLER_KEY
import com.android.tools.idea.streaming.emulator.EMULATOR_VIEW_KEY
import com.android.tools.idea.streaming.emulator.EmulatorConfiguration
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.EmulatorId
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import java.nio.file.Paths
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OpenWearHealthServicesPanelActionTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()
  @get:Rule val fakePopupRule = JBPopupRule()
  @get:Rule val deviceProvisionerRule = DeviceProvisionerRule()

  private lateinit var emulatorController: EmulatorController
  private lateinit var emulatorView: EmulatorView
  private lateinit var actionEvent: AnActionEvent

  @Before
  fun setUp() {
    StudioFlags.WEAR_HEALTH_SERVICES_PANEL.override(true)
    val emulatorConfig =
      mock<EmulatorConfiguration>().also {
        whenever(it.api).thenReturn(33)
        whenever(it.deviceType).thenReturn(DeviceType.WEAR)
      }
    emulatorController =
      mock<EmulatorController>().also {
        whenever(it.emulatorId)
          .thenReturn(
            EmulatorId(
              0,
              null,
              null,
              "avdId",
              "avdFolder",
              Paths.get("avdPath"),
              0,
              0,
              emptyList(),
              "",
            )
          )
        whenever(it.emulatorConfig).thenReturn(emulatorConfig)
        Disposer.register(projectRule.testRootDisposable, it)
      }
    emulatorView = EmulatorView(projectRule.testRootDisposable, emulatorController, 0, null, false)

    val dataContext =
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, projectRule.project)
        .add(EMULATOR_VIEW_KEY, emulatorView)
        .add(EMULATOR_CONTROLLER_KEY, emulatorController)
        .build()

    actionEvent =
      AnActionEvent(null, dataContext, "", Presentation(), ActionManager.getInstance(), 0)

    val deviceProvisionerService: DeviceProvisionerService = mock()
    projectRule.project.replaceService(
      DeviceProvisionerService::class.java,
      deviceProvisionerService,
      projectRule.testRootDisposable,
    )
    whenever(deviceProvisionerService.deviceProvisioner)
      .thenReturn(deviceProvisionerRule.deviceProvisioner)
  }

  @Test
  fun `OpenWearHealthServicesPanelAction opens popup`() {
    val action = OpenWearHealthServicesPanelAction()

    action.actionPerformed(actionEvent)
    assertThat(fakePopupRule.fakePopupFactory.balloonCount).isEqualTo(1)
  }

  @Test
  fun `OpenWearHealthServicesPanelAction is disabled when the emulator is disconnected`() {
    val action = OpenWearHealthServicesPanelAction()
    val emulator =
      deviceProvisionerRule.deviceProvisionerPlugin.addNewDevice(emulatorView.deviceSerialNumber)
    whenever(emulatorController.connectionState)
      .thenReturn(EmulatorController.ConnectionState.DISCONNECTED)
    emulator.stateFlow.update { DeviceState.Disconnected(it.properties) }

    action.update(actionEvent)

    assertFalse(actionEvent.presentation.isEnabled)
  }

  @Test
  fun `OpenWearHealthServicesPanelAction is enabled when the emulator is connected`() {
    val action = OpenWearHealthServicesPanelAction()
    val emulator =
      deviceProvisionerRule.deviceProvisionerPlugin.addNewDevice(emulatorView.deviceSerialNumber)
    val mockDevice =
      mock<ConnectedDevice>().also {
        whenever(it.deviceInfoFlow)
          .thenReturn(MutableStateFlow(DeviceInfo(emulatorView.deviceSerialNumber, ONLINE)))
      }
    emulator.stateFlow.update { DeviceState.Connected(it.properties, mockDevice) }
    whenever(emulatorController.connectionState)
      .thenReturn(EmulatorController.ConnectionState.CONNECTED)

    retryUntilPassing(5.seconds) {
      action.update(actionEvent)
      assertTrue(actionEvent.presentation.isEnabled)
    }
  }
}
