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
package com.android.tools.idea.layoutinspector.model

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.layoutinspector.tree.TreeViewNode
import com.google.common.annotations.VisibleForTesting
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.xml.XmlTag
import org.jetbrains.annotations.TestOnly
import java.awt.Rectangle
import java.awt.Shape
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

// This must have the same value as WindowManager.FLAG_DIM_BEHIND
@VisibleForTesting
const val WINDOW_MANAGER_FLAG_DIM_BEHIND = 0x2

private val systemPackagePrefixes = setOf("android.", "androidx.", "com.android.", "com.google.android.")

/**
 * A view node represents a view in the view hierarchy as seen on the device.
 *
 * @param drawId the View.getUniqueDrawingId which is also the id found in the skia image
 * @param qualifiedName the qualified class name of the view
 * @param layout reference to the layout xml containing this view
 * @param layoutBounds the bounds used by android for layout. Always a rectangle.
 * x and y are the left and top edges of the view from the device left and top edge, ignoring post-layout transformations
 * @param renderBounds the actual bounds of this view as shown on the screen, including any post-layout transformations.
 * @param viewId the id set by the developer in the View.id attribute
 * @param textValue the text value if present
 * @param layoutFlags flags from WindowManager.LayoutParams
 */
open class ViewNode(
  var drawId: Long,
  var qualifiedName: String,
  var layout: ResourceReference?,
  var layoutBounds: Rectangle,
  var renderBounds: Shape,
  var viewId: ResourceReference?,
  var textValue: String,
  var layoutFlags: Int
) {
  @TestOnly
  constructor(drawId: Long,
              qualifiedName: String,
              layout: ResourceReference?,
              layoutBounds: Rectangle,
              viewId: ResourceReference?,
              textValue: String,
              layoutFlags: Int) : this(drawId, qualifiedName, layout, layoutBounds, layoutBounds, viewId, textValue, layoutFlags)

  /** constructor for synthetic nodes */
  constructor(qualifiedName: String): this(-1, qualifiedName, null, Rectangle(), Rectangle(), null, "", 0)

  @Suppress("LeakingThis")
  val treeNode = TreeViewNode(this)

  /** Returns true if this [ViewNode] is found in a layout in the framework or in a system layout from appcompat */
  open val isSystemNode: Boolean
    get() =
      (layout == null && systemPackagePrefixes.any { qualifiedName.startsWith(it) }) ||
      layout?.namespace == ResourceNamespace.ANDROID ||
      layout?.name?.startsWith("abc_") == true

  /** Returns true if this [ViewNode] has merged semantics */
  open val hasMergedSemantics: Boolean
    get() = false

  /** Returns true if this [ViewNode] has unmerged semantics */
  open val hasUnmergedSemantics: Boolean
    get() = false

  /** Returns true if this [ViewNode] represent an inlined composable function */
  open val isInlined: Boolean
    get() = false

  /**
   * Return the closest unfiltered node
   *
   * This will either be:
   * - the node itself
   * - the closest ancestor that is not filtered out of the component tree
   * - null
   */
  fun findClosestUnfilteredNode(treeSettings: TreeSettings): ViewNode? =
    if (treeSettings.hideSystemNodes) readAccess { parentSequence.firstOrNull { !it.isSystemNode } } else this

  /** Returns true if the node appears in the component tree. False if it currently filtered out */
  fun isInComponentTree(treeSettings: TreeSettings): Boolean =
    treeSettings.isInComponentTree(this)

  /** Returns true if the node represents a call from a parent node with a single call and it itself is making a single call */
  open fun isSingleCall(treeSettings: TreeSettings): Boolean = false

  /**
   *  The rectangular bounds of this node's transformed bounds plus the transitive bounds of all children.
   *  [calculateTransitiveBounds] must be called before accessing this, but that should be done automatically soon after creation.
   */
  lateinit var transitiveBounds: Rectangle
    private set

  private var tagPointer: SmartPsiElementPointer<XmlTag>? = null

  private val children = mutableListOf<ViewNode>()
  private var parent: ViewNode? = null

  /**
   * Create a sequence of parents starting with the current ViewNode
   */
  private val parentSequence: Sequence<ViewNode>
    get() = generateSequence(this) { it.parent }

  // Views and images that will be drawn.
  // The order here and in children can be different at least due to how compose->view transitions are grafted in.
  private val drawChildren = mutableListOf<DrawViewNode>()

  var tag: XmlTag?
    get() = tagPointer?.element
    set(value) {
      tagPointer = value?.let { SmartPointerManager.getInstance(value.project).createSmartPsiElementPointer(value) }
    }

  val unqualifiedName: String
    get() = qualifiedName.substringAfterLast('.')

  val isDimBehind: Boolean
    get() = (layoutFlags and WINDOW_MANAGER_FLAG_DIM_BEHIND) > 0

  /**
   * Create a sequence of the sub tree starting with the current ViewNode (Post-order, LRN, or order doesn't matter)
   */
  private fun flatten(): Sequence<ViewNode> {
    return children.asSequence().flatMap { it.flatten() }.plus(this)
  }

  @TestOnly
  fun flattenedList(): List<ViewNode> =
    readAccess { flatten().toList() }

  /**
   * Create a sequence of the sub tree starting with the current ViewNode (Pre-order, LRN)
   */
  private fun preOrderFlatten(): Sequence<ViewNode> {
    return sequenceOf(this).plus(children.asSequence().flatMap { it.preOrderFlatten() })
  }

  /**
   * Calculate the transitive bounds for all nodes under the given [root]. This should be called once after the
   * ViewNode tree is built.
   */
  fun calculateTransitiveBounds() {
    readAccess {
      flatten().forEach {
        it.transitiveBounds = it.children.map(ViewNode::transitiveBounds).plus(it.renderBounds.bounds)
          .reduce { r1, r2 -> r1.union(r2) }
      }
    }
  }

  /**
   * Interface used for traversing the [ViewNode] tree with a read lock. See [readAccess].
   * This interface provides a limited access view of a [ViewNode],
   * so that users of [readAccess] are limited in what methods of [ViewNode] they can invoke.
   */
  interface ReadAccess {
    val ViewNode.children: MutableList<ViewNode> get() = children
    val ViewNode.parent: ViewNode? get() = parent
    val ViewNode.drawChildren: MutableList<DrawViewNode> get() = drawChildren
    val ViewNode.parentSequence: Sequence<ViewNode> get() = parentSequence
    fun ViewNode.flatten(): Sequence<ViewNode> = flatten()
    fun ViewNode.preOrderFlatten(): Sequence<ViewNode> = preOrderFlatten()
  }

  /**
   * Interface used for modifying a [ViewNode] with a write lock. See [writeAccess].
   */
  interface WriteAccess : ReadAccess {
    override var ViewNode.parent: ViewNode?
      get() = parent
      set(value) { parent = value }
  }

  companion object {
    private val lock = ReentrantReadWriteLock()
    private val reader = object : ReadAccess {}
    private val writer = object : WriteAccess {}

    /**
     * Allows to safely perform read actions on the [ViewNode].
     * Preventing other threads to change the tree structure while we are reading it.
     */
    fun <T> readAccess(operation: ReadAccess.() -> T): T =
      lock.read {
        reader.operation()
      }

    /**
     * Allows to safely perform write actions on the [ViewNode].
     * Preventing multiple threads to change the tree structure at the same time.
     */
    fun <T> writeAccess(operation: WriteAccess.() -> T) =
      lock.write {
        writer.operation()
      }
  }
}