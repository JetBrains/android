/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.legacy

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.android.tools.idea.layoutinspector.properties.PropertySection
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.android.tools.idea.layoutinspector.properties.addInternalProperties
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.HashBasedTable
import com.google.common.util.concurrent.Futures
import java.util.concurrent.Future

internal const val ATTR_TOP = "top"
internal const val ATTR_BOTTOM = "bottom"
internal const val ATTR_LEFT = "left"
internal const val ATTR_RIGHT = "right"
internal const val ATTR_SCROLL_X = "scrollX"
internal const val ATTR_SCROLL_Y = "scrollY"
internal const val ATTR_X = "x"
internal const val ATTR_Y = "y"
internal const val ATTR_Z = "z"
@VisibleForTesting const val ATTR_DIM_BEHIND = "dim_behind"

/**
 * A [PropertiesProvider] that can handle pre-api 29 devices.
 *
 * Loads the properties
 */
class LegacyPropertiesProvider : PropertiesProvider {
  private var properties = mapOf<Long, PropertiesTable<InspectorPropertyItem>>()

  override val resultListeners = mutableListOf<(PropertiesProvider, ViewNode, PropertiesTable<InspectorPropertyItem>) -> Unit>()

  override fun requestProperties(view: ViewNode): Future<*> {
    val viewProperties = properties[view.drawId] ?: PropertiesTable.emptyTable()
    resultListeners.forEach { it(this, view, viewProperties) }
    return Futures.immediateFuture(null)
  }

  class Updater(val lookup: ViewNodeAndResourceLookup) {
    private var temp = mutableMapOf<Long, PropertiesTable<InspectorPropertyItem>>()

    fun apply(provider: LegacyPropertiesProvider) {
      provider.properties = temp
    }

    fun parseProperties(view: ViewNode, data: String) {
      val parent = ViewNode.readAccess { view.parent }
      var start = 0
      var stop: Boolean
      val table = HashBasedTable.create<String, String, InspectorPropertyItem>()
      do {
        val index = data.indexOf('=', start)
        val fullName = data.substring(start, index)
        val colonIndex = fullName.indexOf(':')
        val group = fullName.substring(0, Integer.max(0, colonIndex))
        val section = if (group == "layout") PropertySection.LAYOUT else PropertySection.DEFAULT
        val rawName = fullName.substring(colonIndex + 1)
        val index2 = data.indexOf(',', index + 1)
        val length = Integer.parseInt(data.substring(index + 1, index2))
        val rawValue = data.substring(index2 + 1, index2 + 1 + length)
        start = index2 + 1 + length

        val definition = PropertyMapper.mapPropertyName(rawName)
        if (definition != null) {
          val name = definition.name
          val type = definition.type
          val value = definition.value_mapper(rawValue)
          val property = InspectorPropertyItem(ANDROID_URI, name, name, type, value, section, null, view.drawId, lookup)
          table.put(property.namespace, property.name, property)
        }

        stop = start >= data.length
        if (!stop) {
          start += 1
        }
      }
      while (!stop)

      val parentTable: PropertiesTable<InspectorPropertyItem>? = parent?.let { temp[parent.drawId] }
      val parentScrollX = parentTable?.getOrNull(ANDROID_URI, ATTR_SCROLL_X)?.dimensionValue ?: 0
      val parentScrollY = parentTable?.getOrNull(ANDROID_URI, ATTR_SCROLL_Y)?.dimensionValue ?: 0
      view.layoutBounds.x = (table.remove(ANDROID_URI, ATTR_LEFT)?.dimensionValue ?: 0) - parentScrollX
      view.layoutBounds.y = (table.remove(ANDROID_URI, ATTR_TOP)?.dimensionValue ?: 0) - parentScrollY
      view.layoutBounds.width = table.remove(ANDROID_URI, SdkConstants.ATTR_WIDTH)?.dimensionValue ?: 0
      view.layoutBounds.height = table.remove(ANDROID_URI, SdkConstants.ATTR_HEIGHT)?.dimensionValue ?: 0
      view.textValue = table[ANDROID_URI, SdkConstants.ATTR_TEXT]?.value ?: ""
      val url = table[ANDROID_URI, ATTR_ID]?.value?.let { ResourceUrl.parse(it) }
      view.viewId = url?.let { ResourceReference(ResourceNamespace.TODO(), ResourceType.ID, it.name) }
      // TODO: add other layout flags if we care about them
      // TODO(171901393): since we're taking a screenshot rather than images of each view, disable setting layoutFlags for now, so we don't
      // add a Dimmer DrawViewNode (with this API the captured image is already dimmed).
      view.layoutFlags = table.remove(ANDROID_URI, ATTR_DIM_BEHIND)?.value?.let { PropertyMapper.toInt(it) } ?: 0

      // Remove other attributes that we already have elsewhere:
      table.remove(ANDROID_URI, ATTR_X)
      table.remove(ANDROID_URI, ATTR_Y)
      table.remove(ANDROID_URI, ATTR_Z)
      table.remove(ANDROID_URI, ATTR_BOTTOM)
      table.remove(ANDROID_URI, ATTR_RIGHT)

      val properties = PropertiesTable.create(table)
      addInternalProperties(properties, view, url?.name, lookup)
      temp[view.drawId] = properties
    }
  }
}
