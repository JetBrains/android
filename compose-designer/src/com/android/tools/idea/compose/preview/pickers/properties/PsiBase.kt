/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.pickers.properties

import com.android.tools.idea.compose.preview.PARAMETER_API_LEVEL
import com.android.tools.idea.compose.preview.PARAMETER_BACKGROUND_COLOR
import com.android.tools.idea.compose.preview.PARAMETER_DEVICE
import com.android.tools.idea.compose.preview.PARAMETER_FONT_SCALE
import com.android.tools.idea.compose.preview.PARAMETER_GROUP
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DEVICE
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DIM_UNIT
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DENSITY
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_ORIENTATION
import com.android.tools.idea.compose.preview.PARAMETER_LOCALE
import com.android.tools.idea.compose.preview.PARAMETER_SHOW_BACKGROUND
import com.android.tools.idea.compose.preview.PARAMETER_SHOW_DECORATION
import com.android.tools.idea.compose.preview.PARAMETER_SHOW_SYSTEM_UI
import com.android.tools.idea.compose.preview.PARAMETER_UI_MODE
import com.android.tools.idea.compose.preview.pickers.properties.enumsupport.EnumSupportValuesProvider
import com.android.tools.idea.compose.preview.pickers.properties.enumsupport.PsiEnumProvider
import com.android.tools.idea.compose.preview.pickers.properties.inspector.PsiEditorProvider
import com.android.tools.idea.compose.preview.pickers.properties.inspector.PsiPropertiesInspectorBuilder
import com.android.tools.idea.util.ListenerCollection
import com.android.tools.property.panel.api.ControlType
import com.android.tools.property.panel.api.ControlTypeProvider
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.NewPropertyItem
import com.android.tools.property.panel.api.PropertiesModel
import com.android.tools.property.panel.api.PropertiesModelListener
import com.android.tools.property.panel.api.PropertiesView

private const val PSI_PROPERTIES_VIEW_NAME = "PsiProperties"

/**
 * Base class for properties of the [PsiPropertyModel].
 */
interface PsiPropertyItem : NewPropertyItem {
  override fun isSameProperty(qualifiedName: String): Boolean = false
  override val namespace: String get() = ""
}

/**
 * Base [PropertiesModel] for pickers interacting with PSI elements.
 */
abstract class PsiPropertyModel : PropertiesModel<PsiPropertyItem> {
  private val listeners = ListenerCollection.createWithDirectExecutor<PropertiesModelListener<PsiPropertyItem>>()

  override fun addListener(listener: PropertiesModelListener<PsiPropertyItem>) {
    // For now, the properties are always generated at load time, so we can always make this call when the listener is added.
    listener.propertiesGenerated(this)
    listeners.add(listener)
  }

  override fun removeListener(listener: PropertiesModelListener<PsiPropertyItem>) {
    listeners.remove(listener)
  }

  internal fun firePropertyValuesChanged() {
    listeners.forEach {
      it.propertyValuesChanged(this)
    }
  }

  override fun deactivate() {}
}

/**
 * A [PropertiesView] for editing [PsiPropertyModel]s.
 */
internal class PsiPropertyView(
  model: PsiPropertyModel,
  enumSupportValuesProvider: EnumSupportValuesProvider
) : PropertiesView<PsiPropertyItem>(PSI_PROPERTIES_VIEW_NAME, model) {

  init {
    addTab("").apply {
      builders.add(
        PsiPropertiesInspectorBuilder(
          PsiEditorProvider(
            PsiEnumProvider(enumSupportValuesProvider)
          )
        )
      )
    }
  }
}

/**
 * [ControlTypeProvider] for [PsiPropertyItem]s that provides a text editor for every property.
 */
object PsiPropertyItemControlTypeProvider : ControlTypeProvider<PsiPropertyItem> {
  override fun invoke(property: PsiPropertyItem): ControlType = when (property.name) {
      PARAMETER_API_LEVEL,
      PARAMETER_LOCALE,
      PARAMETER_HARDWARE_DEVICE,
      PARAMETER_HARDWARE_ORIENTATION,
      PARAMETER_HARDWARE_DIM_UNIT,
      PARAMETER_HARDWARE_DENSITY,
      PARAMETER_UI_MODE,
      PARAMETER_DEVICE -> ControlType.DROPDOWN
      PARAMETER_BACKGROUND_COLOR -> ControlType.COLOR_EDITOR
      PARAMETER_SHOW_DECORATION,
      PARAMETER_SHOW_SYSTEM_UI,
      PARAMETER_SHOW_BACKGROUND -> ControlType.THREE_STATE_BOOLEAN
      PARAMETER_GROUP,
      PARAMETER_FONT_SCALE -> ControlType.COMBO_BOX
      else -> ControlType.TEXT_EDITOR
    }
}