/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.properties

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceReference
import com.android.testutils.MockitoKt
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionPropertiesProvider
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.LiveViewPropertiesCache
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewPropertiesData
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.collect.HashBasedTable

/** Create a property, and use [AppInspectionPropertiesProvider] to add resource information. */
suspend fun createTestProperty(
  name: String,
  type: PropertyType,
  value: String?,
  source: ResourceReference?,
  resolutionStack: List<ResourceReference>,
  node: ViewNode,
  model: InspectorModel,
): InspectorPropertyItem {
  val item =
    InspectorPropertyItem(
      SdkConstants.ANDROID_URI,
      name,
      name,
      type,
      value,
      PropertySection.DECLARED,
      source,
      node.drawId,
      model,
    )
  val cache = LiveViewPropertiesCache(MockitoKt.mock(), model)
  val provider = AppInspectionPropertiesProvider(cache, null, model)
  val propertyTable = HashBasedTable.create<String, String, InspectorPropertyItem>(3, 10)
  propertyTable.put(item.namespace, item.name, item)
  val resolutionTable = HashBasedTable.create<String, String, List<ResourceReference>>(1, 1)
  resolutionTable.put(item.namespace, item.name, resolutionStack)
  val classNameTable = HashBasedTable.create<String, String, String>(1, 1)
  val properties = PropertiesTable.create(propertyTable)
  val data = ViewPropertiesData(properties, resolutionTable, classNameTable)
  provider.completeProperties(node, data)
  return properties[item.namespace, item.name]
}
