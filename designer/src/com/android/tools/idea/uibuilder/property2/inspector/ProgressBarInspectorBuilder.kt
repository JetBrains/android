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

import com.android.SdkConstants.*
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.property2.api.*
import com.android.tools.idea.uibuilder.property2.NelePropertyItem

class ProgressBarInspectorBuilder(private val editorProvider: EditorProvider<NelePropertyItem>) : InspectorBuilder<NelePropertyItem> {

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>) {
    if (!isApplicable(properties)) return

    inspector.addSeparator()
    val progressBarLabel = inspector.addExpandableTitle("ProgressBar")
    addEditor(inspector, properties["", ATTR_STYLE], progressBarLabel)
    val drawable = addEditor(inspector, properties[ANDROID_URI, ATTR_PROGRESS_DRAWABLE], progressBarLabel)
    val drawableInt = addEditor(inspector, properties[ANDROID_URI, ATTR_INDETERMINATE_DRAWABLE], progressBarLabel)
    val tint = addOptionalEditor(inspector, properties.getOrNull(ANDROID_URI, ATTR_PROGRESS_TINT), progressBarLabel)
    val tintInt = addOptionalEditor(inspector, properties.getOrNull(ANDROID_URI, ATTR_INDETERMINATE_TINT), progressBarLabel)
    val max = addEditor(inspector, properties[ANDROID_URI, ATTR_MAXIMUM], progressBarLabel)
    val progress = addEditor(inspector, properties[ANDROID_URI, ATTR_PROGRESS], progressBarLabel)
    addEditor(inspector, properties[ANDROID_URI, ATTR_VISIBILITY], progressBarLabel)
    addEditor(inspector, properties[ANDROID_URI, ATTR_VISIBILITY].designProperty, progressBarLabel)
    val model = addEditorAndReturnEditorModel(inspector, properties[ANDROID_URI, ATTR_INDETERMINATE], progressBarLabel)
    val updater = StateUpdater(model.property, listOf(drawable, tint, max, progress), listOf(drawableInt, tintInt))
    model.addListener(updater)
    updater.valueChanged()
  }

  private fun addOptionalEditor(inspector: InspectorPanel, property: NelePropertyItem?, group: InspectorLineModel): InspectorLineModel? {
    if (property == null) return null
    return addEditor(inspector, property, group)
  }

  private fun addEditor(inspector: InspectorPanel, property: NelePropertyItem, group: InspectorLineModel): InspectorLineModel {
    val line = inspector.addEditor(editorProvider(property))
    group.addChild(line)
    return line
  }

  private fun addEditorAndReturnEditorModel(
    inspector: InspectorPanel,
    property: NelePropertyItem,
    group: InspectorLineModel
  ): PropertyEditorModel {
    val (model, editor) = editorProvider(property)
    val line = inspector.addEditor(model, editor)
    group.addChild(line)
    return model
  }

  private fun isApplicable(properties: PropertiesTable<NelePropertyItem>): Boolean {
    return properties.getByNamespace(ANDROID_URI).keys.containsAll(REQUIRED_PROPERTIES)
        && properties.getByNamespace("").keys.contains(ATTR_STYLE)
  }

  companion object {
    val REQUIRED_PROPERTIES = listOf(
      ATTR_PROGRESS_DRAWABLE,
      ATTR_INDETERMINATE_DRAWABLE,
      ATTR_MAXIMUM,
      ATTR_PROGRESS,
      ATTR_INDETERMINATE,
      ATTR_VISIBILITY
    )
  }

  private class StateUpdater(
    private val indeterminate: PropertyItem,
    private val determinateLines: List<InspectorLineModel?>,
    private val indeterminateLines: List<InspectorLineModel?>
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
