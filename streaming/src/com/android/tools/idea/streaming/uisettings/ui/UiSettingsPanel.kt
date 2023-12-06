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

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.streaming.uisettings.binding.ReadOnlyProperty
import com.android.tools.idea.streaming.uisettings.binding.TwoWayProperty
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.UIUtil
import java.awt.Font
import javax.swing.AbstractButton
import javax.swing.JSlider
import javax.swing.plaf.UIResource

private const val TITLE = "Device Settings Shortcuts"
internal const val DARK_THEME_TITLE = "Dark Theme:"
internal const val FONT_SIZE_TITLE = "Font Size:"

/**
 * Custom horizontal spacing between labels and controls.
 */
private val SPACING = object : IntelliJSpacingConfiguration() {
  override val horizontalSmallGap = 50
}

/**
 * Displays a picker with setting shortcuts.
 */
internal class UiSettingsPanel(
  private val model: UiSettingsModel,
  parentDisposable: Disposable
) : BorderLayoutPanel()  {
  init {
    add(panel {
      customizeSpacingConfiguration(SPACING) {
        row(title(TITLE)) {}
        separator()
        row(label(DARK_THEME_TITLE)) {
          checkBox("")
            .bind(model.inDarkMode, parentDisposable)
            .apply { component.name = DARK_THEME_TITLE }
        }
        row(label(FONT_SIZE_TITLE)) {
          slider(0, model.fontSizeMaxIndex.value, 1, 1)
            .noLabels()
            .bindSliderPosition(model.fontSizeIndex, parentDisposable)
            .bindSliderMaximum(model.fontSizeMaxIndex, parentDisposable)
            .apply { component.name = FONT_SIZE_TITLE }
        }
      }
    })
    updateBackground()
  }

  fun createPicker(): Balloon {
    return JBPopupFactory.getInstance()
      .createBalloonBuilder(this)
      .setShadow(true)
      .setHideOnAction(false)
      .setBlockClicksThroughBalloon(true)
      .setAnimationCycle(200)
      .setFillColor(secondaryPanelBackground)
      .createBalloon()
  }

  /**
   * Create a label for the title of the panel.
   */
  private fun title(title: String): JBLabel =
    JBLabel(title).apply { foreground = UIUtil.getInactiveTextColor() }

  /**
   * Create a bold label.
   */
  private fun label(labelText: String): JBLabel =
    JBLabel(labelText).apply { font = font.deriveFont(Font.BOLD) }

  /**
   * Bind a [Boolean] property to an [AbstractButton] cell.
   */
  private fun <T : AbstractButton> Cell<T>.bind(predicate: TwoWayProperty<Boolean>, disposable: Disposable): Cell<T> {
    predicate.addControllerListener(disposable) { selected -> component.isSelected = selected }
    component.isSelected = predicate.value
    return actionListener { _, c -> predicate.setFromUi(c.isSelected) }
  }

  private fun Cell<JSlider>.noLabels(): Cell<JSlider> = apply {
    component.paintLabels = false
  }

  private fun Cell<JSlider>.bindSliderPosition(property: TwoWayProperty<Int>, disposable: Disposable): Cell<JSlider> = apply {
    property.addControllerListener(disposable) { component.value = it }
    component.value = property.value
    component.addChangeListener { if (!component.valueIsAdjusting) property.setFromUi(component.value) }
  }

  private fun Cell<JSlider>.bindSliderMaximum(property: ReadOnlyProperty<Int>, disposable: Disposable): Cell<JSlider> = apply {
    property.addControllerListener(disposable) { component.maximum = it }
    component.maximum = property.value
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
