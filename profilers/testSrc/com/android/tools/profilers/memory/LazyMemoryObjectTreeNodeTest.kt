/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.profilers.memory.adapters.MemoryObject
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import javax.swing.tree.DefaultTreeModel

class LazyMemoryObjectTreeNodeTest {

  @Test
  fun childrenNodesSizeLessThanOnePage() {
    val rootNode = createRoot(50)
    rootNode.expandNode()
    assertThat(rootNode.myChildren.size).isEqualTo(50)
    assertThat(rootNode.childCount).isEqualTo(50)
    // Last node does nothing
    val lastNode = rootNode.getChildAt(49) as MemoryObjectTreeNode<MemoryObject>
    lastNode.select()
    assertThat(rootNode.childCount).isEqualTo(50)
  }

  @Test
  fun childrenNodesSizeEqualToOnePageMax() {
    val rootNode = createRoot(100)
    rootNode.expandNode()
    assertThat(rootNode.myChildren.size).isEqualTo(100)
    assertThat(rootNode.childCount).isEqualTo(100)
    // Last node does nothing
    val lastNode = rootNode.getChildAt(99) as MemoryObjectTreeNode<MemoryObject>
    lastNode.select()
    assertThat(rootNode.childCount).isEqualTo(100)
  }

  @Test
  fun childrenNodesSizeEqualToTwoPageMax() {
    val rootNode = createRoot(200)
    rootNode.expandNode()
    assertThat(rootNode.myChildren.size).isEqualTo(200)
    assertThat(rootNode.childCount).isEqualTo(101)
    val lastNode = rootNode.getChildAt(100) as MemoryObjectTreeNode<MemoryObject>
    lastNode.select()
    assertThat(rootNode.childCount).isEqualTo(200)
  }

  private fun createRoot(childrenSize: Int): LazyMemoryObjectTreeNode<MemoryObject> {
    val rootNode = object : LazyMemoryObjectTreeNode<MemoryObject>(MemoryObject { "root" }, true) {
      private var nodesAdded = false

      override fun expandNode() {
        if (!nodesAdded) {
          nodesAdded = true
          for (i in 0 until childrenSize) {
            add(MemoryObjectTreeNode(MemoryObject { "node$i" }))
          }
        }
      }

      override fun computeChildrenCount(): Int {
        return childrenSize
      }
    }
    rootNode.treeModel = DefaultTreeModel(rootNode)
    return rootNode
  }
}
