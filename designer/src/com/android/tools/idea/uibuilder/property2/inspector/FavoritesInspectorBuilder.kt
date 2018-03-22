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

import com.android.SdkConstants
import com.android.SdkConstants.ATTR_VISIBILITY
import com.android.tools.idea.common.property2.api.EditorProvider
import com.android.tools.idea.common.property2.api.InspectorBuilder
import com.android.tools.idea.common.property2.api.InspectorPanel
import com.android.tools.idea.common.property2.api.PropertiesTable
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.google.common.base.Splitter
import com.intellij.ide.util.PropertiesComponent

const val STARRED_PROP = "ANDROID.STARRED_PROPERTIES"

class FavoritesInspectorBuilder(
  private val editorProvider: EditorProvider<NelePropertyItem>,
  private val propertiesComponent: PropertiesComponent = PropertiesComponent.getInstance()
) : InspectorBuilder<NelePropertyItem> {

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>) {
    if (!isApplicable(properties)) return

    inspector.addSeparator()
    val titleModel = inspector.addExpandableTitle("Favorite Attributes", initiallyExpanded = false)
    for (propertyName in starredProperties) {
      // TODO: Handle other namespaces
      val property = properties.getOrNull(SdkConstants.ANDROID_URI, propertyName)
      if (property != null) {
        val line = inspector.addEditor(editorProvider(property))
        titleModel.addChild(line)
      }
    }
  }

  private fun isApplicable(properties: PropertiesTable<NelePropertyItem>): Boolean {
    return starredProperties.firstOrNull({ properties.getOrNull(SdkConstants.ANDROID_URI, it) !=  null}) !=  null
  }

  private val starredPropertiesAsString: String
    get() {
      var starredProperties = propertiesComponent.getValue(STARRED_PROP)
      if (starredProperties == null) {
        starredProperties = ATTR_VISIBILITY
      }
      return starredProperties
    }

  private val starredProperties: Iterable<String>
    get() = Splitter.on(';').trimResults().omitEmptyStrings().split(starredPropertiesAsString)
}
