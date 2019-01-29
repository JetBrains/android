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
package com.android.tools.idea.uibuilder.property2.inspector

import com.android.tools.idea.common.property2.api.EditorProvider
import com.android.tools.idea.common.property2.api.InspectorBuilder
import com.android.tools.idea.common.property2.api.InspectorLineModel
import com.android.tools.idea.common.property2.api.InspectorPanel
import com.android.tools.idea.common.property2.api.PropertiesTable
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.project.Project

private const val BASIC_TITLE = "Common Attributes"

class BasicAttributesInspectorBuilder(project: Project,
                                      editorProvider: EditorProvider<NelePropertyItem>) : InspectorBuilder<NelePropertyItem> {
  private val viewInspector = ViewInspectorBuilder(project, editorProvider)
  private val textInspector = TextViewInspectorBuilder(editorProvider)
  private val progressBarInspector = ProgressBarInspectorBuilder(editorProvider)

  override fun resetCache() {
    viewInspector.resetCache()
  }

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>) {
    val generator = TitleGenerator(inspector)
    viewInspector.attachToInspector(inspector, properties) { generator.title }
    textInspector.attachToInspector(inspector, properties) { generator.title }
    progressBarInspector.attachToInspector(inspector, properties)  { generator.title }
  }

  @VisibleForTesting
  class TitleGenerator(val inspector: InspectorPanel) {
    var titleHolder: InspectorLineModel? = null

    val title: InspectorLineModel
      get() {
        val line = titleHolder ?: inspector.addExpandableTitle(BASIC_TITLE)
        titleHolder = line
        return line
      }
  }
}
