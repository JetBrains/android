/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property2.support

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.adtui.LightCalloutPopup
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.property.panel.api.HelpSupport
import com.android.tools.idea.res.colorToString
import com.android.tools.idea.res.resolveColor
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog
import com.android.tools.idea.ui.resourcechooser.ColorResourcePicker
import com.android.tools.idea.ui.resourcechooser.ColorResourcePickerListener
import com.android.tools.idea.ui.resourcechooser.HorizontalTabbedPanelBuilder
import com.android.tools.idea.ui.resourcechooser.colorpicker2.ColorPickerBuilder
import com.android.tools.idea.ui.resourcechooser.colorpicker2.ColorPickerListener
import com.android.tools.idea.ui.resourcechooser.colorpicker2.internal.MaterialColorPaletteProvider
import com.android.tools.idea.ui.resourcechooser.colorpicker2.internal.MaterialGraphicalColorPipetteProvider
import com.android.tools.idea.ui.resourcecommon.ResourcePickerDialog
import com.android.tools.idea.ui.resourcemanager.ResourceExplorerDialog
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.PlatformDataKeys
import icons.StudioIcons
import java.awt.Color
import java.awt.Component
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import java.util.Locale
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.SwingUtilities

const val PICK_A_RESOURCE = "Pick a Resource"

/**
 * Resource actions in Nele.
 *
 * Note: this may change pending UX specifications.
 */
class ToggleShowResolvedValueAction(val model: NelePropertiesModel) : AnAction("Toggle Computed Value") {

  init {
    shortcutSet = CustomShortcutSet(SHORTCUT)
  }

  override fun actionPerformed(e: AnActionEvent) {
    model.showResolvedValues = !model.showResolvedValues
  }

  companion object {
    @JvmField
    val SHORTCUT = KeyboardShortcut(KeyStrokes.CMD_MINUS, null)
  }
}

object OpenResourceManagerAction : AnAction("Open Resource Manager", PICK_A_RESOURCE, StudioIcons.Common.PROPERTY_UNBOUND) {

  override fun actionPerformed(event: AnActionEvent) {
    val property = event.dataContext.getData(HelpSupport.PROPERTY_ITEM) as NelePropertyItem? ?: return
    val newValue = property.delegate?.let { selectFromResourceDialog(it) }
    if (newValue != null) {
      property.value = newValue
    }
    // The resource picker is a modal dialog.
    // Attempt to request focus back to the original Swing component where the event came from.
    // Without this, focus would go back to the table containing the editor.
    (event.inputEvent?.source as? JComponent)?.requestFocus()
  }

  private fun selectFromResourceDialog(property: NelePropertyItem): String? {
    val module = property.model.facet.module
    val propertyName = property.name
    val tag = property.components.firstOrNull()?.backend?.tag ?: return null
    val hasImageTag = property.components.stream().filter { component -> component.tagName == SdkConstants.IMAGE_VIEW }.findFirst()
    val defaultResourceType = getDefaultResourceType(propertyName)
    val isImageViewDrawable = hasImageTag.isPresent &&
                              (SdkConstants.ATTR_SRC_COMPAT == propertyName || SdkConstants.ATTR_SRC == propertyName)
    val showSampleData = SdkConstants.TOOLS_URI == property.namespace
    val dialog: ResourcePickerDialog =
      if (StudioFlags.RESOURCE_EXPLORER_PICKER.get())
        ResourceExplorerDialog(property.model.facet,
                               property.type.resourceTypes,
                               showSampleData,
                               tag.containingFile.virtualFile)
      else ChooseResourceDialog.builder()
        .setModule(module)
        .setTypes(property.type.resourceTypes)
        .setCurrentValue(property.rawValue)
        .setTag(tag)
        .setDefaultType(defaultResourceType)
        .setFilterColorStateLists(isImageViewDrawable)
        .setShowSampleDataPicker(showSampleData)
        .build()
    dialog.title = PICK_A_RESOURCE
    return if (dialog.showAndGet()) dialog.resourceName else null
  }

  /**
   * For some attributes, it make more sense the display a specific type by default.
   *
   * For example `textColor` has more chance to have a color value than a drawable value,
   * so in the [ChooseResourceDialog], we need to select the Color tab by default.
   *
   * @param propertyName The property name to get the associated default type from.
   * @return The [ResourceType] that should be selected by default for the provided property name.
   */
  private fun getDefaultResourceType(propertyName: String): ResourceType? {
    val lowerCaseProperty = propertyName.toLowerCase(Locale.getDefault())
    return when {
      lowerCaseProperty.contains("color") || lowerCaseProperty.contains("tint")
      -> ResourceType.COLOR
      lowerCaseProperty.contains("drawable") || propertyName == SdkConstants.ATTR_SRC || propertyName == SdkConstants.ATTR_SRC_COMPAT
      -> ResourceType.DRAWABLE
      else -> null
    }
  }
}

object ColorSelectionAction: AnAction("Select Color") {

  override fun actionPerformed(event: AnActionEvent) {
    val property = event.dataContext.getData(HelpSupport.PROPERTY_ITEM) as NelePropertyItem? ?: return

    val resourceReference = property.resolveValueAsReference(property.rawValue)
    val currentColor = if (resourceReference != null) {
      property.resolver?.resolveColor(property.resolver?.getResolvedResource(resourceReference), property.project)
    }
    else {
      property.resolveValueAsColor(property.rawValue)
    }
    val restoreFocusTo = componentToRestoreFocusTo(event)

    selectFromColorDialog(locationFromEvent(event), property, currentColor, resourceReference, restoreFocusTo)
  }

  private fun selectFromColorDialog(location: Point,
                                    property: NelePropertyItem,
                                    initialColor: Color?,
                                    resourceReference: ResourceReference?,
                                    restoreFocusTo: Component?) {
    val dialog = LightCalloutPopup()

    val colorPicker = ColorPickerBuilder()
      .setOriginalColor(initialColor)
      .addSaturationBrightnessComponent()
      .addColorAdjustPanel(MaterialGraphicalColorPipetteProvider())
      .addColorValuePanel().withFocus()
      .addSeparator()
      .addCustomComponent(MaterialColorPaletteProvider)
      .addColorPickerListener(ColorPickerListener { color, _ -> property.value = colorToString(color) })
      .focusWhenDisplay(true)
      .setFocusCycleRoot(true)
      .addKeyAction(KeyStrokes.ESCAPE, object : AbstractAction() {
        override fun actionPerformed(event: ActionEvent) {
          dialog.close()
          restoreFocus(restoreFocusTo)
        }
      })
      .build()

    val popupContent: JComponent
    val configuration = property.model.surface?.configuration
    if (StudioFlags.NELE_RESOURCE_POPUP_PICKER.get() && configuration != null) {
      // Use tabbed panel instead.
      val resourcePicker = ColorResourcePicker(configuration, resourceReference)
      resourcePicker.addColorResourcePickerListener (object : ColorResourcePickerListener {
        override fun colorResourcePicked(resourceReference: ResourceReference) {
          // TODO: Use relative resource url instead.
          property.value = resourceReference.resourceUrl.toString()
        }
      })
      popupContent = HorizontalTabbedPanelBuilder()
        .addTab("Resources", resourcePicker)
        .addTab("Custom", colorPicker)
        .setDefaultPage(if (resourceReference != null) 0 else 1)
        .build()
    }
    else {
      popupContent = colorPicker
    }

    dialog.show(popupContent, null, location)
  }

  private fun locationFromEvent(event: AnActionEvent): Point {
    val source = componentFromEvent(event)
    if (source is Component) {
      val location = source.locationOnScreen
      return Point(location.x + source.width / 2, location.y + source.height / 2)
    }
    val input = event.inputEvent
    if (input is MouseEvent) {
      return input.locationOnScreen
    }
    return Point(20, 20)
  }

  private fun componentFromEvent(event: AnActionEvent): Component? {
    return PlatformDataKeys.CONTEXT_COMPONENT.getData(event.dataContext) ?: event.inputEvent?.component
  }

  private fun componentToRestoreFocusTo(event: AnActionEvent): Component? {
    val component = componentFromEvent(event) ?: return null
    val table = SwingUtilities.getAncestorOfClass(JTable::class.java, component)
    return table ?: component
  }

  private fun restoreFocus(restoreFocusTo: Component?) {
    if (restoreFocusTo is JTable && restoreFocusTo.selectedRow > 0 && restoreFocusTo.selectedColumn > 0) {
      restoreFocusTo.editCellAt(restoreFocusTo.selectedRow, restoreFocusTo.selectedColumn)
      restoreFocusTo.editorComponent.requestFocus()
    }
    else {
      restoreFocusTo?.requestFocus()
    }
  }
}
