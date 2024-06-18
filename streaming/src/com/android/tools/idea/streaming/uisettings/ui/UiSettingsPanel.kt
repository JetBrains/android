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
package com.android.tools.idea.streaming.uisettings.ui

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.uisettings.binding.ReadOnlyProperty
import com.android.tools.idea.streaming.uisettings.binding.TwoWayProperty
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.JSlider
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.ListCellRenderer
import javax.swing.plaf.UIResource

private const val TITLE = "Device Settings Shortcuts"
internal const val DARK_THEME_TITLE = "Dark Theme:"
internal const val GESTURE_NAVIGATION_TITLE = "Gesture Navigation:"
internal const val APP_LANGUAGE_TITLE = "App Language:"
internal const val TALKBACK_TITLE = "TalkBack:"
internal const val SELECT_TO_SPEAK_TITLE = "Select to Speak:"
internal const val FONT_SCALE_TITLE = "Font Size:"
internal const val DENSITY_TITLE = "Display Size:"
internal const val DEBUG_LAYOUT_TITLE = "Debug Layout:"
internal const val RESET_TITLE = "Reset"
internal const val PERMISSION_HINT_LINE1 = "More options may be available if \"Disable permission monitoring\" is turned on in"
internal const val PERMISSION_HINT_LINE2 = "\"Developer Options\" and the device is restarted."

/**
 * Custom horizontal spacing between labels and controls.
 */
private val SPACING = object : IntelliJSpacingConfiguration() {
  override val horizontalSmallGap = 50
}

/**
 * Displays a picker with setting shortcuts.
 *
 * @param model the UI settings model
 * @param deviceType some controls are only available for certain device types
 */
internal class UiSettingsPanel(
  private val model: UiSettingsModel,
  deviceType: DeviceType
) : BorderLayoutPanel()  {
  init {
    add(panel {
      customizeSpacingConfiguration(SPACING) {
        row(title(TITLE)) {
          link(RESET_TITLE) { model.resetAction() }
            .accessibleName(RESET_TITLE)
            .apply { component.name = RESET_TITLE }
            .visibleIf(model.differentFromDefault)
            .align(AlignX.RIGHT)
        }
        separator()

        if (deviceType != DeviceType.WEAR) {
          row(JBLabel(DARK_THEME_TITLE)) {
            checkBox("")
              .accessibleName(DARK_THEME_TITLE)
              .bind(model.inDarkMode)
              .apply { component.name = DARK_THEME_TITLE }
          }
        }

        if (deviceType == DeviceType.HANDHELD) {
          row(JBLabel(GESTURE_NAVIGATION_TITLE)) {
            comboBox(model.navigationModel)
              .accessibleName(GESTURE_NAVIGATION_TITLE)
              .bindItem(model.navigationModel.selection)
              .apply {
                component.name = GESTURE_NAVIGATION_TITLE
                component.renderer = ListCellRenderer { _, value, _, _, _ ->
                  JBLabel(if (value == true) "Gesture navigation" else "3 Button navigation")
                }
              }
          }.visibleIf(model.permissionMonitoringDisabled.and(model.gestureOverlayInstalled))
        }

        row(JBLabel(APP_LANGUAGE_TITLE)) {
          comboBox(model.appLanguage)
            .accessibleName(APP_LANGUAGE_TITLE)
            .bindItem(model.appLanguage.selection)
            .apply { component.name = APP_LANGUAGE_TITLE }
            .align(AlignX.FILL)
        }.visibleIf(model.appLanguage.sizeIsAtLeast(2))

        row(JBLabel(TALKBACK_TITLE)) {
          checkBox("")
            .accessibleName(TALKBACK_TITLE)
            .bind(model.talkBackOn)
            .apply { component.name = TALKBACK_TITLE }
        }.visibleIf(model.talkBackInstalled.and(model.permissionMonitoringDisabled))

        if (deviceType == DeviceType.HANDHELD) {
          row(JBLabel(SELECT_TO_SPEAK_TITLE)) {
            checkBox("")
              .accessibleName(SELECT_TO_SPEAK_TITLE)
              .bind(model.selectToSpeakOn)
              .apply { component.name = SELECT_TO_SPEAK_TITLE }
          }.visibleIf(model.talkBackInstalled.and(model.permissionMonitoringDisabled))
        }

        row(JBLabel(FONT_SCALE_TITLE)) {
          slider(0, model.fontScaleMaxIndex.value, 1, 1)
            .accessibleName(FONT_SCALE_TITLE)
            .noLabels()
            .bindSliderPosition(model.fontScaleIndex)
            .bindSliderMaximum(model.fontScaleMaxIndex)
            .apply { component.name = FONT_SCALE_TITLE }
        }.visibleIf(model.permissionMonitoringDisabled)

        if (deviceType == DeviceType.HANDHELD) {
          row(JBLabel(DENSITY_TITLE)) {
            slider(0, model.screenDensityIndex.value, 1, 1)
              .accessibleName(DENSITY_TITLE)
              .noLabels()
              .bindSliderPosition(model.screenDensityIndex)
              .bindSliderMaximum(model.screenDensityMaxIndex)
              .apply { component.name = DENSITY_TITLE }
          }.visibleIf(model.permissionMonitoringDisabled)
        }

        if (StudioFlags.EMBEDDED_EMULATOR_DEBUG_LAYOUT_IN_UI_SETTINGS.get()) {
          row(JBLabel(DEBUG_LAYOUT_TITLE)) {
            checkBox("")
              .accessibleName(DEBUG_LAYOUT_TITLE)
              .bind(model.debugLayout)
              .apply { component.name = DEBUG_LAYOUT_TITLE }
          }
        }

        row {
          cell(BorderLayoutPanel().apply {
            addToTop(JBLabel(PERMISSION_HINT_LINE1, UIUtil.ComponentStyle.MINI))
            addToBottom(JBLabel(PERMISSION_HINT_LINE2, UIUtil.ComponentStyle.MINI))
          })
        }.visibleIf(model.permissionMonitoringDisabled.not())
      }
    })
    updateBackground()

    isFocusCycleRoot = true
    isFocusTraversalPolicyProvider = true
    focusTraversalPolicy = LayoutFocusTraversalPolicy()
  }

  /**
   * Create a label for the title of the panel.
   */
  private fun title(title: String): JBLabel =
    JBLabel(title).apply { foreground = UIUtil.getInactiveTextColor() }

  /**
   * Bind a [Boolean] property to an [AbstractButton] cell.
   */
  private fun <T : AbstractButton> Cell<T>.bind(predicate: TwoWayProperty<Boolean>): Cell<T> {
    predicate.addControllerListener { selected -> component.isSelected = selected }
    component.isSelected = predicate.value
    return actionListener { _, c -> predicate.setFromUi(c.isSelected) }
  }

  private fun <T> Cell<ComboBox<T>>.bindItem(property: TwoWayProperty<T>): Cell<ComboBox<T>> {
    property.addControllerListener { selected -> component.selectedItem = selected }
    component.selectedItem = property.value
    component.addActionListener { property.setFromUi(component.selectedItem as T) }
    return this
  }

  private fun Cell<JSlider>.noLabels(): Cell<JSlider> {
    component.paintLabels = false
    return this
  }

  private fun Cell<JSlider>.bindSliderPosition(property: TwoWayProperty<Int>): Cell<JSlider> {
    property.addControllerListener { component.value = it }
    component.value = property.value
    component.addChangeListener { if (!component.valueIsAdjusting) property.setFromUi(component.value) }
    return this
  }

  private fun Cell<JSlider>.bindSliderMaximum(property: ReadOnlyProperty<Int>): Cell<JSlider> {
    property.addControllerListener { component.maximum = it }
    component.maximum = property.value
    return this
  }

  private fun Row.visibleIf(predicate: ReadOnlyProperty<Boolean>): Row {
    visible(predicate.value)
    predicate.addControllerListener { visible(it) }
    return this
  }

  private fun <T : JComponent> Cell<T>.visibleIf(predicate: ReadOnlyProperty<Boolean>): Cell<T> {
    visible(predicate.value)
    predicate.addControllerListener { visible(it) }
    return this
  }

  /**
   * Use the lighter background color rather than the Swing default.
   */
  private fun updateBackground() {
    AdtUiUtils.allComponents(this).forEach {
      if (it.background is UIResource) {
        it.background = secondaryPanelBackground
      }
    }
  }
}
