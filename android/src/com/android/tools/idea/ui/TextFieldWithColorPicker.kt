/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui

import com.android.tools.adtui.FocusableIcon
import com.android.tools.adtui.TextFieldWithLeftComponent
import com.android.ide.common.resources.colorToString
import com.android.ide.common.resources.parseColor
import com.android.tools.adtui.LightCalloutPopup
import com.android.tools.adtui.MaterialColorPaletteProvider
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.colorpicker.ColorPickerBuilder
import com.intellij.ui.colorpicker.MaterialGraphicalColorPipetteProvider
import com.intellij.ui.colorpicker.canShowBelow
import com.intellij.ui.picker.ColorListener
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

private const val ICON_SIZE = 15

/**
 * Returns the given [JTextField] in a [JPanel] right next to a [ColorIcon]. The [ColorIcon] is a clickable Icon that opens up a
 * ColorPicker. Whatever value color is selected in it will be reflected into the given [JTextField], similarly, any valid color manually
 * typed into the [JTextField] will be reflected on the Icon.
 *
 * The appearance of the returned [JPanel] is basically the same of a regular [JTextField], so the background and border of the actual
 * [JTextField] are not painted.
 */
fun JTextField.wrapWithColorPickerIcon(defaultColor: Color?): JPanel {
  val initialColor = defaultColor ?: MaterialColors.RED_500
  val colorPickerIcon = FocusableIcon()
  val textFieldWithIcon = TextFieldWithLeftComponent(colorPickerIcon, this)

  if (defaultColor == null) {
    // The Pipette Icon is the default Icon when there's no valid color.
    colorPickerIcon.icon = StudioIcons.LayoutEditor.Extras.PIPETTE
  }

  // Listen for whenever a valid color is typed in to the TextField an update the Icon appropriately.
  this.document.addDocumentListener(object : DocumentAdapter() {
    override fun textChanged(e: DocumentEvent) {
      val colorText = this@wrapWithColorPickerIcon.text
      if (colorText.isNotEmpty() && colorText.startsWith("#") && colorText.length >= 7) {
        // Supports color values in the format '#rrggbb' and '#aarrggbb'.
        val colorTyped = parseColor(colorText)
        if (colorTyped != null) {
          colorPickerIcon.icon = ColorIcon(JBUI.scale(ICON_SIZE), colorTyped)
          return
        }
      }
      colorPickerIcon.icon = StudioIcons.LayoutEditor.Extras.PIPETTE
    }
  })

  // The callback whenever the Icon gets clicked. Bring up the color picker and update the Icon and TextField.
  val colorPickerIconCallback: () -> Unit = {
    val popup = LightCalloutPopup()
    val colorPicker = baseColorPickerBuilder(initialColor)
      .addOperationPanel(
        { color ->
          // 'Ok' option callback.
          text = colorToString(color)
          colorPickerIcon.icon = ColorIcon(JBUI.scale(ICON_SIZE), color)
          popup.close()
        },
        {
          // 'Cancel' option callback.
          popup.close()
        })
      .build().content as JPanel
    val relativeLocation = JBPopupFactory.getInstance().guessBestPopupLocation(colorPickerIcon)
    if (canShowBelow(textFieldWithIcon.parent as JComponent, relativeLocation.point, colorPicker)) {
      popup.show(colorPicker, null, relativeLocation.screenPoint)
    }
    else {
      // Can't display ColorPicker in the popup, fallback to using a dialog.
      var selectedColor = initialColor
      val colorPickerListener = ColorListener { color, _ ->
        // For the dialog, listen to whenever the color is updated.
        selectedColor = color
      }
      val colorPickerDialog = baseColorPickerBuilder(initialColor)
        .addColorListener(colorPickerListener)
        .build().content as JPanel
      colorPickerDialog.minimumSize = colorPickerDialog.preferredSize
      val builder = DialogBuilder()
      builder.setTitle("Pick a Color")
      builder.setCenterPanel(colorPickerDialog)
      builder.setOkOperation {
        // Apply last selected color when clicking 'Ok' in the dialog.
        this.text = colorToString(selectedColor)
        colorPickerIcon.icon = ColorIcon(JBUI.scale(ICON_SIZE), selectedColor)
        builder.dialogWrapper.close(DialogWrapper.OK_EXIT_CODE)
      }
      builder.showAndGet()
    }
  }
  // Set color picker callback to when the Icon is clicked.
  colorPickerIcon.onClick = colorPickerIconCallback
  return textFieldWithIcon
}

private fun baseColorPickerBuilder(originalColor: Color): ColorPickerBuilder =
  ColorPickerBuilder(showAlpha = true, showAlphaAsPercent = false)
    .setOriginalColor(originalColor)
    .addSaturationBrightnessComponent()
    .addColorAdjustPanel(MaterialGraphicalColorPipetteProvider())
    .addColorValuePanel().withFocus()
    .addSeparator()
    .addCustomComponent(MaterialColorPaletteProvider)
    .focusWhenDisplay(true)
    .setFocusCycleRoot(true)
