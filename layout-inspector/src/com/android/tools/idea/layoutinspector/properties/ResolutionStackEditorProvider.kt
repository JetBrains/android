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
package com.android.tools.idea.layoutinspector.properties

import com.android.tools.idea.layoutinspector.model.ResolutionStackModel
import com.android.tools.idea.layoutinspector.ui.ResolutionElementEditor
import com.android.tools.property.panel.api.ControlType
import com.android.tools.property.panel.api.ControlTypeProvider
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.EnumSupportProvider
import com.android.tools.property.panel.api.PropertyEditorModel
import javax.swing.JComponent

/**
 * [EditorProvider] that provides a link below the normal editor.
 */
class ResolutionStackEditorProvider(
  model: InspectorPropertiesModel,
  enumSupportProvider: EnumSupportProvider<InspectorPropertyItem>,
  private val controlTypeProvider: ControlTypeProvider<InspectorPropertyItem>
) : EditorProvider<InspectorPropertyItem> {
  private val baseTypeProvider = BaseTypeProvider(controlTypeProvider)
  private val editorProvider = EditorProvider.create(enumSupportProvider, baseTypeProvider)
  private val resolutionStackModel = ResolutionStackModel(model)
  private val linkEditorTypes = listOf(PropertyType.LAMBDA, PropertyType.FUNCTION_REFERENCE, PropertyType.SHOW_MORE_LINK)

  override fun createEditor(property: InspectorPropertyItem, asTableCellEditor: Boolean): Pair<PropertyEditorModel, JComponent> {
    val (model, editor) = editorProvider.createEditor(property, asTableCellEditor)
    model.readOnly = true
    if (!property.needsResolutionEditor) {
      return Pair(model, editor)
    }
    model.isCustomHeight = true
    return Pair(model, ResolutionElementEditor(resolutionStackModel, model, editor))
  }

  fun isValueClickable(property: InspectorPropertyItem): Boolean =
    ResolutionElementEditor.hasLinkPanel(property) || property.type in linkEditorTypes
}

/**
 * A [ResolutionElementEditor] consist of a base editor (readonly) and one or more links.
 *
 * This [ControlTypeProvider] will produce the [ControlType] of the base editor.
 */
private class BaseTypeProvider(
  private val controlTypeProvider: ControlTypeProvider<InspectorPropertyItem>
) : ControlTypeProvider<InspectorPropertyItem> {

  override fun invoke(property: InspectorPropertyItem): ControlType =
    when (val type = controlTypeProvider.invoke(property)) {
      TEXT_RESOURCE_EDITOR -> ControlType.TEXT_EDITOR
      COLOR_RESOURCE_EDITOR -> ControlType.COLOR_EDITOR
      else -> type
    }
}
