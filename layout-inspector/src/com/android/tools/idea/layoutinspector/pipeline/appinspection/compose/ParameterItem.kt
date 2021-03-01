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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.compose

import com.android.SdkConstants
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.properties.PropertySection
import com.android.tools.idea.layoutinspector.properties.PropertyType
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.android.tools.property.ptable2.PTableGroupItem

/**
 * A compose parameter for display in the attributes panel.
 */
open class ParameterItem(
  name: String,
  type: PropertyType,
  value: String?,
  viewId: Long,
  lookup: ViewNodeAndResourceLookup,
  val rootId: Long,
  var index: Int
) : InspectorPropertyItem(SdkConstants.ANDROID_URI, name, type, value, PropertySection.DEFAULT, null, viewId, lookup) {

  open fun clone(): ParameterItem =
    ParameterItem(name, type, value, viewId, lookup, rootId, index)
}

/**
 * A composite parameter.
 */
class ParameterGroupItem(
  name: String,
  type: PropertyType,
  value: String?,
  viewId: Long,
  lookup: ViewNodeAndResourceLookup,
  rootId: Long,
  index: Int,
  var reference: ParameterReference?,
  override var children: MutableList<ParameterItem>
) : ParameterItem(name, type, value, viewId, lookup, rootId, index), PTableGroupItem {

  override fun clone(): ParameterGroupItem =
    ParameterGroupItem(name, type, value, viewId, lookup, rootId, index, reference, children.mapTo(mutableListOf()) { it.clone() })

  fun cloneChildrenFrom(other: ParameterGroupItem) {
    val clone = other.clone()
    reference = other.reference
    children = clone.children
  }

  override fun expandWhenPossible(expandNow: (restructured: Boolean) -> Unit) {
    if (reference == null || children.isNotEmpty()) {
      expandNow(false)
      return
    }
    lookup.resolve(rootId, reference!!) { other ->
      if (other != null) {
        cloneChildrenFrom(other)
        expandNow(true)
      }
    }
  }
}

/**
 * A reference to another composite parameter.
 */
class ParameterReference(
  val nodeId: Long,
  val parameterIndex: Int,
  val indices: IntArray
)
