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
import org.mockito.Mockito
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JMenuItem
import javax.swing.Popup
import javax.swing.PopupFactory


class DeviceListStepTest : LightPlatform4TestCase() {
  @get:Rule
  val restoreFlagRule = RestoreFlagRule(WEAR_DEVICE_PAIRING_ENABLED) // reset flag after test

  private var defaultPopupFactory: PopupFactory = PopupFactory.getSharedInstance()
  private val invokeStrategy = TestInvokeStrategy()
  private val model = WearDevicePairingModel()
  private val phoneDevice = PairingDevice(
    deviceID = "id1", displayName = "My Phone", apiLevel = 30, isWearDevice = false, isEmulator = true, hasPlayStore = true,
    state = ONLINE, isPaired = false
  )
  private val wearDevice = PairingDevice(
    deviceID = "id2", displayName = "Round Watch", apiLevel = 30, isEmulator = true, isWearDevice = true, hasPlayStore = true,
    state = ONLINE, isPaired = false
  )

  override fun setUp() {
    super.setUp()

    WEAR_DEVICE_PAIRING_ENABLED.override(true)
    BatchInvoker.setOverrideStrategy(invokeStrategy)
  }

  override fun tearDown() {
    try {
      PopupFactory.setSharedInstance(defaultPopupFactory)
      BatchInvoker.clearOverrideStrategy()
    }
    finally {
      super.tearDown()
    }
  }

  @Test
  fun stepShouldShowTwoEmptyListsWhenNoDevicesAvailable() {
    val wizardTest = WizardActionTest()
    val fakeUi = createDeviceListStepUi(wizardTest)

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

    assertThat(wizardTest.closeCalled).isFalse()
    fakeUi.mouse.click(150, 260)
    assertThat(wizardTest.closeCalled).isTrue()
  }

  @Test
  fun stepShouldShowEmptyPhoneListWhenNoPhonesAvailable() {
    val fakeUi = createDeviceListStepUi()
    model.wearList.set(listOf(wearDevice))

    assertThat(fakeUi.getPhoneList().isEmpty).isTrue()
    assertThat(fakeUi.getWearList().isEmpty).isFalse()
  }

  @Test
  fun stepShouldShowEmptyWearListWhenNoWearsAvailable() {
    val fakeUi = createDeviceListStepUi()
    model.phoneList.set(listOf(phoneDevice))

    assertThat(fakeUi.getPhoneList().isEmpty).isFalse()
    assertThat(fakeUi.getWearList().isEmpty).isTrue()
  }

  @Test
  fun stepShouldShowTwoLists() {
    val fakeUi = createDeviceListStepUi()
    model.phoneList.set(listOf(phoneDevice))
    model.wearList.set(listOf(wearDevice))

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
    model.phoneList.set(listOf(phoneDevice))

    val phoneList = fakeUi.getPhoneList()
    val cellList = phoneList.cellRenderer.getListCellRendererComponent(phoneList, phoneList.model.getElementAt(0), 0, false, false)
    val cellListFakeUi = FakeUi(cellList)

    val topLabel = cellListFakeUi.getLabelWithText("My Phone")
    assertThat(topLabel).isNotNull()

    val topLabelIcon = topLabel!!.icon as IconLoader.CachedImageIcon
    assertThat(topLabelIcon.originalPath!!).contains("device-play-store.svg")
  }

  @Test
  fun disconnectedListItemDevicesShouldNotBeSelectable() {
    val fakeUi = createDeviceListStepUi()

    model.phoneList.set(listOf(
      phoneDevice,
      phoneDevice.copy(deviceID = "id2", displayName = "My Phone2", state = DISCONNECTED),
      phoneDevice.copy(deviceID = "id3", displayName = "My Phone3"),
      phoneDevice.copy(deviceID = "id4", displayName = "My Phone4", apiLevel = 29),
      phoneDevice.copy(deviceID = "id5", displayName = "My Phone5", isEmulator = true, hasPlayStore = false),
      phoneDevice.copy(deviceID = "id6", displayName = "My Phone6", isPaired = true, state = DISCONNECTED),
      phoneDevice.copy(deviceID = "id7", displayName = "My Phone7"),
    ))

    fakeUi.getPhoneList().apply {
      assertThat(selectedIndex).isEqualTo(-1) // Nothing should be selected at the start

      selectedIndex = 0
      assertThat(selectedIndex).isEqualTo(0) // Selecting 0 is OK

      selectedIndex = 1
      assertThat(selectedIndex).isEqualTo(0) // Selecting 1 should be rejected (Disconnected)

      selectedIndex = 2
      assertThat(selectedIndex).isEqualTo(2) // Selecting 2 is OK too

      selectedIndex = 3
      assertThat(selectedIndex).isEqualTo(2) // Selecting 3 should be rejected (api level < 30)

      selectedIndex = 4
      assertThat(selectedIndex).isEqualTo(2) // Selecting 4 should be rejected (no play store)

      selectedIndex = 5
      assertThat(selectedIndex).isEqualTo(2) // Selecting 5 should be rejected (paired but disconnected)

      selectedIndex = 6
      assertThat(selectedIndex).isEqualTo(6) // Selecting 6 is OK
    }
  }

  @Test
  fun rightClickOnPairedDeviceShouldOfferPopupToDisconnect() {
    val fakeUi = createDeviceListStepUi()

    phoneDevice.launch = { throw RuntimeException("Can't launch on tests") } // launch fields needs some value, so it can be copied
    wearDevice.launch = phoneDevice.launch
    WearPairingManager.setPairedDevices(phoneDevice, wearDevice)

    model.phoneList.set(listOf(
      phoneDevice.copy(isPaired = true),
    ))

    val phoneList = Mockito.spy(fakeUi.getPhoneList())
    Mockito.doReturn(Point(0, 0)).`when`(phoneList).locationOnScreen // Work around for headless UI

    val cellRect = phoneList.getCellBounds(0, 0)

    val popupFactory = TestPopupFactory().install()
    FakeUi(phoneList).mouse.rightClick(cellRect.width / 2, cellRect.height / 2)

    assertThat(popupFactory.getMenuItemAndHide(0)).isEqualTo("Forget Round Watch connection")
  }

  @Test
  fun showTooltipIfDeviceNotAllowed() {
    val fakeUi = createDeviceListStepUi()

    model.phoneList.set(listOf(
      phoneDevice.copy(deviceID = "id2", displayName = "My Phone2", apiLevel = 29),
      phoneDevice.copy(deviceID = "id3", displayName = "My Phone3", hasPlayStore = false),
      phoneDevice.copy(deviceID = "id4", displayName = "My Phone3", apiLevel = 29, isEmulator = false),
    ))

    val phoneList = fakeUi.getPhoneList()

    fun getListItemTooltip(index: Int): String? {
      val cellRect = phoneList.getCellBounds(index, index)
      val p = Point(cellRect.width / 2, cellRect.y + cellRect.height / 2)
      val mouseEvent = MouseEvent(phoneList, MouseEvent.MOUSE_ENTERED, 0, 0, p.x, p.y, 0, false, 0)
      return phoneList.getToolTipText(mouseEvent)
    }

    assertThat(getListItemTooltip(0)).contains("Wear paring requires API level >= 30")
    assertThat(getListItemTooltip(1)).contains("Wear pairing requires Google Play")
    assertThat(getListItemTooltip(2)).isNull() // Non emulators are always OK
  }

  private fun createDeviceListStepUi(wizardAction: WizardAction = WizardActionTest()): FakeUi {
    val deviceListStep = DeviceListStep(model, project, wizardAction)
    val modelWizard = ModelWizard.Builder().addStep(deviceListStep).build()
    Disposer.register(testRootDisposable, modelWizard)
    invokeStrategy.updateAllSteps()

    modelWizard.contentPanel.size = Dimension(600, 400)
    return FakeUi(modelWizard.contentPanel)
  }

  private fun FakeUi.getPhoneList(): JBList<PairingDevice> = findComponent { it.name == "phoneList" }!!

  private fun FakeUi.getWearList(): JBList<PairingDevice> = findComponent { it.name == "wearList" }!!

  private fun FakeUi.getLabelWithText(text: String): JBLabel? = findComponent { it.text == text }

  private class TestPopupFactory : PopupFactory() {
    var popupContents: Component? = null

    override fun getPopup(owner: Component?, contents: Component?, x: Int, y: Int): Popup {
      popupContents = contents
      return super.getPopup(owner, contents, x, y, false)
    }

    fun install(): TestPopupFactory {
      setSharedInstance(this)
      return this
    }

    fun getMenuItemAndHide(index: Int): String {
      popupContents?.isVisible = false // Needs to hide, otherwise leaks a Timer
      return ((popupContents as Container).components[index] as JMenuItem).text
    }
  }
}