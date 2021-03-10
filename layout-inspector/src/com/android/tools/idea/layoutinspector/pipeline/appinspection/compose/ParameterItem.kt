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
import com.android.tools.property.ptable2.PTableGroupModification

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
    ParameterGroupItem(name, type, value, viewId, lookup, rootId, index, reference, mutableListOf()).apply {
      addClonedChildrenFrom(this@ParameterGroupItem.children)
    }

  private fun addClonedChildrenFrom(items: List<ParameterItem>) {
    items.mapTo(children) { it.clone() }
    if (items.lastOrNull() is ShowMoreElementsItem) {
      children[children.size - 1] = ShowMoreElementsItem(this)
    }
  }

  /**
   * Return the last index into children that is not a ShowMoreElementsItem
   */
  val lastRealChildElementIndex: Int
    get() = children.lastIndex - (if (children.lastOrNull() is ShowMoreElementsItem) 1 else 0)

  /**
   * Return the index from the composite value on the device of the last child in children that is not a ShowMoreElementsItem
   *
   * This value may be identical to [lastRealChildElementIndex] unless some of the sibling values were skipped in the agent.
   */
  val lastRealChildReferenceIndex: Int
    get() = children.getOrNull(lastRealChildElementIndex)?.index ?: -1

  /**
   * Return the index of the children list where [ParameterItem.index] is the same as [referenceIndex]
   *
   * The [referenceIndex] value will usually come from one of [ParameterReference.indices].
   */
  fun elementIndexOf(referenceIndex: Int): Int {
    val element = children.getOrNull(referenceIndex)
    if (element?.index == referenceIndex) {
      return referenceIndex
    }
    return children.binarySearch { it.index - referenceIndex }
  }

  override fun expandWhenPossible(expandNow: (restructured: Boolean) -> Unit) {
    if (reference == null || children.isNotEmpty()) {
      expandNow(false)
      return
    }
    lookup.resolve(rootId, reference!!, 0, MAX_INITIAL_ITERABLE_SIZE) { other, _ ->
      if (other != null) {
        applyReplacement(other)
        expandNow(true)
      }
    }
  }

  fun applyReplacement(replacement: ParameterGroupItem): PTableGroupModification? {
    reference = replacement.reference
    if (children.isEmpty()) {
      addClonedChildrenFrom(replacement.children)
      return PTableGroupModification(children.toList())
    }
    val showMoreItem = children.lastOrNull() as? ShowMoreElementsItem ?: return null
    children.removeAt(children.lastIndex)
    val lastReferenceIndex = lastRealChildReferenceIndex
    val elementIndex = replacement.children.indexOfFirst { it.index > lastReferenceIndex }
    if (elementIndex < 0) {
      return null
    }
    val sizeBeforeAdding = children.size
    addClonedChildrenFrom(replacement.children.subList(elementIndex, replacement.lastRealChildElementIndex + 1))
    val added = children.subList(sizeBeforeAdding, children.size).toList()
    if (reference != null) {
      children.add(showMoreItem)
      showMoreItem.index = lastRealChildReferenceIndex + 1
    }
    val removed = if (reference == null) listOf(showMoreItem) else listOf()

    return PTableGroupModification(added, removed)
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
