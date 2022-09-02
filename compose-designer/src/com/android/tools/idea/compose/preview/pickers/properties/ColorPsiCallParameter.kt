package com.android.tools.idea.compose.preview.pickers.properties

import com.android.tools.adtui.actions.componentToRestoreFocusTo
import com.android.tools.adtui.actions.locationFromEvent
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.pickers.properties.editingsupport.ColorValidation
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
 * A [PsiPropertyItem] for Color parameters.
 *
 * Adds the Color picker to the property item and displays color values (Long values) in the proper
 * color format '0xAARRGGBB'.
 */
internal class ColorPsiCallParameter(
  project: Project,
  model: PsiCallPropertyModel,
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
