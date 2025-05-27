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
import com.android.resources.Density
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.State
import com.android.tools.configurations.Configuration
import com.android.tools.configurations.ConfigurationListener
import com.android.tools.configurations.updateScreenSize
import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.getDimensionsInDp
import com.android.tools.idea.compose.preview.util.previewElement
import com.android.tools.idea.configurations.DeviceGroup
import com.android.tools.idea.configurations.groupDevices
import com.android.tools.idea.preview.Colors
import com.android.tools.idea.preview.util.getSdkDevices
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.visual.getDeviceGroupsSortedAsMap
import com.android.tools.preview.UNDEFINED_DIMENSION
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Disposer
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil.invokeLaterIfNeeded
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Point
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.SwingConstants
import kotlin.math.roundToInt
import org.jetbrains.annotations.TestOnly

private const val textFieldWidth = 60

/**
 * Panel that allows resizing the preview by selecting a device or entering custom dimensions. It is
 * displayed in the toolbar of the Compose Preview.
 */
class ResizePanel(parentDisposable: Disposable) : JBPanel<ResizePanel>(), Disposable {

  /*
   * Declares the UI components that constitute the ResizePanel.
   * These components are arranged horizontally using a FlowLayout, creating
   * the visual structure for device selection, custom dimension input, and panel visibility control:
   *
   * [ Device Picker ▼ ]  [ WidthTF ] x [ HeightTF ] dp  [X]
   * (devicePickerBtn)    (widthTxtF) (x) (heightTxtF) (unitLbl) (closeBtn)
   *
   * - devicePickerButton: Displays the current device/state (e.g., "Pixel 5", "Custom")
   * and triggers a popup list of devices for selection.
   * - widthTextField:    Text field for manually inputting the desired width in dp.
   * - xLabel:            A simple label displaying the 'x' character as a separator.
   * - heightTextField:   Text field for manually inputting the desired height in dp.
   * - unitLabel:         A label displaying the measurement unit, typically "dp".
   * - closeButton:       A button to hide this ResizePanel and revert the preview to its original device/state.
   */
  private val devicePickerButton: JButton
  private val widthTextField: JBTextField
  private val xLabel: JBLabel
  private val heightTextField: JBTextField
  private val unitLabel: JBLabel
  private val closeButton: JButton

  private var currentPopupListItems: List<DropDownListItem> = emptyList()
  private var currentModuleForList: Module? = null
  private var currentFocusedPreviewElement: PsiComposePreviewElementInstance? = null
  private var currentSceneManager: LayoutlibSceneManager? = null

  private var isUpdatingFromConfig = false
  private var currentConfiguration: Configuration? = null

  private var originalDeviceSnapshot: Device? = null
  private var originalDeviceStateSnapshot: State? = null
  private val LOG = Logger.getInstance(ResizePanel::class.java)

  /**
   * Indicates whether the preview has been resized using this panel at least once since the panel
   * was last cleared or initialized.
   */
  var hasBeenResized: Boolean = false
    private set

  /**
   * Listener responsible for reacting to device configuration changes to trigger a re-render of the
   * preview and update its LayoutParams. This instance is created and managed by ResizePanel for
   * the current [SceneManager].
   */
  private var renderTriggerListener: ConfigurationResizeListener? = null

  /**
   * Listener responsible for updating ResizePanel's own UI elements (e.g., text fields, visibility)
   * when the device configuration changes, potentially due to external factors or actions from this
   * panel itself.
   */
  private val resizePanelUiUpdaterListener = ConfigurationListener { flags ->
    if ((flags and ConfigurationListener.CFG_DEVICE) != 0) {
      if (currentConfiguration != null) {
        hasBeenResized = true
        val panelWasHidden = !isVisible
        if (panelWasHidden) {
          isVisible = true
        }
      }
      updatePanelFromConfiguration()
    }
    true
  }

  init {
    layout = FlowLayout(FlowLayout.CENTER, JBUI.scale(4), JBUI.scale(2))
    border = JBEmptyBorder(2)
    background = Colors.DEFAULT_BACKGROUND_COLOR

    devicePickerButton = setupDevicePickerButton()
    widthTextField = JBTextField()
    xLabel = JBLabel("x")
    heightTextField = JBTextField()
    unitLabel = JBLabel(SdkConstants.UNIT_DP)
    closeButton = getCloseButton()

    val textFieldPreferredWidth = JBUI.scale(textFieldWidth)
    widthTextField.preferredSize =
      Dimension(textFieldPreferredWidth, widthTextField.preferredSize.height)
    heightTextField.preferredSize =
      Dimension(textFieldPreferredWidth, heightTextField.preferredSize.height)

    add(devicePickerButton)
    add(widthTextField)
    add(xLabel)
    add(heightTextField)
    add(unitLabel)
    add(closeButton)

    Disposer.register(parentDisposable, this)
    clearAndDisablePanel()

    setUpTextFieldListeners()
    isVisible = false
  }

  private fun getCloseButton(): JButton {
    val closeButton = JButton(AllIcons.Actions.Close)
    closeButton.isBorderPainted = false
    closeButton.isContentAreaFilled = false
    closeButton.isOpaque = false
    closeButton.toolTipText = message("resize.panel.hide.revert.tooltip")
    closeButton.addActionListener { revertResizingAndHidePanel() }
    return closeButton
  }

  /**
   * Reverts the preview to its original device and state, and hides the resize panel. This action
   * is triggered by the close button.
   */
  private fun revertResizingAndHidePanel() {
    revertResizing()
    isVisible = false
  }

  /**
   * Reverts the preview to its original device and state. This is called when the user clicks the
   * close button or selects the "Original" device option from the dropdown.
   */
  private fun revertResizing() {
    currentSceneManager?.sceneRenderConfiguration?.clearOverrideRenderSize = true
    currentSceneManager?.forceNextResizeToWrapContent = isOriginalPreviewSizeModeWrap()
    currentConfiguration?.setEffectiveDevice(originalDeviceSnapshot, originalDeviceStateSnapshot)

    hasBeenResized = false
  }

  /** Clears the panel's state, removing any existing configuration and hiding it. */
  fun clearPanelAndHidePanel() {
    currentConfiguration?.removeListener(resizePanelUiUpdaterListener)
    renderTriggerListener?.let { existingListener ->
      Disposer.dispose(existingListener)
      currentConfiguration?.removeListener(existingListener)
    }
    renderTriggerListener = null
    currentSceneManager = null
    currentConfiguration = null
    currentFocusedPreviewElement = null
    currentModuleForList = null
    hasBeenResized = false
    isVisible = false
  }

  /**
   * Sets up listeners for the width and height text fields to update the device configuration when
   * dimensions are changed by the user.
   *
   * Updates are triggered under the following conditions, provided the panel is not currently being
   * updated programmatically (`isUpdatingFromConfig` is false) and a valid `currentConfiguration`
   * exists:
   * - When either the width or height text field loses focus after a potential edit.
   * - When an action event (pressing Enter) occurs in either text field.
   */
  private fun setUpTextFieldListeners() {
    val dimensionChangeListener =
      object : FocusAdapter() {
        override fun focusLost(e: FocusEvent?) {
          if (isUpdatingFromConfig || currentConfiguration == null) return
          updateConfigurationFromTextFields()
        }
      }
    widthTextField.addFocusListener(dimensionChangeListener)
    heightTextField.addFocusListener(dimensionChangeListener)
    widthTextField.addActionListener {
      if (!isUpdatingFromConfig && currentConfiguration != null) updateConfigurationFromTextFields()
    }
    heightTextField.addActionListener {
      if (!isUpdatingFromConfig && currentConfiguration != null) updateConfigurationFromTextFields()
    }
  }

  private fun setupDevicePickerButton(): JButton {
    val button = JButton()
    button.text = message("device.name.custom")
    button.icon = AllIcons.General.ArrowDown
    button.horizontalAlignment = SwingConstants.RIGHT
    button.horizontalTextPosition = SwingConstants.LEFT

    button.isBorderPainted = false
    button.isContentAreaFilled = false
    button.isOpaque = false

    button.addActionListener {
      val step = DeviceListPopupStep(currentPopupListItems, this::handleDeviceSelectionFromPopup)
      val popup = JBPopupFactory.getInstance().createListPopup(step)
      popup.show(RelativePoint(button, Point(0, button.height)))
    }
    return button
  }

  private fun handleDeviceSelectionFromPopup(selectedItem: DropDownListItem) {
    if (currentConfiguration == null && selectedItem !is DropDownListItem.OriginalItem) return

    if (selectedItem is DropDownListItem.OriginalItem) {
      revertResizing()
    } else if (selectedItem is DropDownListItem.DeviceItem) {
      currentConfiguration?.setDevice(selectedItem.device, false)
    }
  }

  /**
   * Builds the base list of items for the device picker popup, consisting of SDK and AVD devices
   * grouped by their categories.
   *
   * The resulting list serves as the primary content for the device selection popup. Other dynamic
   * items, like an "Original" device option, may be added to this base list by the caller before
   * displaying the popup. If the provided [module] is null, an empty list is returned.
   *
   * @param module The [Module] context used to retrieve the available devices.
   * @return A [List] of [DropDownListItem]s, structured with headers for each device group and
   *   device items within those groups. Returns an empty list if [module] is null.
   */
  private fun buildBasePopupListItems(module: Module?): List<DropDownListItem> {
    if (module == null) {
      return emptyList()
    }
    val sdkDevices = getSdkDevices(module)
    val groupedDisplayDevices: Map<DeviceGroup, List<Device>> = groupDevices(sdkDevices)
    val popupItems = mutableListOf<DropDownListItem>()

    val sortedGroupedDeviceMap = getDeviceGroupsSortedAsMap(groupedDisplayDevices)

    sortedGroupedDeviceMap.forEach { (group, devicesInGroup) ->
      if (devicesInGroup.isNotEmpty()) {
        popupItems.add(DropDownListItem.DeviceGroupHeaderItem(group.displayName))
        devicesInGroup.forEach { device -> popupItems.add(DropDownListItem.DeviceItem(device)) }
      }
    }
    return popupItems
  }

  /**
   * Sets the [LayoutlibSceneManager] for the [ResizePanel], providing context for its operations.
   * This method is called when the focused preview element (and thus its SceneManager) changes.
   *
   * @param sceneManager The [LayoutlibSceneManager] associated with the currently focused preview,
   *   or null if none.
   */
  fun setSceneManager(sceneManager: LayoutlibSceneManager?) {
    clearPanelAndHidePanel()

    currentSceneManager = sceneManager
    val model = sceneManager?.model
    currentConfiguration = model?.configuration
    currentModuleForList = model?.module
    currentFocusedPreviewElement = model?.dataProvider?.previewElement()

    originalDeviceSnapshot = currentConfiguration?.device
    originalDeviceStateSnapshot = currentConfiguration?.deviceState
    currentConfiguration?.addListener(resizePanelUiUpdaterListener)
    currentConfiguration?.let { configuration ->
      currentSceneManager?.let { sceneManager ->
        renderTriggerListener =
          ConfigurationResizeListener(sceneManager, configuration).also {
            configuration.addListener(it)
          }
      }
    }
    updatePanelFromConfiguration()
  }

  /**
   * Determines if the original @Preview annotation for the currently focused element implies a
   * "wrap content" sizing behavior when in shrink mode.
   *
   * This is true if:
   * 1. The preview is in "shrink mode" (showDecorations = false).
   * 2. The original @Preview annotation did not specify explicit widthDp or heightDp.
   */
  private fun isOriginalPreviewSizeModeWrap(): Boolean { // Renamed
    val element = currentFocusedPreviewElement ?: return false

    val isShrinkMode = !element.displaySettings.showDecoration

    if (!isShrinkMode) {
      return false
    }

    val originalAnnotationConfig = element.configuration
    val originalDefinesNoExplicitDimensions =
      originalAnnotationConfig.width == UNDEFINED_DIMENSION &&
        originalAnnotationConfig.height == UNDEFINED_DIMENSION

    return originalDefinesNoExplicitDimensions
  }

  private fun setEnabledIncludingChildren(enabled: Boolean) {
    isEnabled = enabled
    listOf(devicePickerButton, widthTextField, xLabel, heightTextField, unitLabel).forEach {
      it.isEnabled = enabled
    }
  }

  private fun clearAndDisablePanel() {
    isUpdatingFromConfig = true
    devicePickerButton.text = message("device.name.custom")
    currentPopupListItems = emptyList()
    widthTextField.text = ""
    heightTextField.text = ""
    setEnabledIncludingChildren(false)
    isUpdatingFromConfig = false
  }

  private fun updateConfigurationFromTextFields() {
    val config = currentConfiguration ?: return
    val newWidthDp = widthTextField.text.toIntOrNull()
    val newHeightDp = heightTextField.text.toIntOrNull()

    if (newWidthDp != null && newHeightDp != null && newWidthDp > 0 && newHeightDp > 0) {
      val (currentConfigWidthDp, currentConfigHeightDp) = getDimensionsInDp(config)
      if (newWidthDp == currentConfigWidthDp && newHeightDp == currentConfigHeightDp) {
        return
      }
      val dpi = config.density.dpiValue
      if (dpi <= 0) {
        LOG.warn("Cannot update screen size, invalid DPI: $dpi")
        return
      }
      val widthPx = (newWidthDp * dpi / Density.DEFAULT_DENSITY.toFloat()).roundToInt()
      val heightPx = (newHeightDp * dpi / Density.DEFAULT_DENSITY.toFloat()).roundToInt()
      config.updateScreenSize(widthPx, heightPx)
    }
  }

  private fun updatePanelFromConfiguration() {
    val config = currentConfiguration
    invokeLaterIfNeeded { updatePanelFromConfigurationInternal(config) }
  }

  private fun updatePanelFromConfigurationInternal(config: Configuration?) {
    isUpdatingFromConfig = true

    if (config == null || config.deviceState == null) {
      clearAndDisablePanel()
      isUpdatingFromConfig = false
      return
    }

    val baseItems = buildBasePopupListItems(currentModuleForList)
    val finalPopupItems = mutableListOf<DropDownListItem>()
    if (hasBeenResized && originalDeviceSnapshot != null) {
      finalPopupItems.add(DropDownListItem.OriginalItem)
    }
    finalPopupItems.addAll(baseItems)
    currentPopupListItems = finalPopupItems

    val targetDevice = config.cachedDevice
    if (targetDevice != null && targetDevice.id != Configuration.CUSTOM_DEVICE_ID) {
      devicePickerButton.text = targetDevice.displayName
    } else {
      devicePickerButton.text = message("device.name.custom")
    }

    val (wDp, hDp) = getDimensionsInDp(config)
    widthTextField.text = wDp.toString()
    heightTextField.text = hDp.toString()

    setEnabledIncludingChildren(true)

    devicePickerButton.isEnabled =
      currentPopupListItems.any { it !is DropDownListItem.DeviceGroupHeaderItem }
    isUpdatingFromConfig = false
  }

  override fun dispose() {
    clearPanelAndHidePanel()
  }

  @TestOnly
  fun getCurrentPreviewElementForTest(): PsiComposePreviewElementInstance? {
    return currentFocusedPreviewElement
  }
}

/**
 * Represents the different types of items that can appear in the device picker dropdown. This
 * includes actual devices, headers for device groups, and special command items.
 */
sealed class DropDownListItem {
  /**
   * Represents a non-selectable header in the device dropdown list, used to visually categorize a
   * group of [DeviceItem]s.
   *
   * @param displayName The text to be displayed for this group header (e.g., "Phones", "Tablets").
   */
  data class DeviceGroupHeaderItem(val displayName: String) : DropDownListItem()

  /**
   * Represents a selectable device in the dropdown list.
   *
   * @param device The actual [Device] instance.
   */
  data class DeviceItem(val device: Device) : DropDownListItem()

  /**
   * Represents the special "Original" command item in the dropdown list, allowing the user to
   * revert to the original device configuration.
   */
  object OriginalItem : DropDownListItem()
}

private class DeviceListPopupStep(
  items: List<DropDownListItem>,
  private val onSelectionCallback: (DropDownListItem) -> Unit,
) : BaseListPopupStep<DropDownListItem>(null, items) {

  override fun getTextFor(value: DropDownListItem): String {
    return when (value) {
      is DropDownListItem.DeviceGroupHeaderItem -> value.displayName
      is DropDownListItem.DeviceItem -> value.device.displayName
      is DropDownListItem.OriginalItem -> message("device.name.original")
    }
  }

  override fun getIconFor(value: DropDownListItem): Icon? = null

  override fun onChosen(selectedValue: DropDownListItem, finalChoice: Boolean): PopupStep<*>? {
    if (isSelectable(selectedValue)) {
      onSelectionCallback(selectedValue)
    }
    return super.onChosen(selectedValue, finalChoice)
  }

  override fun isSelectable(value: DropDownListItem): Boolean {
    return value !is DropDownListItem.DeviceGroupHeaderItem
  }

  /**
   * Determines if a [ListSeparator] should be rendered above the given [value] in the popup list.
   *
   * A separator is added before any [DropDownListItem.DeviceGroupHeaderItem] unless it is the first
   * item in the entire list. This helps visually distinguish the start of new device categories.
   */
  override fun getSeparatorAbove(value: DropDownListItem): ListSeparator? {
    val currentIndex = values.indexOf(value)
    if (value is DropDownListItem.DeviceGroupHeaderItem && currentIndex > 0) {
      return ListSeparator()
    }
    return null
  }
}
