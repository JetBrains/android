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

import com.android.SdkConstants.*
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.property.panel.api.*

class ProgressBarInspectorBuilder(private val editorProvider: EditorProvider<NlPropertyItem>) {

  fun attachToInspector(
    inspector: InspectorPanel,
    properties: PropertiesTable<NlPropertyItem>,
    getTitleLine: () -> InspectorLineModel,
  ) {
    if (!isApplicable(properties)) return

    val titleLine = getTitleLine()
    inspector.addEditor(editorProvider.createEditor(properties["", ATTR_STYLE]), titleLine)
    val drawable =
      inspector.addEditor(
        editorProvider.createEditor(properties[ANDROID_URI, ATTR_PROGRESS_DRAWABLE]),
        titleLine,
      )
    val drawableInt =
      inspector.addEditor(
        editorProvider.createEditor(properties[ANDROID_URI, ATTR_INDETERMINATE_DRAWABLE]),
        titleLine,
      )
    val tint =
      addOptionalEditor(inspector, properties.getOrNull(ANDROID_URI, ATTR_PROGRESS_TINT), titleLine)
    val tintInt =
      addOptionalEditor(
        inspector,
        properties.getOrNull(ANDROID_URI, ATTR_INDETERMINATE_TINT),
        titleLine,
      )
    val max =
      inspector.addEditor(
        editorProvider.createEditor(properties[ANDROID_URI, ATTR_MAXIMUM]),
        titleLine,
      )
    val progress =
      inspector.addEditor(
        editorProvider.createEditor(properties[ANDROID_URI, ATTR_PROGRESS]),
        titleLine,
      )
    val indeterminate = properties[ANDROID_URI, ATTR_INDETERMINATE]
    val model = addEditorAndReturnEditorModel(inspector, indeterminate, titleLine)
    val updater =
      StateUpdater(
        indeterminate,
        listOf(drawable, tint, max, progress),
        listOf(drawableInt, tintInt),
      )
    model.addListener(updater)
    updater.valueChanged()
  }

  private fun addOptionalEditor(
    inspector: InspectorPanel,
    property: NlPropertyItem?,
    group: InspectorLineModel,
  ): InspectorLineModel? {
    if (property == null) return null
    return inspector.addEditor(editorProvider.createEditor(property), group)
  }

  private fun addEditorAndReturnEditorModel(
    inspector: InspectorPanel,
    property: NlPropertyItem,
    group: InspectorLineModel,
  ): PropertyEditorModel {
    val (model, editor) = editorProvider.createEditor(property)
    inspector.addCustomEditor(model, editor, group)
    return model
  }

  private fun isApplicable(properties: PropertiesTable<NlPropertyItem>): Boolean {
    return properties.getByNamespace(ANDROID_URI).keys.containsAll(REQUIRED_PROPERTIES) &&
      properties.getByNamespace("").keys.contains(ATTR_STYLE)
  }

  companion object {
    val REQUIRED_PROPERTIES =
      listOf(
        ATTR_PROGRESS_DRAWABLE,
        ATTR_INDETERMINATE_DRAWABLE,
        ATTR_MAXIMUM,
        ATTR_PROGRESS,
        ATTR_INDETERMINATE,
      )
  }

  private class StateUpdater(
    private val indeterminate: NlPropertyItem,
    private val determinateLines: List<InspectorLineModel?>,
    private val indeterminateLines: List<InspectorLineModel?>,
  ) : ValueChangedListener {
    var previousState: Boolean? = null

    override fun valueChanged() {
      val determinateState = indeterminate.resolvedValue != VALUE_TRUE
      if (previousState == determinateState) return
      indeterminateLines.forEach { it?.hidden = determinateState }
      determinateLines.forEach { it?.hidden = !determinateState }
      previousState = determinateState
    }
  }
}
