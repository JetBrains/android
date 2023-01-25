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
package com.android.tools.componenttree.api

import com.android.tools.componenttree.treetable.ViewTreeCellRenderer
import javax.swing.Icon
import javax.swing.tree.TreeCellRenderer

/**
 * [NodeType] used to represent an item with a tag and may have id and text attributes.
 *
 * Usually used for an Android View class.
 */
abstract class ViewNodeType<T> : NodeType<T> {

  /**
   * Returns the qualified tag name of [node].
   */
  abstract fun tagNameOf(node: T): String

  /**
   * Returns the id of [node] if present.
   */
  abstract fun idOf(node: T): String?

  /**
   * The text attribute value lookup of the node if applicable.
   */
  abstract fun textValueOf(node: T): String?

  /**
   * The icon representing the component type, can optionally include state information.
   */
  abstract fun iconOf(node: T): Icon?

  abstract fun isEnabled(node: T): Boolean

  override fun createRenderer(): TreeCellRenderer = ViewTreeCellRenderer(this)

  override fun toSearchString(node: T): String =
    ViewTreeCellRenderer.computeSearchString(this, node)

  /**
   * If true the renderer will display a faint representation of the node
   */
  abstract fun isDeEmphasized(node: T): Boolean
}
