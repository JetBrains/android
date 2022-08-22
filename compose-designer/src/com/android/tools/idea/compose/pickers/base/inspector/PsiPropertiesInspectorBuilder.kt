/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.pickers.base.inspector

import com.android.tools.idea.compose.pickers.base.property.PsiPropertyItem
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable

internal abstract class PsiPropertiesInspectorBuilder : InspectorBuilder<PsiPropertyItem> {
  protected abstract val editorProvider: EditorProvider<PsiPropertyItem>

  override fun attachToInspector(
    inspector: InspectorPanel,
    properties: PropertiesTable<PsiPropertyItem>
  ) {
    inspector.addEditorsForProperties(properties.values)
  }

  protected fun InspectorPanel.addEditorsForProperties(properties: Collection<PsiPropertyItem>) {
    properties.forEach {
      val modelEditor = editorProvider.createEditor(it)
      // Set the preferred size, to avoid layout managers from changing it, which may cause popups
      // close unexpectedly
      modelEditor.second.preferredSize = modelEditor.second.preferredSize
      this.addEditor(modelEditor)
    }
  }
}
