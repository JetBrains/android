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

/**
 * A view node represents a view in the view hierarchy as seen on the device.
 *
 * @param drawId the View.getUniqueDrawingId which is also the id found in the skia image
 * @param qualifiedName the qualified class name of the view
 * @param layout reference to the layout xml containing this view
 * @param x the left edge of the view from the device left edge, ignoring any post-layout transformations
 * @param y the top edge of the view from the device top edge, ignoring any post-layout transformations
 * @param width the width of this view, ignoring any post-layout transformations
 * @param height the height of this view, ignoring any post-layout transformations
 * @param bounds the actual bounds of this view as shown on the screen, including any post-layout transformations.
 * @param viewId the id set by the developer in the View.id attribute
 * @param textValue the text value if present
 * @param layoutFlags flags from WindowManager.LayoutParams
 */
open class ViewNode(
  var drawId: Long,
  var qualifiedName: String,
  var layout: ResourceReference?,
  var x: Int,
  var y: Int,
  var width: Int,
  var height: Int,
  bounds: Shape?,
  var viewId: ResourceReference?,
  var textValue: String,
  var layoutFlags: Int
) {
  /** constructor for synthetic nodes */
  constructor(qualifiedName: String): this(-1, qualifiedName, null, 0, 0, 0, 0, null, null, "", 0)

  /** The bounds used by android for layout. Always a rectangle. */
  val layoutBounds: Rectangle
    get() = Rectangle(x, y, width, height)

  @Suppress("LeakingThis")
  val treeNode = TreeViewNode(this)

  /** Returns true if this [ViewNode] is found in a layout in the framework or in a system layout from appcompat */
  open val isSystemNode: Boolean
    get() =
      layout == null ||
      layout?.namespace == ResourceNamespace.ANDROID ||
      layout?.name?.startsWith("abc_") == true

  /** Returns true if this [ViewNode] has merged semantics */
  open val hasMergedSemantics: Boolean
    get() = false

  /** Returns true if this [ViewNode] has unmerged semantics */
  open val hasUnmergedSemantics: Boolean
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

  private var _transformedBounds = bounds

  val transformedBounds: Shape
    get() = _transformedBounds ?: layoutBounds

  fun setTransformedBounds(bounds: Shape?) {
    _transformedBounds = bounds
  }

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
        it.transitiveBounds = it.children.map(ViewNode::transitiveBounds).plus(it.transformedBounds.bounds)
          .reduce { r1, r2 -> r1.union(r2) }
      }
    }
  }

  /**
   * For reading from a [ViewNode] with a read lock. See [readAccess].
   */
  interface ReadAccess {
    val ViewNode.children: List<ViewNode>
    val ViewNode.drawChildren: List<DrawViewNode>
    val ViewNode.parent: ViewNode?
    val ViewNode.parentSequence: Sequence<ViewNode>
    fun ViewNode.flatten(): Sequence<ViewNode>
    fun ViewNode.preOrderFlatten(): Sequence<ViewNode>
  }

  /**
   * For modifying a [ViewNode] with a write lock. See [writeAccess].
   */
  interface WriteAccess {
    val ViewNode.children: MutableList<ViewNode>
    val ViewNode.drawChildren: MutableList<DrawViewNode>
    var ViewNode.parent: ViewNode?
    val ViewNode.parentSequence: Sequence<ViewNode>
    fun ViewNode.flatten(): Sequence<ViewNode>
    fun ViewNode.preOrderFlatten(): Sequence<ViewNode>
  }

  companion object {
    private val lock = ReentrantReadWriteLock()
    private val reader = object : ReadAccess {
      override val ViewNode.children get() = children
      override val ViewNode.drawChildren get() = drawChildren
      override val ViewNode.parent get() = parent
      override val ViewNode.parentSequence get() = parentSequence
      override fun ViewNode.flatten() = flatten()
      override fun ViewNode.preOrderFlatten() = preOrderFlatten()
    }
    private val writer = object : WriteAccess {
      override val ViewNode.children get() = children
      override val ViewNode.drawChildren get() = drawChildren
      override var ViewNode.parent
        get() = parent
        set(value) { this.parent = value }
      override val ViewNode.parentSequence get() = parentSequence
      override fun ViewNode.flatten() = flatten()
      override fun ViewNode.preOrderFlatten() = preOrderFlatten()
    }

    fun <T> readAccess(operation: ReadAccess.() -> T): T =
      lock.read {
        reader.operation()
      }

    fun <T> writeAccess(operation: WriteAccess.() -> T) =
      lock.write {
        writer.operation()
      }
  }
}
