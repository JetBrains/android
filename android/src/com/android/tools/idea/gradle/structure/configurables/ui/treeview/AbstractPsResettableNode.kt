/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui.treeview

import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.model.PsModel
import com.android.tools.idea.gradle.structure.model.android.PsCollectionBase
import com.intellij.ui.treeStructure.SimpleNode

abstract class AbstractPsResettableNode<K, N : SimpleNode, M : PsModel> : AbstractPsModelNode<M> {
  protected constructor(uiSettings: PsUISettings) : super(uiSettings)
  protected constructor(parent: AbstractPsNode, uiSettings: PsUISettings) : super(parent, uiSettings)

  private var myChildren: Array<SimpleNode>? = null

  init {
    autoExpandNode = true
  }

  protected abstract fun getKeys(from: Unit): Set<K>
  protected abstract fun create(key: K): N
  protected abstract fun update(key: K, node: N)

  fun reset() {
    collection.refresh()
    myChildren = null
  }

  final override fun getChildren(): Array<SimpleNode> =
    myChildren ?: let {
      collection.refresh()
      val result = collection.items.toTypedArray<SimpleNode>()
      myChildren = result
      result
    }

  private val collection: PsCollectionBase<out N, K, Unit> =
    object : PsCollectionBase<N, K, Unit>(Unit) {
      override fun getKeys(from: Unit): Set<K> = this@AbstractPsResettableNode.getKeys(from)
      override fun create(key: K): N = this@AbstractPsResettableNode.create(key)
      override fun update(key: K, model: N) = this@AbstractPsResettableNode.update(key, model)
    }
}
