/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import java.util.Vector
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode

open class FilterableTreeNode : DefaultMutableTreeNode() {
  private var invisibleNodes: List<TreeNode> = listOf()
  val allChildren: Sequence<TreeNode>
    get() = sequence {
      // In JDK 8 DefaultMutableTreeNode.children() returns a raw Vector but as of JDK 11 the generic type matches
      // and this assignment is no longer unchecked.
      @Suppress("UNCHECKED_CAST") // In JDK 11 the cast is no longer needed.
      children?.let { yieldAll(it as Vector<TreeNode>) }
      yieldAll(invisibleNodes)
    }

  /**
   * Applies a filter to show or hide rows.
   *
   * @param filter a predicate which returns false for an item to be hidden
   */
  fun applyFilter(filter: (Any) -> Boolean) {
    if (children == null) {
      return
    }
    children.addAll(invisibleNodes)
    children.forEach {
      if (it is FilterableTreeNode) {
        it.applyFilter(filter)
      }
    }
    // In JDK 8 DefaultMutableTreeNode.children() returns a raw Vector but as of JDK 11 the generic type matches
    // and this assignment is no longer unchecked.
    @Suppress("UNCHECKED_CAST") // In JDK 11 the cast is no longer needed.
    invisibleNodes = children.filterNot(filter) as List<TreeNode>
    children.retainAll(filter)
  }
}