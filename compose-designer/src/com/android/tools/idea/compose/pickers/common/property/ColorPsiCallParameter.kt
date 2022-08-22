/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.pickers.common.property

import com.android.tools.adtui.actions.componentToRestoreFocusTo
import com.android.tools.adtui.actions.locationFromEvent
import com.android.tools.idea.compose.pickers.base.model.PsiCallPropertiesModel
import com.android.tools.idea.compose.pickers.base.property.PsiCallParameterPropertyItem
import com.android.tools.idea.compose.pickers.common.editingsupport.ColorValidation
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.res.colorToStringWithAlpha
import com.android.tools.idea.res.parseColor
import com.android.tools.idea.ui.resourcechooser.util.createAndShowColorPickerPopup
import com.android.tools.property.panel.api.ActionIconButton
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import icons.StudioIcons
import java.awt.Color
import javax.swing.Icon
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

/**
 * A [PsiCallParameterPropertyItem] for Color parameters.
 *
 * Adds the Color picker to the property item and displays color values (Long values) in the proper
 * color format '0xAARRGGBB'.
 */
internal class ColorPsiCallParameter(
  project: Project,
  model: PsiCallPropertiesModel,
  resolvedCall: ResolvedCall<*>,
  descriptor: ValueParameterDescriptor,
  argumentExpression: KtExpression?,
  initialValue: String?
) :
  PsiCallParameterPropertyItem(
    project,
    model,
    resolvedCall,
    descriptor,
    argumentExpression,
    initialValue,
    ColorValidation
  ) {

  override val colorButton =
    object : ActionIconButton {
      override val actionButtonFocusable: Boolean = true

      override val actionIcon: Icon?
        get() = StudioIcons.LayoutEditor.Extras.PIPETTE

      override val action =
        object : AnAction(message("picker.preview.color.action.title")) {
          override fun actionPerformed(e: AnActionEvent) {
            createAndShowColorPickerPopup(
              initialColor = value?.substringAfter("0x")?.let { parseColor("#$it") },
              initialColorResource = null,
              configuration = null,
              resourcePickerSources = listOf(),
              restoreFocusComponent = e.componentToRestoreFocusTo(),
              locationToShow = e.locationFromEvent(),
              colorPickedCallback = { value = colorToStringWithAlpha(it) },
              colorResourcePickedCallback = {
                // Do nothing.
              }
            )
          }
        }
    }

  override var value: String?
    get() {
      val valueString = super.value
      val colorValue = valueString?.toLongOrNull() ?: return valueString
      return colorToStringWithAlpha(
        Color(
          (colorValue shr 16 and 0xFF).toInt(),
          (colorValue shr 8 and 0xFF).toInt(),
          (colorValue and 0xFF).toInt(),
          (colorValue shr 24 and 0xFF).toInt()
        )
      )
    }
    set(newValue) {
      super.value = newValue
    }
}
