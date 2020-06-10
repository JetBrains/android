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
package com.android.tools.profilers.memory

import com.android.tools.profilers.memory.adapters.FieldObject
import com.android.tools.profilers.memory.adapters.InstanceObject
import com.android.tools.profilers.memory.adapters.MemoryObject
import com.android.tools.profilers.memory.adapters.ReferenceObject
import com.android.tools.profilers.memory.adapters.ValueObject

/**
 * This module defines several kinds of nodes for instances that are re-used in different places
 */

/**
 * A simple leaf node that does not expand
 */
internal class LeafNode<T: MemoryObject>(adapter: T) : LazyMemoryObjectTreeNode<T>(adapter, true) {
  override fun computeChildrenCount() = 0
  override fun expandNode() { }
}

/**
 * A tree node that lazily expands to the instance's fields
 */
internal class InstanceDetailsTreeNode(adapter: InstanceObject) : LazyMemoryObjectTreeNode<InstanceObject>(adapter, true) {
  override fun computeChildrenCount() = adapter.fieldCount

  override fun expandNode() {
    if (myMemoizedChildrenCount != myChildren.size) {
      val fields = adapter.fields
      myMemoizedChildrenCount = fields.size
      fields.forEach{addChild(it, ::FieldTreeNode)}
    }
  }
}

/**
 * A tree node that lazily expands to the field's sub-fields
 */
private class FieldTreeNode(adapter: FieldObject) : LazyMemoryObjectTreeNode<FieldObject>(adapter, true) {
  override fun computeChildrenCount() = adapter.asInstance?.fieldCount ?: 0

  override fun expandNode() {
    if (myMemoizedChildrenCount != myChildren.size && adapter.asInstance != null) {
      val fields = adapter.asInstance!!.fields
      myMemoizedChildrenCount = fields.size
      fields.forEach{addChild(it, ::FieldTreeNode)}
    }
  }
}

/**
 * A tree node that lazily expands to the instance's references
 */
open class ReferenceTreeNode(valueObject: ValueObject) : LazyMemoryObjectTreeNode<ValueObject>(valueObject, false) {
  private var myReferenceObjects: List<ReferenceObject>? = null
  override fun computeChildrenCount(): Int {
    if (myReferenceObjects == null) {
      myReferenceObjects = when (val a = adapter) {
        is InstanceObject -> nextReferences(a)
        is ReferenceObject -> nextReferences(a.referenceInstance)
        else -> emptyList()
      }
    }
    return myReferenceObjects!!.size
  }

  override fun expandNode() {
    childCount // ensure we grab all the references
    val refObjs = myReferenceObjects!!
    if (myMemoizedChildrenCount != myChildren.size) {
      refObjs.forEach{addChild(it, ::makeChildNode)}
    }
  }

  protected open fun makeChildNode(value: ReferenceObject): ReferenceTreeNode = ReferenceTreeNode(value)
  protected open fun nextReferences(inst: InstanceObject): List<ReferenceObject> = inst.references
}

/**
 * A reference tree node that only expands towards the nearest GC root
 */
class NearestGCRootTreeNode(valueObject: ValueObject): ReferenceTreeNode(valueObject) {
  override fun makeChildNode(value: ReferenceObject) = NearestGCRootTreeNode(value)
  override fun nextReferences(inst: InstanceObject) = inst.references.filter { it.depth < inst.depth }
}

internal fun <T : MemoryObject?, S : T?>
      LazyMemoryObjectTreeNode<*>.addChild(adapter: S, makeNode: (S) -> LazyMemoryObjectTreeNode<T>) {
  val child = makeNode(adapter)
  child.treeModel = this.treeModel
  this.add(child)
}