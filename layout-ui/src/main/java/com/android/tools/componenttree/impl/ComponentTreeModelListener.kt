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
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.componenttree.impl

import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener

interface ComponentTreeModelListener : TreeModelListener {

  /**
   * Invoked after the tree the tree root has changed.
   */
  fun treeChanged(event: TreeModelEvent)
}

abstract class ComponentTreeModelAdapter : ComponentTreeModelListener {
  override fun treeChanged(event: TreeModelEvent) {}

  override fun treeNodesInserted(event: TreeModelEvent?) {}

  override fun treeStructureChanged(e: TreeModelEvent?) {}

  override fun treeNodesChanged(e: TreeModelEvent?) {}

  override fun treeNodesRemoved(e: TreeModelEvent?) {}
}