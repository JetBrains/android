/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Separator
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import icons.StudioIcons
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.verify

/**
 * Unit tests for [DeviceAndApiLevelFilterComboBoxAction].
 */
@RunWith(JUnit4::class)
@RunsInEdt
class DeviceAndApiLevelFilterComboBoxActionTest {

  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(EdtRule())
    .around(disposableRule)

  @Test
  fun allDevicesAreSelectedByDefault() {
    val comboBox = DeviceAndApiLevelFilterComboBoxAction()
    val filter = comboBox.filter

    val actionEvent = TestActionEvent.createTestEvent()
    comboBox.update(actionEvent)

    assertThat(actionEvent.presentation.text).isEqualTo("All devices")
    assertThat(actionEvent.presentation.icon).isEqualTo(StudioIcons.DeviceExplorer.MULTIPLE_DEVICES)
    assertThat(filter(mock())).isTrue()
  }

  @Test
  fun createActionGroup() {
    val comboBox = DeviceAndApiLevelFilterComboBoxAction().apply {
      addDevice(AndroidDevice("id1", "Z-device1", "", AndroidDeviceType.LOCAL_PHYSICAL_DEVICE, AndroidVersion(28)))
      addDevice(AndroidDevice("id2", "A-device2", "A-device2", AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(29)))
      addDevice(AndroidDevice("id3", "B-device3", "B-device3", AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(28)))
    }

    val actions = comboBox.createActionGroup().getChildren(null)
    assertThat(actions).asList().hasSize(7)
    assertThat(actions[0].templateText).isEqualTo("All devices")
    assertThat(actions[1]).isInstanceOf(Separator::class.java)
    assertThat(actions[2].templateText).isEqualTo("API level")
    assertThat(actions[2]).isInstanceOf(ActionGroup::class.java)
    assertThat(actions[3]).isInstanceOf(Separator::class.java)
    assertThat(actions[4].templateText).isEqualTo("A-device2")
    assertThat(actions[5].templateText).isEqualTo("B-device3")
    assertThat(actions[6].templateText).isEqualTo("Z-device1")

    val apiLevelActions = (actions[2] as ActionGroup).getChildren(null)
    assertThat(apiLevelActions).asList().hasSize(2)
    assertThat(apiLevelActions[0].templateText).isEqualTo("API 28")
    assertThat(apiLevelActions[1].templateText).isEqualTo("API 29")
  }

  @Test
  fun filterDeviceByName() {
    val comboBox = DeviceAndApiLevelFilterComboBoxAction()
    val filter = comboBox.filter
    val device1 = AndroidDevice("id1", "device1", "", AndroidDeviceType.LOCAL_PHYSICAL_DEVICE, AndroidVersion(28))
    val device2 = AndroidDevice("id2", "device2", "device2", AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(29))
    val device3 = AndroidDevice("id3", "device3", "device3", AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(28))

    comboBox.addDevice(device1)
    comboBox.addDevice(device2)
    comboBox.addDevice(device3)
    comboBox.createActionGroup().flattenedActions().first { it.templateText == "device2" }.actionPerformed(TestActionEvent.createTestEvent())

    val actionEvent = TestActionEvent.createTestEvent()
    comboBox.update(actionEvent)

    assertThat(actionEvent.presentation.text).isEqualTo("device2")
    assertThat(actionEvent.presentation.icon).isEqualTo(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE)
    assertThat(filter(device1)).isFalse()
    assertThat(filter(device2)).isTrue()
    assertThat(filter(device3)).isFalse()
  }

  @Test
  fun filterDeviceByApiLevel() {
    val comboBox = DeviceAndApiLevelFilterComboBoxAction()
    val filter = comboBox.filter
    val device1 = AndroidDevice("id1", "device1", "", AndroidDeviceType.LOCAL_PHYSICAL_DEVICE, AndroidVersion(28))
    val device2 = AndroidDevice("id2", "device2", "device2", AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(29))
    val device3 = AndroidDevice("id3", "device3", "device3", AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(28))

    comboBox.addDevice(device1)
    comboBox.addDevice(device2)
    comboBox.addDevice(device3)
    comboBox.createActionGroup().flattenedActions().first { it.templateText == "API 28" }.actionPerformed(TestActionEvent.createTestEvent())

    val actionEvent = TestActionEvent.createTestEvent()
    comboBox.update(actionEvent)

    assertThat(actionEvent.presentation.text).isEqualTo("API 28")
    assertThat(actionEvent.presentation.icon).isNull()
    assertThat(filter(device1)).isTrue()
    assertThat(filter(device2)).isFalse()
    assertThat(filter(device3)).isTrue()
  }

  @Test
  fun filterDeviceByApiLevelWithCodename() {
    val comboBox = DeviceAndApiLevelFilterComboBoxAction()
    val filter = comboBox.filter
    val device1 = AndroidDevice("id1", "device1", "device1", AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(30))
    val device2 = AndroidDevice("id2", "device2", "device2", AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(30, "S"))

    // Filter by API S
    comboBox.addDevice(device1)
    comboBox.addDevice(device2)
    comboBox.createActionGroup().flattenedActions().first { it.templateText == "API S" }.actionPerformed(TestActionEvent.createTestEvent())

    val actionEvent = TestActionEvent.createTestEvent()
    comboBox.update(actionEvent)

    assertThat(actionEvent.presentation.text).isEqualTo("API S")
    assertThat(actionEvent.presentation.icon).isNull()
    assertThat(filter(device1)).isFalse()
    assertThat(filter(device2)).isTrue()

    // Filter by API 30
    comboBox.createActionGroup().flattenedActions().first { it.templateText == "API 30" }.actionPerformed(TestActionEvent.createTestEvent())
    val secondActionEvent = TestActionEvent.createTestEvent()
    comboBox.update(secondActionEvent)

    assertThat(secondActionEvent.presentation.text).isEqualTo("API 30")
    assertThat(secondActionEvent.presentation.icon).isNull()
    assertThat(filter(device1)).isTrue()
    assertThat(filter(device2)).isFalse()
  }

  @Test
  fun listenerIsInvokedUponSelection() {
    val mockListener = mock<DeviceAndApiLevelFilterComboBoxActionListener>()
    val comboBox = DeviceAndApiLevelFilterComboBoxAction().apply {
      addDevice(AndroidDevice("id1", "device1", "", AndroidDeviceType.LOCAL_PHYSICAL_DEVICE, AndroidVersion(28)))
      listener = mockListener
    }
    comboBox.createActionGroup().flattenedActions().first { it.templateText == "device1" }.actionPerformed(TestActionEvent.createTestEvent())

    verify(mockListener).onFilterUpdated()
  }

  @Test
  fun selectorIsInvisibleWhenSingleDevice() {
    val comboBox = DeviceAndApiLevelFilterComboBoxAction()
    val device1 = AndroidDevice("id1", "device1", "", AndroidDeviceType.LOCAL_PHYSICAL_DEVICE, AndroidVersion(28))

    comboBox.addDevice(device1)

    val actionEvent = TestActionEvent.createTestEvent()
    comboBox.update(actionEvent)

    assertThat(actionEvent.presentation.isVisible).isFalse()
  }

  @Test
  fun selectorIsVisibleWhenMultipleDevices() {
    val comboBox = DeviceAndApiLevelFilterComboBoxAction()
    val device1 = AndroidDevice("id1", "device1", "", AndroidDeviceType.LOCAL_PHYSICAL_DEVICE, AndroidVersion(28))
    val device2 = AndroidDevice("id2", "device2", "device2", AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(29))

    comboBox.addDevice(device1)
    comboBox.addDevice(device2)

    val actionEvent = TestActionEvent.createTestEvent()
    comboBox.update(actionEvent)

    assertThat(actionEvent.presentation.isVisible).isTrue()
  }

  private fun ActionGroup.flattenedActions(): Sequence<AnAction> = sequence {
    getChildren(null).forEach {
      if (it is ActionGroup) {
        yieldAll(it.flattenedActions())
      } else {
        yield(it)
      }
    }
  }
}