/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,ndroid
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.layoutinspector.properties

import com.android.SdkConstants.ATTR_HEIGHT
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_WIDTH
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.properties.PropertySection.DIMENSION
import com.android.tools.idea.layoutinspector.properties.PropertySection.RECOMPOSITIONS
import com.android.tools.idea.layoutinspector.properties.PropertySection.VIEW
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.util.concurrent.Futures
import java.util.concurrent.Future
import com.android.tools.idea.layoutinspector.properties.PropertyType as Type

// Constants for fabricated internal properties
const val NAMESPACE_INTERNAL = "internal"
const val ATTR_X = "x"
const val ATTR_Y = "y"

/**
 * A [PropertiesProvider] provides properties to registered listeners..
 */
interface PropertiesProvider {

  /**
   * Listeners for [PropertiesProvider] results.
   */
  val resultListeners: MutableList<(PropertiesProvider, ViewNode, PropertiesTable<InspectorPropertyItem>) -> Unit>

  /**
   * Requests properties for the specified [view].
   *
   * This is potentially an asynchronous request. The associated [InspectorPropertiesModel]
   * is notified when the table is ready.
   */
  fun requestProperties(view: ViewNode): Future<*>
}

object EmptyPropertiesProvider : PropertiesProvider {

  override val resultListeners = mutableListOf<(PropertiesProvider, ViewNode, PropertiesTable<InspectorPropertyItem>) -> Unit>()

  override fun requestProperties(view: ViewNode): Future<*> {
    return Futures.immediateFuture(null)
  }
}

/**
 * Add a few fabricated internal attributes.
 */
fun addInternalProperties(
  table: PropertiesTable<InspectorPropertyItem>,
  view: ViewNode,
  attrId: String?,
  lookup: ViewNodeAndResourceLookup
) {
  add(table, ATTR_NAME, Type.STRING, view.qualifiedName, VIEW, view.drawId, lookup)
  add(table, ATTR_X, Type.DIMENSION, view.layoutBounds.x.toString(), DIMENSION, view.drawId, lookup)
  add(table, ATTR_Y, Type.DIMENSION, view.layoutBounds.y.toString(), DIMENSION, view.drawId, lookup)
  add(table, ATTR_WIDTH, Type.DIMENSION, view.layoutBounds.width.toString(), DIMENSION, view.drawId, lookup)
  add(table, ATTR_HEIGHT, Type.DIMENSION, view.layoutBounds.height.toString(), DIMENSION, view.drawId, lookup)
  attrId?.let { add(table, ATTR_ID, Type.STRING, it, VIEW, view.drawId, lookup) }

  (view as? ComposeViewNode)?.addComposeProperties(table, lookup)
}

private fun ComposeViewNode.addComposeProperties(table: PropertiesTable<InspectorPropertyItem>, lookup: ViewNodeAndResourceLookup) {
  if (!recompositions.isEmpty) {
    // Do not show the "Recomposition" section in the properties panel for nodes without any counts.
    // This includes inlined composables for which we are unable to get recomposition counts for.
    add(table, "count", Type.INT32, recompositions.count.toString(), RECOMPOSITIONS, drawId, lookup)
    add(table, "skips", Type.INT32, recompositions.skips.toString(), RECOMPOSITIONS, drawId, lookup)
  }
}

private fun add(table: PropertiesTable<InspectorPropertyItem>, name: String, type: Type, value: String?, section: PropertySection, id: Long,
                lookup: ViewNodeAndResourceLookup) {
  table.put(InspectorPropertyItem(NAMESPACE_INTERNAL, name, type, value, section, null, id, lookup))
}
