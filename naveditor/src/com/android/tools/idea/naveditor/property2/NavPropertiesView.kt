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
package com.android.tools.idea.naveditor.property2

import com.android.tools.idea.naveditor.property2.inspector.ActionInspectorBuilder
import com.android.tools.idea.naveditor.property2.inspector.ActionListInspectorBuilder
import com.android.tools.idea.naveditor.property2.inspector.AnimationInspectorBuilder
import com.android.tools.idea.naveditor.property2.inspector.ArgumentInspectorBuilder
import com.android.tools.idea.naveditor.property2.inspector.DeepLinkInspectorBuilder
import com.android.tools.idea.naveditor.property2.inspector.DefaultValueInspectorBuilder
import com.android.tools.idea.naveditor.property2.inspector.DestinationInspectorBuilder
import com.android.tools.idea.naveditor.property2.inspector.GraphInspectorBuilder
import com.android.tools.idea.naveditor.property2.inspector.LabelInspectorBuilder
import com.android.tools.idea.naveditor.property2.inspector.NameInspectorBuilder
import com.android.tools.idea.naveditor.property2.inspector.StartDestinationInspectorBuilder
import com.android.tools.idea.naveditor.property2.support.NavEnumSupportProvider
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.idea.uibuilder.property2.inspector.IdInspectorBuilder
import com.android.tools.idea.uibuilder.property2.inspector.SelectedComponentBuilder
import com.android.tools.idea.uibuilder.property2.support.NeleControlTypeProvider
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.PropertiesView
import com.android.tools.property.panel.api.Watermark

private const val VIEW_NAME = "NavEditor"
private const val WATERMARK_MESSAGE = "No component selected."
private const val WATERMARK_ACTION_MESSAGE = "Select a destination or action on the Design surface."

class NavPropertiesView(model: NelePropertiesModel) : PropertiesView<NelePropertyItem>(VIEW_NAME, model) {
  private val enumSupportProvider = NavEnumSupportProvider()
  private val controlTypeProvider = NeleControlTypeProvider(enumSupportProvider)
  private val editorProvider = EditorProvider.create(enumSupportProvider, controlTypeProvider)

  init {
    watermark = Watermark(WATERMARK_MESSAGE, WATERMARK_ACTION_MESSAGE, "")
    main.builders.add(SelectedComponentBuilder())
    val tab = addTab("")
    tab.builders.add(IdInspectorBuilder(editorProvider))
    tab.builders.add(LabelInspectorBuilder(editorProvider))
    tab.builders.add(DestinationInspectorBuilder(editorProvider))
    tab.builders.add(StartDestinationInspectorBuilder(editorProvider))
    tab.builders.add(GraphInspectorBuilder(editorProvider))
    tab.builders.add(NameInspectorBuilder(editorProvider))

    tab.builders.add(AnimationInspectorBuilder(editorProvider))
    tab.builders.add(DefaultValueInspectorBuilder())
    tab.builders.add(ActionInspectorBuilder(editorProvider))
    tab.builders.add(ArgumentInspectorBuilder())
    tab.builders.add(ActionListInspectorBuilder())
    tab.builders.add(DeepLinkInspectorBuilder())
  }
}