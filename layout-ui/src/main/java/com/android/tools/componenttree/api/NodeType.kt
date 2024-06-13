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

import java.awt.Image
import java.awt.datatransfer.Transferable
import javax.swing.tree.TreeCellRenderer

/** A node type with access to various information. */
interface NodeType<T> {
  /** The class of the node. The actual node can be a derived type. */
  val clazz: Class<T>

  /** Returns the parent of [node], which may be of a different node type. */
  fun parentOf(node: T): Any?

  /** Returns the children of [node], which may be of a different node types. */
  fun childrenOf(node: T): List<*>

  /** Returns a string used for speed search based on [node]. */
  fun toSearchString(node: T): String

  /**
   * Create a cell renderer used for this node type.
   *
   * The renderer will be recreated during a LaF change.
   */
  fun createRenderer(): TreeCellRenderer

  // region Drag and Drop Support

  /**
   * Return true if this [node] can accept [data] being inserted into [node].
   *
   * Note: the implementation must return false if [data] refers to a node containing [node].
   */
  fun canInsert(node: T, data: Transferable): Boolean = false

  /**
   * Insert [data] into [node] either before [before] or at the end if [before] is null.
   *
   * The [isMove] specifies if this is a move or copy operation. This is intended as a hint for undo
   * messages. If the origin of the items being dragged is the component tree; then the items will
   * be supplied in [draggedFromTree]. If the origin is not the component tree the [draggedFromTree]
   * will be empty. Return true if the insert was successful.
   */
  fun insert(
    node: T,
    data: Transferable,
    before: Any? = null,
    isMove: Boolean,
    draggedFromTree: List<Any>,
  ): Boolean = false

  /** Delete this [node] after a successful DnD move operation. */
  fun delete(node: T) {}

  /** Create a [Transferable] for [node]. */
  fun createTransferable(node: T): Transferable? = null

  /** Create a drag [Image] for [node]. */
  fun createDragImage(node: T): Image? = null

  // endregion
}
