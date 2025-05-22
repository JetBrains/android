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
package com.android.tools.idea.ui.resourcechooser.util

import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.resources.ResourceResolver
import com.android.resources.ResourceType
import com.android.tools.adtui.LightCalloutPopup
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.configurations.virtualFile
import com.android.tools.idea.ui.resourcechooser.CompactResourcePicker
import com.android.tools.idea.ui.resourcechooser.HorizontalTabbedPanelBuilder
import com.android.tools.adtui.MaterialColorPaletteProvider
import com.android.tools.idea.ui.resourcechooser.common.ResourcePickerSources
import com.android.tools.idea.ui.resourcemanager.ResourcePickerDialog
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.colorpicker.ColorPickerBuilder
import com.intellij.ui.colorpicker.MaterialGraphicalColorPipetteProvider
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Color
import java.awt.Component
import java.awt.MouseInfo
import java.awt.Point
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JTable

/**
 * Returns a [ResourcePickerDialog], may list sample data, project, library, android and theme attributes resources for the given
 * [resourceTypes].
 *
 * Selecting a resource in the ResourcePicker will populate [ResourcePickerDialog.resourceName] in the following format:
 * '@string/my_string' or '?attr/my_attribute' for theme attributes, the resource name will include the appropriate namespace.
 *
 * @param dialogTitle For the DialogWrapper
 * @param currentValue The current/initial resource reference value E.g: '@string/my_string'
 * @param facet The current [AndroidFacet]
 * @param resourceTypes Supported or expected [ResourceType]s in the ResourcePicker
 * @param defaultResourceType Preferred [ResourceType] when there are multiple [ResourceType]s supported
 * @param showColorStateLists If true, include state lists in Color resources
 * @param showSampleData If true, include SampleData
 * @param file The context file with a Configuration, used for theme attributes
 */
fun createResourcePickerDialog(
  @NlsContexts.DialogTitle dialogTitle: String,
  currentValue: String?,
  facet: AndroidFacet,
  resourceTypes: Set<ResourceType>,
  defaultResourceType: ResourceType?,
  showColorStateLists: Boolean,
  showSampleData: Boolean,
  showThemeAttributes: Boolean,
  file: VirtualFile?
): ResourcePickerDialog {
  // TODO(139313381): Implement showColorStateLists
  return ResourcePickerDialog(
    facet = facet,
    initialResourceUrl = currentValue,
    supportedTypes = resourceTypes,
    preferredType = defaultResourceType,
    showSampleData = showSampleData,
    showThemeAttributes = showThemeAttributes,
    currentFile = file
  ).apply { title = dialogTitle }
}

/**
 * Creates and shows a popup dialog with the ColorPicker and if possible, the popup will have an additional tab with a ResourcePicker for
 * colors.
 *
 * @param initialColor The initial color for the ColorPicker panel
 * @param initialColorResource The initial resource reference for the ResourcePicker, when not null, the popup dialog will open in the
 * ResourcePicker tab
 * @param configuration The [Configuration] of the current file, required to have a ResourcePicker in the popup dialog
 * @param resourcePickerSources List of the different places the color resources will be pulled from, empty list will default to all sources
 * @param restoreFocusComponent When closing the popup dialog, this component will regain focus
 * @param locationToShow Preferred location in the screen to show the popup dialog, if null, the current location of the mouse will be used
 * @param colorPickedCallback The callback for whenever a new [Color] is picked in the ColorPicker
 * @param colorResourcePickedCallback The callback for whenever a new Color resource is picked in the ResourcePicker, returns the string
 * representation of the resource Eg: @color/colorPrimary
 */
fun createAndShowColorPickerPopup(
  initialColor: Color?,
  initialColorResource: ResourceValue?,
  configuration: Configuration?,
  resourcePickerSources: List<ResourcePickerSources>,
  restoreFocusComponent: Component?,
  locationToShow: Point?,
  colorPickedCallback: ((Color) -> Unit)?,
  colorResourcePickedCallback: ((String) -> Unit)?
) {

  val facet = configuration?.let { AndroidFacet.getInstance(ConfigurationManager.getFromConfiguration(configuration).module) }
  val file = configuration?.virtualFile
  val resourceResolver = configuration?.resourceResolver

  createAndShowColorPickerPopup(initialColor, initialColorResource, facet, file, resourceResolver, resourcePickerSources,
                                restoreFocusComponent, locationToShow, colorPickedCallback, colorResourcePickedCallback)
}

/**
 * Creates and shows a popup dialog with the ColorPicker and if possible, the popup will have an additional tab with a ResourcePicker for
 * colors.
 *
 * @param initialColor The initial color for the ColorPicker panel
 * @param initialColorResource The initial resource reference for the ResourcePicker, when not null, the popup dialog will open in the
 * ResourcePicker tab
 * @param facet The [AndroidFacet] of current module
 * @param contextFile The [VirtualFile] to provide the context.
 * @param resourceResolver The [ResourceResolver] to read and parse the resource color
 * @param resourcePickerSources List of the different places the color resources will be pulled from, empty list will default to all sources
 * @param restoreFocusComponent When closing the popup dialog, this component will regain focus
 * @param locationToShow Preferred location in the screen to show the popup dialog, if null, the current location of the mouse will be used
 * @param colorPickedCallback The callback for whenever a new [Color] is picked in the ColorPicker
 * @param colorResourcePickedCallback The callback for whenever a new Color resource is picked in the ResourcePicker, returns the string
 * representation of the resource Eg: @color/colorPrimary
 */
fun createAndShowColorPickerPopup(
  initialColor: Color?,
  initialColorResource: ResourceValue?,
  facet: AndroidFacet?,
  contextFile: VirtualFile?,
  resourceResolver: ResourceResolver?,
  resourcePickerSources: List<ResourcePickerSources>,
  restoreFocusComponent: Component?,
  locationToShow: Point?,
  colorPickedCallback: ((Color) -> Unit)?,
  colorResourcePickedCallback: ((String) -> Unit)?
) {
  val disposable = Disposer.newDisposable("ResourcePickerPopup")
  val onPopupClosed = {
    Disposer.dispose(disposable)
  }
  val popupDialog = LightCalloutPopup(onPopupClosed, onPopupClosed, null)

  val colorPicker = if (colorPickedCallback == null) null else {
    ColorPickerBuilder(showAlpha = true, showAlphaAsPercent = false)
    .setOriginalColor(initialColor)
    .addSaturationBrightnessComponent()
    .addColorAdjustPanel(MaterialGraphicalColorPipetteProvider())
    .addColorValuePanel().withFocus()
    .addSeparator()
    .addCustomComponent(MaterialColorPaletteProvider)
    .addColorListener { color, _ -> colorPickedCallback(color) }
    .focusWhenDisplay(true)
    .setFocusCycleRoot(true)
    .addKeyAction(KeyStrokes.ESCAPE, object : AbstractAction() {
      override fun actionPerformed(event: ActionEvent) {
        popupDialog.close()
        restoreFocusComponent?.let(::restoreFocus)
      }
    })
    .build()
  }

  val resourcePicker = if (facet != null && colorPickedCallback != null && contextFile != null &&
                           resourceResolver != null && colorResourcePickedCallback != null) {
    CompactResourcePicker(
      facet,
      contextFile,
      resourceResolver,
      ResourceType.COLOR,
      resourcePickerSources,
      colorResourcePickedCallback,
      popupDialog::close,
      disposable
    )
  }
  else null

  if (colorPicker == null && resourcePicker == null) {
    return
  }

  val popupContent = if (colorPicker != null && resourcePicker != null) {
    // TODO: Use relative resource url instead.
    HorizontalTabbedPanelBuilder() // Use tabbed panel instead.
      .addTab("Resources", resourcePicker)
      .addTab("Custom", colorPicker.content)
      .setDefaultPage(if (initialColorResource != null) 0 else 1)
      .addKeyAction(KeyStrokes.ESCAPE, object : AbstractAction() {
        override fun actionPerformed(event: ActionEvent) {
          popupDialog.close()
          restoreFocusComponent?.let(::restoreFocus)
        }
      })
      .build()
  }
  else colorPicker?.content ?: resourcePicker!!

  popupDialog.show(popupContent, null, locationToShow ?: MouseInfo.getPointerInfo().location)
}

/**
 * Shows a popup with the resource picker.
 *
 * Contains different lists for local, libraries, framework and theme attributes resources.
 *
 * @param resourceType [ResourceType] to pick from
 * @param configuration The [Configuration] of the current file, required to have a ResourcePicker in the popup dialog
 * @param facet [AndroidFacet] from which local and library android resources are obtained
 * @param locationToShow Preferred location in the screen to show the popup dialog, if null, the current location of the mouse will be used
 * @param resourcePickedCallback The callback for whenever a resource is selected in the ResourcePicker, returns the string
 * reference of the resource Eg: @color/colorPrimary
 */
fun createAndShowResourcePickerPopup(
  resourceType: ResourceType,
  configuration: Configuration,
  facet: AndroidFacet,
  resourcePickerSources: List<ResourcePickerSources>,
  locationToShow: Point?,
  resourcePickedCallback: (String) -> Unit
) {
  val disposable = Disposer.newDisposable("ResourcePickerPopup")
  val onPopupClosed = {
    Disposer.dispose(disposable)
  }
  val popupDialog = LightCalloutPopup(onPopupClosed, onPopupClosed, null)
  val resourcePicker = CompactResourcePicker(
    facet,
    configuration.virtualFile,
    configuration.resourceResolver,
    resourceType,
    resourcePickerSources,
    resourcePickedCallback,
    popupDialog::close,
    disposable
  )
  popupDialog.show(resourcePicker, null, locationToShow ?: MouseInfo.getPointerInfo().location)
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