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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.annotations.concurrency.Slow
import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ComposeParametersCache
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ComposeParametersData
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewPropertiesCache
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewPropertiesData
import com.android.tools.idea.layoutinspector.properties.InspectorGroupPropertyItem
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.properties.NAMESPACE_INTERNAL
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.android.tools.idea.layoutinspector.properties.addInternalProperties
import com.android.tools.idea.layoutinspector.resource.SourceLocation
import com.android.tools.property.panel.api.PropertiesTable
import com.intellij.openapi.application.runReadAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Future

class AppInspectionPropertiesProvider(
  private val propertiesCache: ViewPropertiesCache,
  private val parametersCache: ComposeParametersCache?,
  private val model: InspectorModel)
  : PropertiesProvider {

  override val resultListeners = CopyOnWriteArrayList<(PropertiesProvider, ViewNode, PropertiesTable<InspectorPropertyItem>) -> Unit>()

  override fun requestProperties(view: ViewNode): Future<*> {
    val future = CompletableFuture<Unit>()
    val self = this

    CoroutineScope(Dispatchers.IO).launch {
      var propertiesTable: PropertiesTable<InspectorPropertyItem>? = null
      if (view !is ComposeViewNode) {
        val viewData = propertiesCache.getDataFor(view)
        if (viewData != null) {
          completeProperties(view, viewData)
          propertiesTable = viewData.properties
        }
      }
      else {
        val composeData = parametersCache?.getDataFor(view)
        if (composeData != null) {
          completeParameters(view, composeData)
          propertiesTable = composeData.parameters
        }
      }

      if (propertiesTable != null) {
        for (listener in resultListeners) {
          listener(self, view, propertiesTable)
        }
      }
      future.complete(Unit)
    }
    return future
  }

  /**
   * Complete the properties table with information from the [ViewNode].
   *
   * The properties were loaded from the agent, but the following cannot be completed before the [ViewNode] is known:
   * - The agent does not specify which attributes is a dimension type. Get that from the Studio side.
   * - Add the standard internal attributes from the [ViewNode].
   * - Add a call location to all known object types where the className is known.
   * - Create resolution stack items based on the resolution stack received from the agent.
   */
  @Slow // may use index for resolveDimension
  private fun completeProperties(view: ViewNode, propertiesData: ViewPropertiesData) {
    val properties = propertiesData.properties
    if (properties.getByNamespace(NAMESPACE_INTERNAL).isNotEmpty()) return

    properties.values.forEach { it.resolveDimensionType(view) }

    if (model.resourceLookup.hasResolver) {
      runReadAction {
        propertiesData.classNames.cellSet().mapNotNull { cell ->
          properties.getOrNull(cell.rowKey!!, cell.columnKey!!)?.let { convertToItemWithClassLocation(it, cell.value!!) }
        }.forEach { properties.put(it) }
        propertiesData.resolutionStacks.cellSet().mapNotNull { cell ->
          properties.getOrNull(cell.rowKey!!, cell.columnKey!!)?.let { convertToResolutionStackItem(it, view, cell.value!!) }
        }.forEach { properties.put(it) }
      }
    }
    addInternalProperties(properties, view, properties.getOrNull(ANDROID_URI, ATTR_ID)?.value ,model)
  }

  private fun completeParameters(view: ViewNode, parametersData: ComposeParametersData) {
    val parameters = parametersData.parameters
    if (parameters.getByNamespace(NAMESPACE_INTERNAL).isNotEmpty()) return

    addInternalProperties(parameters, view, "", model)
  }

  /**
   * Generate items with a classLocation for known object types.
   *
   * This strictly could have happened up front because the [ViewNode] is not needed for computing the
   * [SourceLocation] for the class used for this value. However the computation takes time so this will
   * delay that cost until it is needed to show the properties for the containing [ViewNode].
   */
  private fun convertToItemWithClassLocation(
    item: InspectorPropertyItem,
    className: String
  ): InspectorPropertyItem? {
    val classLocation = model.resourceLookup.resolveClassNameAsSourceLocation(className) ?: return null
    return InspectorGroupPropertyItem(item.namespace, item.name, item.type, item.initialValue, classLocation,
                                      item.section, item.source, item.viewId, item.lookup, emptyList())
  }

  /**
   * Generate items for displaying the resolution stack.
   *
   * Each property may include a resolution stack i.e. places and values in e.g. styles
   * that are overridden by other attribute or style assignments.
   *
   * In the inspector properties table we have chosen to show these as independent values
   * in collapsible sections for each property. The resolution stack is received as a list
   * of resource references that may (or may not) set the value of the current attribute.
   * The code below will lookup the value (from PSI source files) of each possible resource
   * reference. If any values were found the original property item is replaced with a group
   * item with children consisting of the available resource references where a value was
   * found.
   */
  private fun convertToResolutionStackItem(
    item: InspectorPropertyItem,
    view: ViewNode,
    resolutionStack: List<ResourceReference>
  ): InspectorPropertyItem? {
    val map = resolutionStack
      .associateWith { model.resourceLookup.findAttributeValue(item, view, it) }
      .filterValues { it != null }
      .toMutableMap()
    val firstRef = map.keys.firstOrNull()
    if (firstRef != null && firstRef == item.source) {
      map.remove(firstRef)
    }
    val classLocation: SourceLocation? = (item as? InspectorGroupPropertyItem)?.classLocation
    if (map.isNotEmpty() || item.source != null || classLocation != null) {
      // Make this item a group item such that the details are hidden until the item is expanded.
      // Note that there doesn't have to be sub items in the group. A source location or class location is enough to trigger this.
      return InspectorGroupPropertyItem(item.namespace, item.name, item.type, item.initialValue, classLocation,
                                        item.section, item.source, item.viewId, item.lookup, map)
    }
    return null
  }

}
