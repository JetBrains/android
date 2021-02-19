package com.android.tools.idea.compose.preview.pickers.properties

import com.android.tools.adtui.actions.componentToRestoreFocusTo
import com.android.tools.adtui.actions.locationFromEvent
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.res.colorToStringWithAlpha
import com.android.tools.idea.res.parseColor
import com.android.tools.idea.ui.resourcechooser.util.createAndShowColorPickerPopup
import com.android.tools.property.panel.api.ActionIconButton
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import icons.StudioIcons
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.inspections.AbstractPrimitiveRangeToInspection.Companion.constantValueOrNull
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.constants.LongValue
import java.awt.Color
import javax.swing.Icon

/**
 * A [PsiPropertyItem] for Color parameters.
 *
 * Adds the Color picker to the property item and displays color values (Long values) in the proper color format '0xAARRGGBB'.
 */
internal class ColorPsiCallParameter(project: Project,
                                     model: PsiCallPropertyModel,
                                     resolvedCall: ResolvedCall<*>,
                                     descriptor: ValueParameterDescriptor,
                                     argumentExpression: KtExpression?,
                                     initialValue: String?) : PsiCallParameterPropertyItem(project,
                                                                                           model,
                                                                                           resolvedCall,
                                                                                           descriptor,
                                                                                           argumentExpression,
                                                                                           initialValue) {

  override val colorButton: ActionIconButton? = object : ActionIconButton {
    override val actionButtonFocusable: Boolean = true

    override val actionIcon: Icon?
      get() = StudioIcons.LayoutEditor.Extras.PIPETTE

    override val action = object : AnAction(message("picker.preview.color.action.title")) {
      override fun actionPerformed(e: AnActionEvent) {
        createAndShowColorPickerPopup(
          value?.substringAfter("0x")?.let { parseColor("#$it") },
          null,
          null,
          listOf(),
          e.componentToRestoreFocusTo(),
          e.locationFromEvent(),
          {
            value = colorToStringWithAlpha(it)
          },
          {
            // Do nothing.
          }
        )
      }
    }
  }

  override var value: String?
    get() {
      argumentExpression?.constantValueOrNull(argumentExpression?.analyze())?.let { constant ->
        require(constant is LongValue)
        return colorToStringWithAlpha(
          Color((constant.value shr 16 and 0xFF).toInt(),
                (constant.value shr 8 and 0xFF).toInt(),
                (constant.value and 0xFF).toInt(),
                (constant.value shr 24 and 0xFF).toInt()))
      }
      return super.value
    }
    set(newValue) {
      super.value = newValue
    }
}