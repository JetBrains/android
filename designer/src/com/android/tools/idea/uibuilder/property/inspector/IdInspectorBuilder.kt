/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *I
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.uibuilder.property.inspector

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.TAG_DEEP_LINK
import com.android.SdkConstants.TAG_GROUP
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_MENU
import com.android.tools.idea.uibuilder.model.PreferenceUtils
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable
import org.jetbrains.android.dom.navigation.NavigationSchema.TAG_ARGUMENT

/** An [InspectorBuilder] for the [ATTR_ID] attribute shown on top in the Nele inspector. */
class IdInspectorBuilder(private val editorProvider: EditorProvider<NlPropertyItem>) :
  InspectorBuilder<NlPropertyItem> {
  private val menuTags = listOf(TAG_GROUP, TAG_ITEM, TAG_MENU)
  private val navTags = listOf(TAG_DEEP_LINK, TAG_ARGUMENT)
  private val hiddenTags = PreferenceUtils.VALUES.union(menuTags).union(navTags)

  override fun attachToInspector(
    inspector: InspectorPanel,
    properties: PropertiesTable<NlPropertyItem>,
  ) {
    val property = properties.getOrNull(ANDROID_URI, ATTR_ID) ?: return
    if (!isApplicable(property)) return

    inspector.addEditor(editorProvider.createEditor(property))
  }

  private fun isApplicable(property: NlPropertyItem): Boolean {
    return property.components.size == 1 && property.components.none { it.tagName in hiddenTags }
  }
}
