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
package com.android.tools.idea.compose.pickers.preview.inspector

import com.android.tools.idea.compose.pickers.base.enumsupport.EnumSupportValuesProvider
import com.android.tools.idea.compose.pickers.base.inspector.PsiPropertiesInspectorBuilder
import com.android.tools.idea.compose.pickers.base.property.PsiPropertyItem
import com.android.tools.idea.compose.pickers.common.enumsupport.PsiEnumProvider
import com.android.tools.idea.compose.pickers.common.inspector.PsiEditorProvider
import com.android.tools.idea.compose.pickers.common.inspector.PsiPropertyItemControlTypeProvider
import com.android.tools.preview.config.PARAMETER_API_LEVEL
import com.android.tools.preview.config.PARAMETER_BACKGROUND_COLOR
import com.android.tools.preview.config.PARAMETER_DEVICE
import com.android.tools.preview.config.PARAMETER_FONT_SCALE
import com.android.tools.preview.config.PARAMETER_GROUP
import com.android.tools.preview.config.PARAMETER_HARDWARE_CHIN_SIZE
import com.android.tools.preview.config.PARAMETER_HARDWARE_CUTOUT
import com.android.tools.preview.config.PARAMETER_HARDWARE_DENSITY
import com.android.tools.preview.config.PARAMETER_HARDWARE_DEVICE
import com.android.tools.preview.config.PARAMETER_HARDWARE_DIM_UNIT
import com.android.tools.preview.config.PARAMETER_HARDWARE_HEIGHT
import com.android.tools.preview.config.PARAMETER_HARDWARE_IS_ROUND
import com.android.tools.preview.config.PARAMETER_HARDWARE_NAVIGATION
import com.android.tools.preview.config.PARAMETER_HARDWARE_ORIENTATION
import com.android.tools.preview.config.PARAMETER_HARDWARE_WIDTH
import com.android.tools.preview.config.PARAMETER_HEIGHT_DP
import com.android.tools.preview.config.PARAMETER_LOCALE
import com.android.tools.preview.config.PARAMETER_NAME
import com.android.tools.preview.config.PARAMETER_SHOW_BACKGROUND
import com.android.tools.preview.config.PARAMETER_SHOW_DECORATION
import com.android.tools.preview.config.PARAMETER_SHOW_SYSTEM_UI
import com.android.tools.preview.config.PARAMETER_UI_MODE
import com.android.tools.preview.config.PARAMETER_WALLPAPER
import com.android.tools.preview.config.PARAMETER_WIDTH_DP
import com.android.tools.property.panel.api.ControlType
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable

/**
 * [PsiPropertiesInspectorBuilder] for the Preview annotation.
 *
 * Creates custom UI to organize the parameters and to organize the internal `Device` parameters.
 */
internal class PreviewPropertiesInspectorBuilder(
  enumSupportValuesProvider: EnumSupportValuesProvider
) : PsiPropertiesInspectorBuilder() {
  override val editorProvider: EditorProvider<PsiPropertyItem> =
    PsiEditorProvider(PsiEnumProvider(enumSupportValuesProvider), PreviewControlTypeProvider)

  override fun attachToInspector(
    inspector: InspectorPanel,
    properties: PropertiesTable<PsiPropertyItem>,
  ) {
    val allProps = properties.values.associateBy { it.name }.toMutableMap()
    val previewProperties =
      mutableListOf<PsiPropertyItem>().apply {
        allProps.remove(PARAMETER_NAME)?.let { add(it) }
        allProps.remove(PARAMETER_GROUP)?.let { add(it) }
      }

    allProps.remove(PARAMETER_WIDTH_DP)
    allProps.remove(PARAMETER_HEIGHT_DP)

    val deviceProperties =
      mutableMapOf<String, PsiPropertyItem>().apply {
        allProps.remove(PARAMETER_HARDWARE_DEVICE)?.let { put(PARAMETER_HARDWARE_DEVICE, it) }
        allProps.remove(PARAMETER_HARDWARE_ORIENTATION)?.let {
          put(PARAMETER_HARDWARE_ORIENTATION, it)
        }
        allProps.remove(PARAMETER_HARDWARE_DENSITY)?.let { put(PARAMETER_HARDWARE_DENSITY, it) }
        allProps.remove(PARAMETER_HARDWARE_WIDTH)?.let { put(PARAMETER_HARDWARE_WIDTH, it) }
        allProps.remove(PARAMETER_HARDWARE_HEIGHT)?.let { put(PARAMETER_HARDWARE_HEIGHT, it) }
        allProps.remove(PARAMETER_HARDWARE_DIM_UNIT)?.let { put(PARAMETER_HARDWARE_DIM_UNIT, it) }
        allProps.remove(PARAMETER_HARDWARE_IS_ROUND)?.let { put(PARAMETER_HARDWARE_IS_ROUND, it) }
        allProps.remove(PARAMETER_HARDWARE_CHIN_SIZE)?.let { put(PARAMETER_HARDWARE_CHIN_SIZE, it) }
        allProps.remove(PARAMETER_HARDWARE_CUTOUT)?.let { put(PARAMETER_HARDWARE_CUTOUT, it) }
        allProps.remove(PARAMETER_HARDWARE_NAVIGATION)?.let {
          put(PARAMETER_HARDWARE_NAVIGATION, it)
        }
      }
    val remainingProperties = allProps.values

    // Main preview parameters
    inspector.addEditorsForProperties(previewProperties)

    // Hardware parameters
    inspector.addSectionLabel("Hardware")
    addHardwareView(inspector, deviceProperties, editorProvider)

    // Display parameters
    inspector.addSectionLabel("Display")
    inspector.addEditorsForProperties(remainingProperties)
  }
}

/** [PsiPropertyItemControlTypeProvider] for properties of the Preview annotation. */
private object PreviewControlTypeProvider : PsiPropertyItemControlTypeProvider {
  override fun invoke(property: PsiPropertyItem): ControlType =
    when (property.name) {
      PARAMETER_API_LEVEL,
      PARAMETER_LOCALE,
      PARAMETER_HARDWARE_DEVICE,
      PARAMETER_HARDWARE_ORIENTATION,
      PARAMETER_HARDWARE_DIM_UNIT,
      PARAMETER_HARDWARE_DENSITY,
      PARAMETER_UI_MODE,
      PARAMETER_WALLPAPER,
      PARAMETER_HARDWARE_CUTOUT,
      PARAMETER_HARDWARE_NAVIGATION,
      PARAMETER_DEVICE -> ControlType.DROPDOWN
      PARAMETER_BACKGROUND_COLOR -> ControlType.COLOR_EDITOR
      PARAMETER_HARDWARE_IS_ROUND,
      PARAMETER_SHOW_DECORATION,
      PARAMETER_SHOW_SYSTEM_UI,
      PARAMETER_SHOW_BACKGROUND -> ControlType.THREE_STATE_BOOLEAN
      PARAMETER_GROUP,
      PARAMETER_FONT_SCALE -> ControlType.COMBO_BOX
      else -> ControlType.TEXT_EDITOR
    }
}
