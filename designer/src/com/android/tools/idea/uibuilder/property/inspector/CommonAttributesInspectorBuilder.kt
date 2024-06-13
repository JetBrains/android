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
package com.android.tools.idea.uibuilder.property.inspector

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ALPHA
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorLineModel
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.project.Project

class CommonAttributesInspectorBuilder(
  project: Project,
  private val editorProvider: EditorProvider<NlPropertyItem>,
) : InspectorBuilder<NlPropertyItem> {

  private val viewInspector = ViewInspectorBuilder(project, editorProvider)
  private val textInspector = TextViewInspectorBuilder(editorProvider)
  private val progressBarInspector = ProgressBarInspectorBuilder(editorProvider)
  private val constraintLayoutFlowInspector = ConstraintLayoutFlowInspectorBuilder(editorProvider)

  override fun resetCache() {
    viewInspector.resetCache()
  }

  override fun attachToInspector(
    inspector: InspectorPanel,
    properties: PropertiesTable<NlPropertyItem>,
  ) {
    if (!InspectorSection.COMMON.visible) {
      return
    }
    val generator = TitleGenerator(inspector)
    viewInspector.attachToInspector(inspector, properties) { generator.title }
    textInspector.attachToInspector(inspector, properties) { generator.title }
    progressBarInspector.attachToInspector(inspector, properties) { generator.title }
    constraintLayoutFlowInspector.attachToInspector(inspector, properties) { generator.title }
    addCommonForAll(inspector, properties, generator)
  }

  @VisibleForTesting
  class TitleGenerator(val inspector: InspectorPanel) {
    private var titleHolder: InspectorLineModel? = null

    var titleAdded = false
      private set

    val title: InspectorLineModel
      get() {
        val line = titleHolder ?: inspector.addExpandableTitle(InspectorSection.COMMON.title)
        titleHolder = line
        titleAdded = true
        return line
      }
  }

  private fun addCommonForAll(
    inspector: InspectorPanel,
    properties: PropertiesTable<NlPropertyItem>,
    generator: TitleGenerator,
  ) {
    if (!generator.titleAdded) {
      // Only add the common elements if the basic section was added already.
      return
    }
    addIfExist(inspector, properties.getOrNull(ANDROID_URI, ATTR_ALPHA), generator.title)
  }

  private fun addIfExist(
    inspector: InspectorPanel,
    property: NlPropertyItem?,
    title: InspectorLineModel,
  ) {
    if (property != null) {
      inspector.addEditor(editorProvider.createEditor(property), title)
    }
  }
}
