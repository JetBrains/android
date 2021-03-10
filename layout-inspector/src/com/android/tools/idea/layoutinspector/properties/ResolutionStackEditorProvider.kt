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
import com.android.tools.property.panel.api.ControlTypeProvider
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.EnumSupportProvider
import com.android.tools.property.panel.api.LinkPropertyItem
import com.android.tools.property.panel.api.PropertyEditorModel
import javax.swing.JComponent

/**
 * [EditorProvider] that provides a link below the normal editor.
 */
class ResolutionStackEditorProvider(
  model: InspectorPropertiesModel,
  enumSupportProvider: EnumSupportProvider<InspectorPropertyItem>,
  controlTypeProvider: ControlTypeProvider<InspectorPropertyItem>
) : EditorProvider<InspectorPropertyItem> {
  private val editorProvider = EditorProvider.create(enumSupportProvider, controlTypeProvider)
  private val resolutionStackModel = ResolutionStackModel(model)

  override fun createEditor(property: InspectorPropertyItem, asTableCellEditor: Boolean): Pair<PropertyEditorModel, JComponent> {
    val (model, editor) = editorProvider.createEditor(property, asTableCellEditor)
    model.isUsedInRendererWithSelection = true
    model.readOnly = property !is LinkPropertyItem
    return if (property is InspectorGroupPropertyItem) Pair(model, ResolutionElementEditor(resolutionStackModel, model, editor))
           else Pair(model, editor)
  }

  fun isValueEditable(property: InspectorPropertyItem): Boolean =
    ResolutionElementEditor.hasLinkPanel(property) || property is LinkPropertyItem
}
