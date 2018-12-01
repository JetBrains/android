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
package com.android.tools.idea.uibuilder.property2

import com.android.tools.idea.common.property2.api.EditorProvider
import com.android.tools.idea.common.property2.api.PropertiesView
import com.android.tools.idea.common.property2.api.Watermark
import com.android.tools.idea.uibuilder.property2.inspector.AllAttributesInspectorBuilder
import com.android.tools.idea.uibuilder.property2.inspector.DeclaredAttributesInspectorBuilder
import com.android.tools.idea.uibuilder.property2.inspector.IdInspectorBuilder
import com.android.tools.idea.uibuilder.property2.inspector.LayoutInspectorBuilder
import com.android.tools.idea.uibuilder.property2.inspector.ProgressBarInspectorBuilder
import com.android.tools.idea.uibuilder.property2.inspector.SelectedComponentBuilder
import com.android.tools.idea.uibuilder.property2.inspector.TextViewInspectorBuilder
import com.android.tools.idea.uibuilder.property2.inspector.ViewInspectorBuilder
import com.android.tools.idea.uibuilder.property2.support.NeleControlTypeProvider
import com.android.tools.idea.uibuilder.property2.support.NeleEnumSupportProvider

private const val VIEW_NAME = "LayoutEditor"
private const val BASIC_PAGE = "Basic"
private const val ADVANCED_PAGE = "Advanced"
private const val WATERMARK_MESSAGE = "No component selected."
private const val WATERMARK_ACTION_MESSAGE = "Select a component in the Component Tree or on the Design Surface."

class NelePropertiesView(model : NelePropertiesModel) : PropertiesView<NelePropertyItem>(VIEW_NAME, model) {
  private val enumSupportProvider = NeleEnumSupportProvider()
  private val controlTypeProvider = NeleControlTypeProvider(enumSupportProvider)
  private val editorProvider = EditorProvider.create(enumSupportProvider, controlTypeProvider)

  init {
    watermark = Watermark(WATERMARK_MESSAGE, WATERMARK_ACTION_MESSAGE, "")
    main.builders.add(SelectedComponentBuilder())
    val basic = addTab(BASIC_PAGE)
    basic.searchable = false
    basic.builders.add(IdInspectorBuilder(editorProvider))
    basic.builders.add(LayoutInspectorBuilder(model.facet.module.project, editorProvider))
    basic.builders.add(ViewInspectorBuilder(model.facet.module.project, editorProvider))
    basic.builders.add(TextViewInspectorBuilder(editorProvider))
    basic.builders.add(ProgressBarInspectorBuilder(editorProvider))
    val advanced = addTab(ADVANCED_PAGE)
    advanced.builders.add(DeclaredAttributesInspectorBuilder(model, enumSupportProvider))
    advanced.builders.add(AllAttributesInspectorBuilder(model, controlTypeProvider, editorProvider))
  }
}
