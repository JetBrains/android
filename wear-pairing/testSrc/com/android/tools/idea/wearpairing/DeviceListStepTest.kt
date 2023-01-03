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
package com.android.tools.idea.wearpairing

import com.android.ddmlib.IDevice
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.analytics.LoggedUsage
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.observable.BatchInvoker
import com.android.tools.idea.observable.TestInvokeStrategy
import com.android.tools.idea.wearpairing.ConnectionState.DISCONNECTED
import com.android.tools.idea.wearpairing.ConnectionState.ONLINE
import com.android.tools.idea.wizard.model.ModelWizard
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.WearPairingEvent
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseEvent
import java.util.function.BooleanSupplier
import javax.swing.JEditorPane
import javax.swing.JMenuItem
import javax.swing.Popup
import javax.swing.PopupFactory

class DeviceListStepTest : LightPlatform4TestCase() {
  private var defaultPopupFactory: PopupFactory = PopupFactory.getSharedInstance()
  private val invokeStrategy = TestInvokeStrategy()
  /** A UsageTracker implementation that allows introspection of logged metrics in tests. */
  private val usageTracker = TestUsageTracker(VirtualTimeScheduler())
  private val model = WearDevicePairingModel()
  private val phoneDevice = PairingDevice(
    deviceID = "id1", displayName = "My Phone", apiLevel = 30, isWearDevice = false, isEmulator = true, hasPlayStore = true,
    state = ONLINE
  )
  private val wearDevice = PairingDevice(
    deviceID = "id2", displayName = "Round Watch", apiLevel = 30, isEmulator = true, isWearDevice = true, hasPlayStore = true,
    state = ONLINE
  )

  override fun setUp() {
    // Studio Icons must be of type CachedImageIcon for image asset
    IconLoaderRule.enableIconLoading()
    super.setUp()
    BatchInvoker.setOverrideStrategy(invokeStrategy)
    UsageTracker.setWriterForTest(usageTracker)
    WearPairingManager.getInstance().loadSettings(emptyList(), emptyList()) // Clean up any pairing data leftovers
  }

  override fun tearDown() {
    try {
      PopupFactory.setSharedInstance(defaultPopupFactory)
      BatchInvoker.clearOverrideStrategy()
      usageTracker.close()
      UsageTracker.cleanAfterTesting()
    }
    finally {
      super.tearDown()
    }
  }

  @Test
  fun stepShouldShowTwoEmptyListsWhenNoDevicesAvailable() {
    val wizardTest = WizardActionTest()
    val fakeUi = createDeviceListStepUi(wizardTest)

    fakeUi.getPhoneEmptyComponent().apply {
      assertThat(isVisible).isTrue()
      assertThat(text).contains("No devices available.")
      assertThat(text).contains("Create a phone emulator in")
    }

    fakeUi.getWearEmptyComponent().apply {
      assertThat(isVisible).isTrue()
      assertThat(text).contains("No Wear OS emulators")
      assertThat(text).contains("available. Create a Wear OS")
    }

    assertThat(wizardTest.closeCalled).isFalse()
    val emptyComponent = fakeUi.getPhoneEmptyComponent()
    val linkPoint = fakeUi.getPosition(emptyComponent)
    linkPoint.translate(emptyComponent.width / 2, emptyComponent.height - 10)
    fakeUi.mouse.click(linkPoint.x, linkPoint.y)
    assertThat(wizardTest.closeCalled).isTrue()
  }

  @Test
  fun stepShouldShowEmptyPhoneListWhenNoPhonesAvailable() {
    val fakeUi = createDeviceListStepUi()
    model.wearList.set(listOf(wearDevice))

    assertThat(fakeUi.getPhoneEmptyComponent().isVisible).isTrue()
    assertThat(fakeUi.getWearList().isEmpty).isFalse()
    assertThat(getWearPairingTrackingEvents().last().studioEvent.kind).isEqualTo(AndroidStudioEvent.EventKind.WEAR_PAIRING)
    assertThat(getWearPairingTrackingEvents().last().studioEvent.wearPairingEvent.kind).isEqualTo(WearPairingEvent.EventKind.SHOW_ASSISTANT_FULL_SELECTION)
  }

  @Test
  fun stepShouldShowEmptyWearListWhenNoWearsAvailable() {
    val fakeUi = createDeviceListStepUi()
    model.phoneList.set(listOf(phoneDevice))

    assertThat(fakeUi.getPhoneList().isEmpty).isFalse()
    assertThat(fakeUi.getWearEmptyComponent().isVisible).isTrue()
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

    fakeUi.getSplitter().apply {
      assertThat(firstComponent).isNotNull()
      assertThat(secondComponent).isNotNull()
    }
  }

  @Test
  fun stepShouldShowOnlyPhoneList() {
    model.phoneList.set(listOf(phoneDevice))
    model.wearList.set(listOf(wearDevice))
    model.selectedWearDevice.setNullableValue(wearDevice)

    createDeviceListStepUi().getSplitter().apply {
      assertThat(firstComponent).isNotNull()
      assertThat(secondComponent).isNull()
    }
    assertThat(getWearPairingTrackingEvents().last().studioEvent.wearPairingEvent.kind).isEqualTo(WearPairingEvent.EventKind.SHOW_ASSISTANT_PRE_SELECTION)
  }

  @Test
  fun stepShouldShowOnlyWearList() {
    model.selectedPhoneDevice.setNullableValue(phoneDevice)

    createDeviceListStepUi().getSplitter().apply {
      assertThat(firstComponent).isNull()
      assertThat(secondComponent).isNotNull()
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
    val topLabelIcon = topLabel.icon as IconLoader.CachedImageIcon
    assertThat(topLabelIcon.originalPath!!).contains("device-play-store.svg")
  }

  @Test
  fun disconnectedListItemDevicesShouldNotBeSelectable() {
    val fakeUi = createDeviceListStepUi()

    model.phoneList.set(listOf(
      phoneDevice, // Selectable
      phoneDevice.copy(deviceID = "id2", displayName = "My Phone2", state = DISCONNECTED),
      phoneDevice.copy(deviceID = "id3", displayName = "My Phone3"), // Selectable
      phoneDevice.copy(deviceID = "id4", displayName = "My Phone4", apiLevel = 29),
      phoneDevice.copy(deviceID = "id5", displayName = "My Phone5", isEmulator = true, hasPlayStore = false),
      phoneDevice.copy(deviceID = "id6", displayName = "My Phone6", state = DISCONNECTED),
      phoneDevice.copy(deviceID = "id7", displayName = "My Phone7"), // Selectable
    ))

    fakeUi.getPhoneList().apply {
      // Assert that list was sorted. Enabled first, disabled last.
      arrayOf("id1", "id3", "id7", "id2", "id4", "id5", "id6").forEachIndexed { index, id ->
        assertThat(model.getElementAt(index).deviceID).isEqualTo(id)
      }

      assertThat(selectedIndex).isEqualTo(0) // The first selectable device is 0

      selectedIndex = 1
      assertThat(selectedIndex).isEqualTo(1) // Selecting 1 should be OK

      selectedIndex = 2
      assertThat(selectedIndex).isEqualTo(2) // Selecting 2 is OK too

      selectedIndex = 3
      assertThat(selectedIndex).isEqualTo(2) // Selecting 3 should be rejected (api level < 30)

      selectedIndex = 4
      assertThat(selectedIndex).isEqualTo(2) // Selecting 4 should be rejected (no play store)

      selectedIndex = 5
      assertThat(selectedIndex).isEqualTo(2) // Selecting 5 should be rejected (paired but disconnected)

      selectedIndex = 6
      assertThat(selectedIndex).isEqualTo(2) // Selecting 6 should be rejected (Disconnected)
    }
  }

  @Test
  fun rightClickOnPairedDeviceShouldOfferPopupToDisconnect() {
    assumeFalse(StudioFlags.PAIRED_DEVICES_TAB_ENABLED.get())

    val fakeUi = createDeviceListStepUi()
    val iDevice = Mockito.mock(IDevice::class.java)
    runBlocking { WearPairingManager.getInstance().createPairedDeviceBridge(phoneDevice, iDevice, wearDevice, iDevice, connect = false) }

    model.phoneList.set(listOf(phoneDevice))
    fakeUi.layoutAndDispatchEvents()

    val phoneList = Mockito.spy(fakeUi.getPhoneList())
    Mockito.doReturn(Point(0, 0)).whenever(phoneList).locationOnScreen // Work around for headless UI

    val cellRect = phoneList.getCellBounds(0, 0)

    val popupFactory = TestPopupFactory().install()
    FakeUi(phoneList).mouse.rightClick(cellRect.width / 2, cellRect.height / 2)

    assertThat(popupFactory.getMenuItemAndHide(0)).isEqualTo("Forget Round Watch connection")
  }

  @Test
  fun showTooltipIfDeviceNotAllowed() {
    val fakeUi = createDeviceListStepUi()
    val iDevice = Mockito.mock(IDevice::class.java)
    runBlocking { WearPairingManager.getInstance().createPairedDeviceBridge(phoneDevice, iDevice, wearDevice, iDevice, connect = false) }

    model.phoneList.set(listOf(
      phoneDevice.copy(deviceID = "id3", displayName = "My Phone2", apiLevel = 29),
      phoneDevice.copy(deviceID = "id4", displayName = "My Phone3", hasPlayStore = false),
      phoneDevice.copy(deviceID = "id5", displayName = "My Phone3", apiLevel = 29, isEmulator = false),
      phoneDevice,
      wearDevice.copy(deviceID = "id6", apiLevel = 25),
    ))
    fakeUi.layoutAndDispatchEvents()

    val phoneList = fakeUi.getPhoneList()

    fun getListItemTooltip(index: Int): String? {
      val rect = phoneList.getCellBounds(index, index)
      val mouseEvent = MouseEvent(phoneList, MouseEvent.MOUSE_ENTERED, 0, 0, rect.width / 2, rect.y + rect.height / 2, 0, false, 0)
      phoneList.mouseListeners.forEach { it.mouseEntered(mouseEvent) } // Simulate mouse enter
      phoneList.mouseListeners.forEach { it.mousePressed(mouseEvent) } // Fix javax.swing.ToolTipManager memory/focus leak
      val installed = phoneList.getClientProperty("JComponent.helpTooltip") // HelpTooltip.TOOLTIP_PROPERTY is private
      installed.javaClass.superclass.getDeclaredField("masterPopupOpenCondition").apply { // "description" is private, use reflection
        isAccessible = true
        if (!(get(installed) as BooleanSupplier).asBoolean) {
          return null
        }
      }
      installed.javaClass.superclass.getDeclaredField("description").apply { // "description" is private, use reflection
        isAccessible = true
        return get(installed)?.toString()
      }
    }

    // Assert that list was sorted. Enabled first, disabled last.
    arrayOf("id5", "id1", "id3", "id4", "id6").forEachIndexed { index, id ->
      assertThat(phoneList.model.getElementAt(index).deviceID).isEqualTo(id)
    }

    assertThat(getListItemTooltip(0)).isNull() // Non emulators are always OK
    if (!StudioFlags.PAIRED_DEVICES_TAB_ENABLED.get()) {
      assertThat(getListItemTooltip(1)).contains("Paired with Round Watch")
    }
    assertThat(getListItemTooltip(2)).contains("Wear pairing requires API level >= 30")
    assertThat(getListItemTooltip(3)).contains("Wear pairing requires Google Play")
    assertThat(getListItemTooltip(4)).contains("Wear pairing requires API level >= 28")
  }

  private fun createDeviceListStepUi(wizardAction: WizardAction = WizardActionTest()): FakeUi {
    val deviceListStep = DeviceListStep(model, project, wizardAction)
    val modelWizard = ModelWizard.Builder().addStep(deviceListStep).build()
    Disposer.register(testRootDisposable, modelWizard)
    Disposer.register(testRootDisposable, deviceListStep)
    invokeStrategy.updateAllSteps()

    modelWizard.contentPanel.size = Dimension(600, 400)
    return FakeUi(modelWizard.contentPanel).apply { layoutAndDispatchEvents() }
  }

  private fun FakeUi.getPhoneList() = getComponent<JBList<PairingDevice>> { it.name == "phoneList" }

  private fun FakeUi.getWearList() = getComponent<JBList<PairingDevice>> { it.name == "wearList" }

  private fun FakeUi.getPhoneEmptyComponent() = getComponent<JEditorPane> { it.name == "phoneListEmptyText" }

  private fun FakeUi.getWearEmptyComponent() = getComponent<JEditorPane> { it.name == "wearListEmptyText" }

  private fun FakeUi.getLabelWithText(text: String) = getComponent<JBLabel> { it.text == text }

  private fun FakeUi.getSplitter() = getComponent<Splitter> { true }

  private fun getWearPairingTrackingEvents(): List<LoggedUsage> =
    usageTracker.usages.filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.WEAR_PAIRING }

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