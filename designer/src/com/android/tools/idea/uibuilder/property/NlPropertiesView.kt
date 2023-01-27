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
package com.android.tools.idea.uibuilder.property

import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.PropertiesView
import com.android.tools.property.panel.api.Watermark
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.property.inspector.AllAttributesInspectorBuilder
import com.android.tools.idea.uibuilder.property.inspector.CommonAttributesInspectorBuilder
import com.android.tools.idea.uibuilder.property.inspector.ComponentActionsInspectorBuilder
import com.android.tools.idea.uibuilder.property.inspector.ConstraintLayoutHelperInspectorBuilder
import com.android.tools.idea.uibuilder.property.inspector.DeclaredAttributesInspectorBuilder
import com.android.tools.idea.uibuilder.property.inspector.FavoritesInspectorBuilder
import com.android.tools.idea.uibuilder.property.inspector.IdInspectorBuilder
import com.android.tools.idea.uibuilder.property.inspector.LayoutInspectorBuilder
import com.android.tools.idea.uibuilder.property.inspector.SelectedComponentBuilder
import com.android.tools.idea.uibuilder.property.inspector.TransformsAttributesInspectorBuilder
import com.android.tools.idea.uibuilder.property.support.NlControlTypeProvider
import com.android.tools.idea.uibuilder.property.support.NlEnumSupportProvider

private const val VIEW_NAME = "LayoutEditor"
private const val WATERMARK_MESSAGE = "No component selected."
private const val WATERMARK_ACTION_MESSAGE = "Select a component in the Component Tree or on the Design Surface."

class NlPropertiesView(model : NlPropertiesModel) : PropertiesView<NlPropertyItem>(VIEW_NAME, model) {
  private val enumSupportProvider = NlEnumSupportProvider(model)
  private val controlTypeProvider = NlControlTypeProvider(enumSupportProvider)
  private val editorProvider = EditorProvider.create(enumSupportProvider, controlTypeProvider)

  init {
    watermark = Watermark(WATERMARK_MESSAGE, WATERMARK_ACTION_MESSAGE, "")
    main.builders.add(SelectedComponentBuilder(model))
    val tab = addTab("")
    if (StudioFlags.NELE_PROPERTY_PANEL_ACTIONBAR.get()) {
      tab.builders.add(ComponentActionsInspectorBuilder(model))
    }
    tab.builders.add(IdInspectorBuilder(editorProvider))
    tab.builders.add(ConstraintLayoutHelperInspectorBuilder(editorProvider))
    tab.builders.add(DeclaredAttributesInspectorBuilder(model, enumSupportProvider))
    tab.builders.add(LayoutInspectorBuilder(model.facet.module.project, editorProvider))
    tab.builders.add(FavoritesInspectorBuilder(model, enumSupportProvider))
    tab.builders.add(TransformsAttributesInspectorBuilder(model, enumSupportProvider))
    tab.builders.add(CommonAttributesInspectorBuilder(model.project, editorProvider))
    tab.builders.add(AllAttributesInspectorBuilder(model, controlTypeProvider, editorProvider))
  }
}
