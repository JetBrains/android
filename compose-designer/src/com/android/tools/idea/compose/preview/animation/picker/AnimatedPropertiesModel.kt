/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation.picker

import com.android.tools.idea.compose.pickers.base.enumsupport.EnumSupportValuesProvider
import com.android.tools.idea.compose.pickers.base.inspector.PsiPropertiesInspectorBuilder
import com.android.tools.idea.compose.pickers.base.model.PsiPropertiesModel
import com.android.tools.idea.compose.pickers.base.property.PsiPropertyItem
import com.android.tools.idea.compose.pickers.base.tracking.ComposePickerTracker
import com.android.tools.idea.compose.pickers.common.enumsupport.PsiEnumProvider
import com.android.tools.idea.compose.pickers.common.inspector.PsiEditorProvider
import com.android.tools.idea.compose.pickers.common.inspector.PsiPropertyItemControlTypeProvider
import com.android.tools.idea.compose.pickers.common.tracking.NoOpTracker
import com.android.tools.idea.compose.preview.animation.ComposeUnit
import com.android.tools.idea.compose.preview.message
import com.android.tools.property.panel.api.ControlType
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.collect.HashBasedTable

const val INITIAL_PROPERTY: String = "initial"
const val TARGET_PROPERTY: String = "target"

/** A [PsiPropertiesModel] for displaying and editing an [initial] and [target] states. */
internal class AnimatedPropertiesModel(
  val initial: ComposeUnit.Unit<*>,
  val target: ComposeUnit.Unit<*>,
  val onModified: (ComposeUnit.Unit<*>?, ComposeUnit.Unit<*>?) -> Unit
) : PsiPropertiesModel() {

  override val properties: PropertiesTable<PsiPropertyItem> =
    PropertiesTable.create(
      HashBasedTable.create<String, String, PsiPropertyItem>().also { table ->
        initial.createProperties(message("animation.inspector.picker.header.initial"))
          .forEachIndexed { index, it ->
            table.put(INITIAL_PROPERTY, "$index", it)
            it.addListener { propertiesValuesChanged() }
          }
        target.createProperties(message("animation.inspector.picker.header.target"))
          .forEachIndexed { index, it ->
            table.put(TARGET_PROPERTY, "$index", it)
            it.addListener { propertiesValuesChanged() }
          }
      }
    )

  override val inspectorBuilder: PsiPropertiesInspectorBuilder =
    object : PsiPropertiesInspectorBuilder() {
      override val editorProvider: EditorProvider<PsiPropertyItem> =
        PsiEditorProvider(
          PsiEnumProvider(EnumSupportValuesProvider.EMPTY),
          AnimatedPropertyTypeProvider
        )

      override fun attachToInspector(
        inspector: InspectorPanel,
        properties: PropertiesTable<PsiPropertyItem>
      ) {
        // If parameter is one-dimensional it displayed as:
        //      initial : {value}
        //      target : {value}
        // If parameter is multidimensional it displayed together with property names, for example:
        //      initial
        //        x : {value}
        //        y : {value}
        //      target
        //        x : {value}
        //        y : {value}
        // `properties` includes properties for both initial and target
        // The size is checked to determine how it will be displayed.
        val size = properties.size
        if (size <= 2 || size % 2 == 1) {
          inspector.addEditorsForProperties(properties.values)
        } else {
          inspector.addSectionLabel("initial")
          // First half of properties.
          inspector.addEditorsForProperties(
            properties.values.filterIndexed { index, _ -> index < size / 2 }
          )
          inspector.addSectionLabel("target")
          // Second half of properties.
          inspector.addEditorsForProperties(
            properties.values.filterIndexed { index, _ -> index >= size / 2 }
          )
        }
      }
    }

  override val tracker: ComposePickerTracker = NoOpTracker

  private fun propertiesValuesChanged() {
    try {
      val resolvedInitial =
        initial.parseUnit { index ->
          properties[INITIAL_PROPERTY, "$index"].let { it.resolvedValue ?: it.defaultValue }
        }
      val resolvedTarget =
        initial.parseUnit { index ->
          properties[TARGET_PROPERTY, "$index"].let { it.resolvedValue ?: it.defaultValue }
        }
      onModified(resolvedInitial, resolvedTarget)
    } catch (_: Exception) {}
  }

  private object AnimatedPropertyTypeProvider : PsiPropertyItemControlTypeProvider {
    override fun invoke(property: PsiPropertyItem): ControlType =
      when (property.namespace) {
        // TODO(b/256586457) Add other types for color, boolean, etc.
        else -> ControlType.TEXT_EDITOR
      }
  }
}
