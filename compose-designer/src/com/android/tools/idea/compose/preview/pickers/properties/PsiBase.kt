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

import com.android.tools.idea.compose.preview.PARAMETER_BACKGROUND_COLOR
import com.android.tools.idea.compose.preview.PARAMETER_DEVICE
import com.android.tools.idea.compose.preview.PARAMETER_FONT_SCALE
import com.android.tools.idea.compose.preview.PARAMETER_SHOW_BACKGROUND
import com.android.tools.idea.compose.preview.PARAMETER_SHOW_DECORATION
import com.android.tools.idea.compose.preview.PARAMETER_SHOW_SYSTEM_UI
import com.android.tools.idea.compose.preview.PARAMETER_UI_MODE
import com.android.tools.idea.util.ListenerCollection
import com.android.tools.property.panel.api.ControlType
import com.android.tools.property.panel.api.ControlTypeProvider
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumSupportProvider
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.NewPropertyItem
import com.android.tools.property.panel.api.PropertiesModel
import com.android.tools.property.panel.api.PropertiesModelListener
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.panel.api.PropertiesView
import java.util.function.Consumer

private const val PSI_PROPERTIES_VIEW_NAME = "PsiProperties"

/**
 * Base class for properties of the [PsiPropertyModel].
 */
interface PsiPropertyItem : NewPropertyItem

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
    listeners.forEach(Consumer {
      it.propertyValuesChanged(this)
    })
  }

  override fun deactivate() {}
}

private class PsiPropertiesInspectorBuilder(private val editorProvider: EditorProvider<PsiPropertyItem>)
  : InspectorBuilder<PsiPropertyItem> {
  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<PsiPropertyItem>) {
    properties.values.forEach {
      inspector.addEditor(editorProvider.createEditor(it))
    }
  }
}

/**
 * A [PropertiesView] for editing [PsiPropertyModel]s.
 */
internal class PsiPropertyView(model: PsiPropertyModel) : PropertiesView<PsiPropertyItem>(PSI_PROPERTIES_VIEW_NAME, model) {
  object PsiEnumProvider : EnumSupportProvider<PsiPropertyItem> {

    override fun invoke(property: PsiPropertyItem): EnumSupport? =
      when (property.name) {
        PARAMETER_UI_MODE -> object : EnumSupport {
          override val values: List<EnumValue> = UiMode.values().toList()
          override fun createValue(stringValue: String): EnumValue =
            UiMode.values().firstOrNull { stringValue == it.resolvedValue.toString() } ?: UiMode.NORMAL
        }
        PARAMETER_DEVICE -> object : EnumSupport {
          override val values: List<EnumValue> = Device.values().toList()
          override fun createValue(stringValue: String): EnumValue =
            Device.values().firstOrNull { stringValue == it.resolvedValue } ?: Device.DEFAULT
        }
        PARAMETER_FONT_SCALE -> object : EnumSupport {
          override val values: List<EnumValue> = FontScale.values().toList()
        }
        else -> null
      }
  }

  /**
   * [ControlTypeProvider] for [PsiPropertyItem]s that provides a text editor for every property.
   */
  inner class PsiPropertyItemControlTypeProvider : ControlTypeProvider<PsiPropertyItem> {
    override fun invoke(property: PsiPropertyItem): ControlType =
      when (property.name) {
        PARAMETER_UI_MODE -> ControlType.DROPDOWN
        PARAMETER_DEVICE -> ControlType.DROPDOWN
        PARAMETER_BACKGROUND_COLOR -> ControlType.COLOR_EDITOR
        PARAMETER_SHOW_DECORATION,
        PARAMETER_SHOW_SYSTEM_UI,
        PARAMETER_SHOW_BACKGROUND -> ControlType.THREE_STATE_BOOLEAN
        PARAMETER_FONT_SCALE -> ControlType.COMBO_BOX
        else -> ControlType.TEXT_EDITOR
      }
  }

  init {
    addTab("").apply {
      builders.add(PsiPropertiesInspectorBuilder(
        EditorProvider.create(PsiEnumProvider, PsiPropertyItemControlTypeProvider())))
    }
  }
}