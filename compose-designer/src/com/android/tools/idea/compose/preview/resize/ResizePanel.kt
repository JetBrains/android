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
import com.android.sdklib.devices.State
import com.android.tools.configurations.Configuration
import com.android.tools.configurations.ConfigurationListener
import com.android.tools.configurations.ConversionUtil
import com.android.tools.configurations.deviceSizeDp
import com.android.tools.configurations.updateScreenSize
import com.android.tools.idea.actions.CONFIGURATIONS
import com.android.tools.idea.actions.DeviceChangeListener
import com.android.tools.idea.actions.DeviceMenuAction
import com.android.tools.idea.actions.HAS_BEEN_RESIZED
import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.compose.preview.analytics.ComposeResizeToolingUsageTracker
import com.android.tools.idea.compose.preview.analytics.resizeMode
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.previewElement
import com.android.tools.idea.preview.Colors
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.preview.UNDEFINED_DIMENSION
import com.google.wireless.android.sdk.stats.ResizeComposePreviewEvent
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI.scale
import com.intellij.util.ui.UIUtil.invokeLaterIfNeeded
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.text.NumberFormat
import java.text.ParseException
import java.util.Objects
import javax.swing.JComponent
import javax.swing.JFormattedTextField
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.text.NumberFormatter
import org.jetbrains.annotations.TestOnly

private const val TEXT_FIELD_WIDTH = 60

private const val MINIMUM_SIZE_DP = 1
private const val MAXIMUM_SIZE_DP = 5000

/**
 * Panel that allows resizing the preview by selecting a device or entering custom dimensions. It is
 * displayed in the toolbar of the Compose Preview.
 *
 * The panel looks like: [ Device Picker ▼ ] [ WidthTF ] x [ HeightTF ] dp [X]
 */
class ResizePanel(parentDisposable: Disposable) :
  JBPanel<ResizePanel>(), Disposable, UiDataProvider {

  private val log = Logger.getInstance(ResizePanel::class.java)

  private var currentModuleForList: Module? = null
  private var currentFocusedPreviewElement: PsiComposePreviewElementInstance? = null
  private var currentSceneManager: LayoutlibSceneManager? = null

  private var currentConfiguration: Configuration? = null

  private var originalDeviceSnapshot: Device? = null
  private var originalDeviceStateSnapshot: State? = null

  /**
   * Indicates whether the preview has been resized using this panel at least once since the panel
   * was last cleared or initialized.
   */
  var hasBeenResized: Boolean = false
    private set

  /**
   * Listener responsible for reacting to device configuration changes to trigger a re-render of the
   * preview and update its LayoutParams. This instance is created and managed by ResizePanel for
   * the current [com.android.tools.idea.common.scene.SceneManager].
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
        // Check if the current device or state differs from the original snapshot,
        // indicating that the preview has been resized.
        hasBeenResized =
          !Objects.equals(currentConfiguration?.device, originalDeviceSnapshot) ||
            !Objects.equals(currentConfiguration?.deviceState, originalDeviceStateSnapshot)
        val panelWasHidden = !isVisible
        if (panelWasHidden && hasBeenResized) {
          isVisible = true
        }
      }
      updatePanelFromConfiguration()
    }
    true
  }

  init {
    layout = BorderLayout()
    border = JBEmptyBorder(2)
    background = Colors.DEFAULT_BACKGROUND_COLOR

    // The main action group for the toolbar. Does not include the close button.
    val mainActionGroup =
      DefaultActionGroup().apply {
        add(DevicePickerAction())
        add(DimensionInputsAction())
      }

    val actionToolbar =
      ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, mainActionGroup, true)
    actionToolbar.targetComponent = this
    actionToolbar.component.isOpaque = false // Make the toolbar itself transparent

    val flowingComponentsPanel =
      JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.CENTER, scale(4), scale(2))).apply {
        isOpaque = false
        add(actionToolbar.component)
      }

    add(flowingComponentsPanel, BorderLayout.CENTER)

    val closeButton = createCloseButton()
    add(closeButton, BorderLayout.EAST)
    Disposer.register(parentDisposable, this)
    isEnabled = false
    isVisible = false
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[CONFIGURATIONS] = listOfNotNull(currentConfiguration)
    sink[HAS_BEEN_RESIZED] = hasBeenResized
  }

  /**
   * Reverts the preview to its original device and state. This is called when the user clicks the
   * close button or selects the "Original" device option from the dropdown.
   */
  private fun revertResizing() {
    currentSceneManager?.sceneRenderConfiguration?.clearOverrideRenderSize = true
    currentSceneManager?.forceNextResizeToWrapContent = isOriginalPreviewSizeModeWrap()
    currentConfiguration?.setEffectiveDevice(originalDeviceSnapshot, originalDeviceStateSnapshot)
    ComposeResizeToolingUsageTracker.logResizeReverted(
      currentSceneManager?.scene?.designSurface,
      currentSceneManager?.resizeMode ?: ResizeComposePreviewEvent.ResizeMode.COMPOSABLE_RESIZE,
    )
    hasBeenResized = false
  }

  /** Clears the panel's state, removing any existing configuration and hiding it. */
  fun clearAndHide() {
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
   * Handles the selection of a device from the device picker dropdown. Updates the current
   * configuration's effective device and logs the event.
   *
   * @param selectedItem The selected [Device].
   */
  private fun handleDeviceSelection(selectedItem: Device) {
    currentConfiguration?.setEffectiveDevice(selectedItem, selectedItem.defaultState)
    ComposeResizeToolingUsageTracker.logResizeStopped(
      currentSceneManager?.scene?.designSurface,
      currentSceneManager?.resizeMode ?: ResizeComposePreviewEvent.ResizeMode.COMPOSABLE_RESIZE,
      ResizeComposePreviewEvent.ResizeSource.DROPDOWN,
      selectedItem.id,
    )
  }

  /**
   * Sets the [LayoutlibSceneManager] for the [ResizePanel], providing context for its operations.
   * This method is called when the focused preview element (and thus its SceneManager) changes.
   *
   * @param sceneManager The [LayoutlibSceneManager] associated with the currently focused preview,
   *   or null if none.
   */
  fun setSceneManager(sceneManager: LayoutlibSceneManager?) {
    clearAndHide()

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
  private fun isOriginalPreviewSizeModeWrap(): Boolean {
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

  private fun updatePanelFromConfiguration() {
    val config = currentConfiguration
    invokeLaterIfNeeded {
      if (config == null || config.deviceState == null) {
        isEnabled = false
        return@invokeLaterIfNeeded
      }
      // The ActionToolbar will automatically update the actions via their update methods.
      // No need to manually trigger updates here.
      isEnabled = true
    }
  }

  override fun dispose() {
    clearAndHide()
  }

  @TestOnly
  fun getCurrentPreviewElementForTest(): PsiComposePreviewElementInstance? {
    return currentFocusedPreviewElement
  }

  /**
   * An action that displays a device picker dropdown. This action delegates the creation of the
   * dropdown and the handling of the device selection to a [DeviceMenuAction] to reuse the complex
   * logic for building the device menu.
   */
  private inner class DevicePickerAction :
    DumbAwareAction("Device", "Select a device to resize the preview", AllIcons.General.ArrowDown),
    CustomComponentAction {
    private val deviceMenuAction =
      DeviceMenuAction(
        object : DeviceChangeListener {
          override fun onDeviceChanged(oldDevice: Device?, newDevice: Device) {
            handleDeviceSelection(newDevice)
          }

          override fun onRevertToOriginal() {
            revertResizing()
          }
        }
      )

    override fun actionPerformed(e: AnActionEvent) {
      deviceMenuAction.actionPerformed(e)
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      val button =
        ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
      button.isFocusable = true
      button.setHorizontalTextPosition(SwingConstants.LEADING) // Set text to be before the icon
      return button
    }

    override fun update(e: AnActionEvent) {
      deviceMenuAction.update(e)
      e.presentation.isEnabled = this@ResizePanel.isEnabled
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }
  }

  /**
   * A [CustomComponentAction] that creates and manages the dimension input fields (width and
   * height).
   */
  private inner class DimensionInputsAction : DumbAwareAction(), CustomComponentAction {
    private val widthTextField = createDimensionTextField()
    private val heightTextField = createDimensionTextField()

    init {
      setUpTextFieldListeners()
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      val panel =
        JPanel(FlowLayout(FlowLayout.CENTER, scale(4), scale(2))).apply {
          isOpaque = false
          add(widthTextField)
          add(JBLabel("x"))
          add(heightTextField)
          add(JBLabel(SdkConstants.UNIT_DP))
        }

      // Initial update of the text fields
      updateTextFieldsFromConfiguration()

      return panel
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
      val isEnabled = this@ResizePanel.isEnabled
      e.presentation.isEnabled = isEnabled
      widthTextField.isEnabled = isEnabled
      heightTextField.isEnabled = isEnabled
      updateTextFieldsFromConfiguration()
    }

    private fun updateTextFieldsFromConfiguration() {
      val config = currentConfiguration ?: return
      val (wDp, hDp) = config.deviceSizeDp()
      if (widthTextField.value != wDp) {
        widthTextField.value = wDp
      }
      if (heightTextField.value != hDp) {
        heightTextField.value = hDp
      }
    }

    private fun createDimensionTextField(): JFormattedTextField {
      val formatter =
        NumberFormatter(NumberFormat.getIntegerInstance().apply { isGroupingUsed = false }).apply {
          minimum = MINIMUM_SIZE_DP
          maximum = MAXIMUM_SIZE_DP
        }
      return JFormattedTextField(formatter).apply {
        preferredSize = Dimension(scale(TEXT_FIELD_WIDTH), preferredSize.height)
      }
    }

    /**
     * Commits the current text in the input fields and applies the changes to the configuration.
     * This method is called when the user presses Enter or when the text fields lose focus.
     */
    private fun applyTextFieldChanges() {
      try {
        widthTextField.commitEdit()
        heightTextField.commitEdit()
      } catch (ex: ParseException) {
        // Ignore parse errors, the old value will be kept.
      }
      updateConfigurationFromTextFields()
    }

    private fun setUpTextFieldListeners() {
      val onEnterListener = ActionListener { applyTextFieldChanges() }
      widthTextField.addActionListener(onEnterListener)
      heightTextField.addActionListener(onEnterListener)

      val focusListener =
        object : FocusAdapter() {
          override fun focusLost(e: FocusEvent?) {
            applyTextFieldChanges()
          }
        }
      widthTextField.addFocusListener(focusListener)
      heightTextField.addFocusListener(focusListener)
    }

    private fun updateConfigurationFromTextFields() {
      val config = currentConfiguration ?: return
      val newWidthDp = widthTextField.value as? Int
      val newHeightDp = heightTextField.value as? Int

      if (newWidthDp != null && newHeightDp != null && newWidthDp > 0 && newHeightDp > 0) {
        val (currentConfigWidthDp, currentConfigHeightDp) = config.deviceSizeDp()
        if (newWidthDp == currentConfigWidthDp && newHeightDp == currentConfigHeightDp) {
          return
        }
        val dpi = config.density.dpiValue
        if (dpi <= 0) {
          log.warn("Cannot update screen size, invalid DPI: $dpi")
          return
        }
        config.updateScreenSize(
          ConversionUtil.dpToPx(newWidthDp, dpi),
          ConversionUtil.dpToPx(newHeightDp, dpi),
        )
        ComposeResizeToolingUsageTracker.logResizeStopped(
          currentSceneManager?.scene?.designSurface,
          currentSceneManager?.resizeMode ?: ResizeComposePreviewEvent.ResizeMode.COMPOSABLE_RESIZE,
          newWidthDp,
          newHeightDp,
          dpi,
          ResizeComposePreviewEvent.ResizeSource.TEXT_FIELD,
        )
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      // This action is just for showing the component, it doesn't have a direct action
    }
  }

  /** Creates and configures the close button. */
  private fun createCloseButton(): JComponent {
    val closeAction = CloseAction()
    val closeButton =
      ActionButton(
          closeAction,
          closeAction.templatePresentation.clone(),
          ActionPlaces.TOOLBAR,
          ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE,
        )
        .apply { isFocusable = true }

    // Wrap the close button in a panel to prevent it from being stretched by the BorderLayout.
    return JBPanel<Nothing>(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
      isOpaque = false
      add(closeButton)
    }
  }

  private inner class CloseAction :
    DumbAwareAction(message("resize.panel.hide.revert.tooltip"), null, AllIcons.Actions.Close) {
    override fun actionPerformed(e: AnActionEvent) {
      revertResizing()
      this@ResizePanel.isVisible = false
    }
  }
}
