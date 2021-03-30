/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.wearparing

import com.android.flags.junit.RestoreFlagRule
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.flags.StudioFlags.WEAR_DEVICE_PAIRING_ENABLED
import com.android.tools.idea.observable.BatchInvoker
import com.android.tools.idea.observable.TestInvokeStrategy
import com.android.tools.idea.wearparing.ConnectionState.DISCONNECTED
import com.android.tools.idea.wearparing.ConnectionState.ONLINE
import com.android.tools.idea.wizard.model.ModelWizard
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension

class DeviceListStepTest : LightPlatform4TestCase() {
  @get:Rule
  val restoreFlagRule = RestoreFlagRule(WEAR_DEVICE_PAIRING_ENABLED) // reset flag after test

  private val invokeStrategy = TestInvokeStrategy()
  private val model = WearDevicePairingModel()
  private val phoneDevice = PairingDevice(
    deviceID = "id1", displayName = "My Phone", versionName = "API 28", isWearDevice = false, hasPlayStore = true,
    state = ONLINE, isPaired = false
  )
  private val wearDevice = PairingDevice(
    deviceID = "id2", displayName = "Round Watch", versionName = "API 28", isWearDevice = true, hasPlayStore = true,
    state = ONLINE, isPaired = false
  )

  override fun setUp() {
    super.setUp()

    WEAR_DEVICE_PAIRING_ENABLED.override(true)
    BatchInvoker.setOverrideStrategy(invokeStrategy)
  }

  override fun tearDown() {
    try {
      BatchInvoker.clearOverrideStrategy()
    }
    finally {
      super.tearDown()
    }
  }

  @Test
  fun stepShouldShowTwoEmptyListsWhenNoDevicesAvailable() {
    var emptyListActionWasCalled = false
    val fakeUi = createDeviceListStepUi {
      emptyListActionWasCalled = true
    }

    fakeUi.getPhoneList().apply {
      assertThat(isEmpty).isTrue()
      assertThat(emptyText.component.getCharSequence(true)).isEqualTo("No devices available.")
      assertThat(emptyText.secondaryComponent.getCharSequence(true)).isEqualTo("Create a phone emulator in")
    }

    fakeUi.getWearList().apply {
      assertThat(isEmpty).isTrue()
      assertThat(emptyText.component.getCharSequence(true)).isEqualTo("No Wear OS emulators")
      assertThat(emptyText.secondaryComponent.getCharSequence(true)).isEqualTo("available. Create a Wear OS")
    }

    assertThat(emptyListActionWasCalled).isFalse()
    fakeUi.mouse.click(150, 260)
    assertThat(emptyListActionWasCalled).isTrue()
  }

  @Test
  fun stepShouldShowEmptyPhoneListWhenNoPhonesAvailable() {
    val fakeUi = createDeviceListStepUi()
    model.deviceList.set(listOf(wearDevice))

    assertThat(fakeUi.getPhoneList().isEmpty).isTrue()
    assertThat(fakeUi.getWearList().isEmpty).isFalse()
  }

  @Test
  fun stepShouldShowEmptyWearListWhenNoWearsAvailable() {
    val fakeUi = createDeviceListStepUi()
    model.deviceList.set(listOf(phoneDevice))

    assertThat(fakeUi.getPhoneList().isEmpty).isFalse()
    assertThat(fakeUi.getWearList().isEmpty).isTrue()
  }

  @Test
  fun stepShouldShowTwoLists() {
    val fakeUi = createDeviceListStepUi()
    model.deviceList.set(listOf(phoneDevice, wearDevice))

    // The phone list should have only one element, and should be the phone
    fakeUi.getPhoneList().apply {
      assertThat(isEmpty).isFalse()
      assertThat(model.size).isEqualTo(1)
      assertThat(model.getElementAt(0).deviceID).isEqualTo("id1")
    }

    // The wear list should have only one element, and should be the wear
    fakeUi.getWearList().apply {
      assertThat(isEmpty).isFalse()
      assertThat(model.size).isEqualTo(1)
      assertThat(model.getElementAt(0).deviceID).isEqualTo("id2")
    }
  }

  @Suppress("UnstableApiUsage")
  @Test
  fun listItemShowPlayStoreIcon() {
    val fakeUi = createDeviceListStepUi()
    model.deviceList.set(listOf(phoneDevice))

    val phoneList = fakeUi.getPhoneList()
    val cellList = phoneList.cellRenderer.getListCellRendererComponent(phoneList, phoneList.model.getElementAt(0), 0, true, true)
    val cellListFakeUi = FakeUi(cellList)

    val topLabel = cellListFakeUi.getLabelWithText("My Phone")
    assertThat(topLabel).isNotNull()

    val topLabelIcon = topLabel!!.icon as IconLoader.CachedImageIcon
    assertThat(topLabelIcon.originalPath!!).contains("device-play-store.svg")
  }

  @Test
  fun disconnectedListItemDevicesShouldNotBeSelectable() {
    val fakeUi = createDeviceListStepUi()
    val phoneDevice2 = phoneDevice.copy(deviceID = "id2", displayName = "My Phone2", state = DISCONNECTED)
    val phoneDevice3 = phoneDevice.copy(deviceID = "id3", displayName = "My Phone3")

    model.deviceList.set(listOf(phoneDevice, phoneDevice2, phoneDevice3))

    fakeUi.getPhoneList().apply {
      assertThat(selectedIndex).isEqualTo(-1) // Nothing should be selected at the start

      selectedIndex = 0
      assertThat(selectedIndex).isEqualTo(0) // Selecting 0 is OK

      selectedIndex = 1
      assertThat(selectedIndex).isEqualTo(0) // Selecting 1 should be rejected

      selectedIndex = 2
      assertThat(selectedIndex).isEqualTo(2) // Selecting 2 is OK too
    }
  }

  private fun createDeviceListStepUi(emptyListClickedAction: () -> Unit = {}): FakeUi {
    val deviceListStep = DeviceListStep(model, project, emptyListClickedAction)
    val modelWizard = ModelWizard.Builder().addStep(deviceListStep).build()
    Disposer.register(testRootDisposable, modelWizard)
    invokeStrategy.updateAllSteps()

    modelWizard.contentPanel.size = Dimension(600, 400)
    return FakeUi(modelWizard.contentPanel)
  }

  private fun FakeUi.getPhoneList(): JBList<PairingDevice> = findComponent<JBList<PairingDevice>> { it.name == "phoneList" }!!

  private fun FakeUi.getWearList(): JBList<PairingDevice> = findComponent<JBList<PairingDevice>> { it.name == "wearList" }!!

  private fun FakeUi.getLabelWithText(text: String): JBLabel? = findComponent<JBLabel> { it.text == text }
}