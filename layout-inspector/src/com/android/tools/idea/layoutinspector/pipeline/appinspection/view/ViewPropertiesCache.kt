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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.view

import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.ViewNodeCache
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.GetPropertiesResponse
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.PropertiesEvent

/**
 * Cache of view properties, to avoid expensive refetches when possible.
 */
class ViewPropertiesCache(private val client: ViewLayoutInspectorClient, model: InspectorModel) : ViewNodeCache<ViewPropertiesData>(model) {
  override suspend fun fetchDataFor(root: ViewNode, node: ViewNode): ViewPropertiesData? {
    val response = client.getProperties(root.drawId, node.drawId)
    return if (response != GetPropertiesResponse.getDefaultInstance()) {
      ViewPropertiesDataGenerator(StringTableImpl(response.stringsList), response.propertyGroup, model).generate()
    }
    else {
      null
    }
  }

  fun setAllFrom(event: PropertiesEvent) {
    val stringTable = StringTableImpl(event.stringsList)
    for (propertyGroup in event.propertyGroupsList) {
      setDataFor(event.rootId, propertyGroup.viewId, ViewPropertiesDataGenerator(stringTable, propertyGroup, model).generate())
    }
  }
}
