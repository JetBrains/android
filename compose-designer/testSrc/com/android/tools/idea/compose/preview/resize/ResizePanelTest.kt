/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.resize

import com.android.SdkConstants
import com.android.sdklib.devices.Device
import com.android.tools.adtui.stdui.OUTLINE_PROPERTY
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.adtui.swing.popup.PopupRule
import com.android.tools.configurations.Configuration
import com.android.tools.configurations.deviceSizeDp
import com.android.tools.idea.actions.SetDeviceAction
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.configurations.isReferenceDevice
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.EdtNoGetDataProvider
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.ui.components.fields.IntegerField
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.FocusEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ResizePanelTest {
  private val projectRule = AndroidProjectRule.withSdk()
  private val popupRule = PopupRule()

  @get:Rule val rule = RuleChain(projectRule, popupRule)

  private lateinit var resizePanel: ResizePanel
  private lateinit var fakeUi: FakeUi
  private lateinit var configuration: Configuration
  private lateinit var sceneManager: LayoutlibSceneManager
  private lateinit var model: NlModel
  private lateinit var surface: DesignSurface<LayoutlibSceneManager>

  @Before
  fun setUp() = runBlocking {
    model =
      NlModelBuilderUtil.model(
          projectRule,
          "layout",
          "layout.xml",
          ComponentDescriptor(SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER),
        )
        .build()
    configuration = model.configuration

    surface = NlSurfaceBuilder.builder(projectRule.project, projectRule.testRootDisposable).build()
    sceneManager = surface.addModelsWithoutRender(listOf(model)).single()
    resizePanel = ResizePanel(projectRule.fixture.testRootDisposable)

    val provider = EdtNoGetDataProvider { sink -> DataSink.uiDataSnapshot(sink, resizePanel) }
    (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider(
      provider,
      projectRule.testRootDisposable,
    )

    runInEdtAndGet {
      val panel =
        JPanel().apply {
          size = Dimension(500, 100)
          add(resizePanel)
        }
      fakeUi = FakeUi(panel, 1.0, true, parentDisposable = projectRule.testRootDisposable)
    }
  }

  @After
  fun tearDown() {
    Disposer.dispose(surface)
  }

  @Test fun `panel is initially hidden`() = runInEdtAndGet { assertFalse(resizePanel.isVisible) }

  @Test
  fun `setting scene manager does not show panel`() = runInEdtAndGet {
    resizePanel.setSceneManager(sceneManager)
    assertFalse(resizePanel.isVisible)
  }

  @Test
  fun `external device change makes panel visible`() = runInEdtAndGet {
    val initialDevice = configuration.device
    resizePanel.setSceneManager(sceneManager)
    assertFalse(resizePanel.isVisible)

    val newDevice = findDifferentDevice(initialDevice)
    configuration.setDevice(newDevice, true)

    assertTrue(resizePanel.hasBeenResized)
    assertTrue(resizePanel.isVisible)
  }

  @Test
  fun `selecting device from dropdown updates configuration`() = runInEdtAndGet {
    setupAndShowPanel()
    val selectedDevice = setDifferentDevice()
    assertEquals(selectedDevice.id, configuration.device!!.id)
  }

  @Test
  fun `changing text fields updates configuration`() = runInEdtAndGet {
    setupAndShowPanel()

    val oldWidth = widthTextField.value
    val oldHeight = heightTextField.value

    changeWidthTextField(500)
    changeHeightTextField(800)

    assertNotEquals(
      oldWidth,
      configuration.device?.getScreenSize(configuration.deviceState!!.orientation)?.width,
    )
    assertNotEquals(
      oldHeight,
      configuration.device?.getScreenSize(configuration.deviceState!!.orientation)?.height,
    )
  }

  private fun findAndPerformDeviceMenuAction(predicate: (AnAction) -> Boolean) {
    fakeUi.clickOn(devicePicker)
    fakeUi.layoutAndDispatchEvents()
    val popupMenu = popupRule.popupContents
    assertNotNull("Popup menu not found", popupMenu)
    val actionMenuItem = popupMenu!!.findDescendant<ActionMenuItem> { predicate(it.anAction) }
    assertNotNull("Action not found in popup", actionMenuItem)
    performAction(actionMenuItem!!.anAction)
    popupRule.mockPopup.hide()
    // JBPopupMenu has a Timer that is stopped when made invisible. If not stopped,
    // checkJavaSwingTimersAreDisposed() will throw in some
    // other test.
    popupMenu.isVisible = false
  }

  private fun performAction(action: AnAction) {
    val dataContext = DataManager.getInstance().getDataContext(resizePanel)
    val event = TestActionEvent.createTestEvent(dataContext)
    action.actionPerformed(event)
  }

  private fun setDifferentDevice(deviceToAvoid: Device? = null): Device {
    val currentDevice = deviceToAvoid ?: configuration.device!!
    var selectedDevice: Device? = null
    findAndPerformDeviceMenuAction { action ->
      val setDeviceAction = action as? SetDeviceAction
      if (setDeviceAction != null && setDeviceAction.device.id != currentDevice.id) {
        selectedDevice = setDeviceAction.device
        true
      } else {
        false
      }
    }
    return selectedDevice!!
  }

  @Test
  fun `close button reverts changes and hides panel`() = runInEdtAndGet {
    val initialDevice = configuration.device!!
    val initialDeviceState = configuration.deviceState!!
    setupAndShowPanel()
    setDifferentDevice(initialDevice)
    assertTrue(resizePanel.hasBeenResized)
    assertNotEquals(initialDevice, configuration.device)

    closePanel()

    assertFalse(resizePanel.isVisible)
    assertFalse(resizePanel.hasBeenResized)
    assertEquals(initialDevice, configuration.device)
    assertEquals(initialDeviceState, configuration.deviceState)
  }

  @Test
  fun `reverting to original from dropdown reverts changes`() = runInEdtAndGet {
    val initialDevice = configuration.device!!
    setupAndShowPanel()
    setDifferentDevice(initialDevice)
    revertToOriginal()
    assertFalse(resizePanel.hasBeenResized)
    assertEquals(initialDevice, configuration.device)
  }

  @Test
  fun `reverting to original sets forceUseOriginalSize`() = runInEdtAndGet {
    setupAndShowPanel()
    setDifferentDevice()
    assertFalse(sceneManager.forceNextResizeToUseOriginalSize)
    revertToOriginal()
    assertTrue(sceneManager.forceNextResizeToUseOriginalSize)
  }

  @Test
  fun `changing dimensions sets device to custom`() = runInEdtAndGet {
    val newDevice = setupAndShowPanel()
    assertDevicePickerText(newDevice.displayName)

    changeWidthTextField(widthTextField.value + 1)

    assertEquals("Custom", configuration.device!!.displayName)
    assertDevicePickerText("Custom")
  }

  @Test
  fun `device to custom to device`() = runInEdtAndGet {
    val initialDevice = setupAndShowPanel()

    // 1. Select a device
    val firstDevice = setDifferentDevice()
    assertEquals(firstDevice.id, configuration.device!!.id)
    assertNotEquals(initialDevice.id, configuration.device!!.id)

    // 2. Change dimensions (should set device to "Custom")
    changeWidthTextField(widthTextField.value + 10)
    assertEquals("Custom", configuration.device!!.displayName)

    // 3. Select another device
    val secondDevice = setDifferentDevice(firstDevice)
    assertEquals(secondDevice.id, configuration.device!!.id)
    assertNotEquals(firstDevice.id, configuration.device!!.id)
  }

  @Test
  fun `custom dimensions then revert`() = runInEdtAndGet {
    val initialDevice = setupAndShowPanel()
    changeWidthTextField(widthTextField.value + 10)
    assertNotEquals(initialDevice.id, configuration.device?.id)
    assertEquals("Custom", configuration.device!!.displayName)

    revertToOriginal()

    assertFalse(resizePanel.hasBeenResized)
    assertEquals(initialDevice.id, configuration.device!!.id)
  }

  @Test
  fun `custom dimensions then close`() = runInEdtAndGet {
    val initialDevice = setupAndShowPanel()
    changeWidthTextField(widthTextField.value + 10)
    assertNotEquals(initialDevice.id, configuration.device?.id)
    assertEquals("Custom", configuration.device!!.displayName)

    closePanel()

    assertFalse(resizePanel.isVisible)
    assertFalse(resizePanel.hasBeenResized)
    assertEquals(initialDevice.id, configuration.device!!.id)
  }

  @Test
  fun `device to custom then close`() = runInEdtAndGet {
    val initialDevice = setupAndShowPanel()
    setDifferentDevice()
    changeWidthTextField(widthTextField.value + 10)
    assertNotEquals(initialDevice.id, configuration.device?.id)
    assertEquals("Custom", configuration.device!!.displayName)

    closePanel()

    assertFalse(resizePanel.isVisible)
    assertFalse(resizePanel.hasBeenResized)
    assertEquals(initialDevice.id, configuration.device!!.id)
  }

  @Test
  fun `invalid then valid dimensions`() = runInEdtAndGet {
    setupAndShowPanel()
    val initialWidth = widthTextField.value
    val initialHeight = heightTextField.value

    // Set invalid width
    widthTextField.text = "0"
    loseFocus(widthTextField)
    assertEquals("error", widthTextField.getClientProperty(OUTLINE_PROPERTY))
    assertEquals(null, heightTextField.getClientProperty(OUTLINE_PROPERTY))
    assertEquals(initialWidth, configuration.deviceSizeDp().width)
    assertEquals(initialHeight, configuration.deviceSizeDp().height)

    // Set invalid height
    heightTextField.text = "9999"
    loseFocus(heightTextField)
    assertEquals("error", widthTextField.getClientProperty(OUTLINE_PROPERTY))
    assertEquals("error", heightTextField.getClientProperty(OUTLINE_PROPERTY))
    assertEquals(initialWidth, configuration.deviceSizeDp().width)
    assertEquals(initialHeight, configuration.deviceSizeDp().height)

    // Set valid width
    val newWidth = initialWidth + 50
    changeWidthTextField(newWidth)
    assertEquals(null, widthTextField.getClientProperty(OUTLINE_PROPERTY))
    // Height is still invalid, so configuration should not change
    assertEquals("error", heightTextField.getClientProperty(OUTLINE_PROPERTY))
    assertEquals(initialWidth, configuration.deviceSizeDp().width)
    assertEquals(initialHeight, configuration.deviceSizeDp().height)

    // Set valid height
    val newHeight = initialHeight + 50
    changeHeightTextField(newHeight)
    assertEquals(null, widthTextField.getClientProperty(OUTLINE_PROPERTY))
    assertEquals(null, heightTextField.getClientProperty(OUTLINE_PROPERTY))
    assertEquals(newWidth, configuration.deviceSizeDp().width)
    assertEquals(newHeight, configuration.deviceSizeDp().height)
  }

  @Test
  fun `verify custom dimensions applied`() = runInEdtAndGet {
    setupAndShowPanel()
    val newWidth = 1600
    val newHeight = 400
    changeWidthTextField(newWidth)
    changeHeightTextField(newHeight)

    val (width, height) = configuration.deviceSizeDp()
    assertEquals(newWidth, width)
    assertEquals(newHeight, height)
  }

  @Test
  fun `select reference device`() = runInEdtAndGet {
    setupAndShowPanel()
    // Find a reference device to select
    val referenceDevice = configuration.settings.devices.first { isReferenceDevice(it) }
    findAndPerformDeviceMenuAction { action ->
      (action as? SetDeviceAction)?.device?.id == referenceDevice.id
    }

    assertEquals(referenceDevice.id, configuration.device!!.id)
    assertDevicePickerText(referenceDevice.displayName)
  }

  @Test
  fun `clearing dimension fields shows error`() = runInEdtAndGet {
    setupAndShowPanel()
    val initialWidth = widthTextField.value
    val initialHeight = heightTextField.value

    widthTextField.text = ""
    loseFocus(widthTextField)
    assertEquals("error", widthTextField.getClientProperty(OUTLINE_PROPERTY))
    assertEquals(null, heightTextField.getClientProperty(OUTLINE_PROPERTY))
    assertEquals(initialWidth, configuration.deviceSizeDp().width)
    assertEquals(initialHeight, configuration.deviceSizeDp().height)

    heightTextField.text = ""
    loseFocus(heightTextField)
    assertEquals("error", widthTextField.getClientProperty(OUTLINE_PROPERTY))
    assertEquals("error", heightTextField.getClientProperty(OUTLINE_PROPERTY))
    // Configuration should not have changed
    assertEquals(initialWidth, configuration.deviceSizeDp().width)
    assertEquals(initialHeight, configuration.deviceSizeDp().height)
  }

  @Test
  fun `device to custom to same device`() = runInEdtAndGet {
    setupAndShowPanel()

    // Change to a different device first
    val firstDevice = setDifferentDevice()
    assertDevicePickerText(firstDevice.displayName)

    // Now change the dimensions, which should set the device to "Custom"
    changeWidthTextField(widthTextField.value + 1)
    assertDevicePickerText("Custom")

    // Now, select the same device as before
    findAndPerformDeviceMenuAction { action ->
      (action as? SetDeviceAction)?.device?.id == firstDevice.id
    }

    // Check that the device is now back to the selected device
    assertEquals(firstDevice.id, configuration.device!!.id)
    assertDevicePickerText(firstDevice.displayName)
  }

  @Test
  fun `invalid value is kept on focus lost`() = runInEdtAndGet {
    setupAndShowPanel()
    val invalidValue = "9999" // Larger than MAXIMUM_SIZE_DP
    widthTextField.text = invalidValue
    loseFocus(widthTextField)
    assertEquals(invalidValue, widthTextField.text)
    assertEquals("error", widthTextField.getClientProperty(OUTLINE_PROPERTY))
  }

  @Test
  fun `ui does not update from config if field has error`() = runInEdtAndGet {
    setupAndShowPanel()
    val invalidValue = "99999"
    widthTextField.text = invalidValue
    loseFocus(widthTextField) // This will mark the field with an error
    assertEquals("error", widthTextField.getClientProperty(OUTLINE_PROPERTY))

    // Simulate an external change to the configuration
    val newDevice = findDifferentDevice(configuration.device)
    configuration.setDevice(newDevice, false)

    // The text field with the error should not have been updated
    assertEquals(invalidValue, widthTextField.text)
  }

  @Test
  fun `invalid value is kept on press enter`() = runInEdtAndGet {
    setupAndShowPanel()
    val invalidValue = "0" // Smaller than MINIMUM_SIZE_DP
    heightTextField.text = invalidValue
    pressEnter(heightTextField)
    assertEquals(invalidValue, heightTextField.text)
    assertEquals("error", heightTextField.getClientProperty(OUTLINE_PROPERTY))
  }

  @Test
  fun `dimension fields only accept digits`() = runInEdtAndGet {
    setupAndShowPanel()
    val initialWidth = widthTextField.text

    // Test inserting non-digits
    widthTextField.document.insertString(0, "abc", null)
    assertEquals(initialWidth, widthTextField.text)

    // Test inserting digits
    widthTextField.text = "" // Clear it first
    widthTextField.document.insertString(0, "123", null)
    assertEquals("123", widthTextField.text)
  }

  private fun findDifferentDevice(device: Device?): Device {
    return configuration.settings.devices.first { it.id != device?.id }
  }

  private val devicePicker: ActionButtonWithText
    get() = fakeUi.getComponent { it.action is ResizePanel.DevicePickerAction }

  private val widthTextField: IntegerField
    get() = fakeUi.findAllComponents<IntegerField>()[0]

  private val heightTextField: IntegerField
    get() = fakeUi.findAllComponents<IntegerField>()[1]

  private fun assertDevicePickerText(expected: String) {
    val dataContext = DataManager.getInstance().getDataContext(resizePanel)
    val event = TestActionEvent.createTestEvent(dataContext)
    devicePicker.action.update(event)
    assertEquals(expected, event.presentation.text)
  }

  private fun loseFocus(component: JComponent) {
    val focusEvent = FocusEvent(component, FocusEvent.FOCUS_LOST)
    component.focusListeners.forEach { it.focusLost(focusEvent) }
  }

  private fun pressEnter(component: JTextField) {
    val actionEvent = ActionEvent(component, ActionEvent.ACTION_PERFORMED, null)
    component.actionListeners.forEach { it.actionPerformed(actionEvent) }
  }

  private fun changeWidthTextField(newValue: Int) {
    widthTextField.value = newValue
    loseFocus(widthTextField)
  }

  private fun changeHeightTextField(newValue: Int) {
    heightTextField.value = newValue
    loseFocus(heightTextField)
  }

  private fun revertToOriginal() {
    findAndPerformDeviceMenuAction { it.templateText == "Original" }
  }

  private fun closePanel() {
    val closeButton = fakeUi.findComponent<ActionButton> { it.action is ResizePanel.CloseAction }!!
    fakeUi.clickOn(closeButton)
  }

  private fun setupAndShowPanel(): Device {
    resizePanel.setSceneManager(sceneManager)
    // Make panel visible by changing device
    val initialDevice = configuration.device!!
    resizePanel.isVisible = true

    // Update the toolbars to ensure the components are created and visible
    @Suppress("DEPRECATION") fakeUi.updateToolbars()
    fakeUi.layoutAndDispatchEvents()
    return initialDevice
  }
}
