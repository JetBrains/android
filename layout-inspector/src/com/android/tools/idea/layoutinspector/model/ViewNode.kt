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

import com.android.ide.common.rendering.api.ResourceReference
import com.google.common.annotations.VisibleForTesting
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.xml.XmlTag
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
  /** The bounds used by android for layout. Always a rectangle. */
  val layoutBounds: Rectangle
    get() = Rectangle(x, y, width, height)

  private var _transformedBounds = bounds

  val transformedBounds: Shape
    get() = _transformedBounds ?: layoutBounds

  fun setTransformedBounds(bounds: Shape?) {
    _transformedBounds = bounds
  }

  private var tagPointer: SmartPsiElementPointer<XmlTag>? = null

  val children = mutableListOf<ViewNode>()
  var parent: ViewNode? = null

  val parentSequence: Sequence<ViewNode>
    get() = generateSequence(this) { it.parent }

  // Views and images that will be drawn.
  // TODO: Figure out whether order of child nodes here and in [children] will always be the same.
  private val drawChildren = mutableListOf<DrawViewNode>()
    get(): MutableList<DrawViewNode> {

      return field
    }

  var tag: XmlTag?
    get() = tagPointer?.element
    set(value) {
      tagPointer = value?.let { SmartPointerManager.getInstance(value.project).createSmartPsiElementPointer(value) }
    }

  val unqualifiedName: String
    get() = qualifiedName.substringAfterLast('.')

  // TODO: move to draw node
  var visible = true

  val isDimBehind: Boolean
    get() = (layoutFlags and WINDOW_MANAGER_FLAG_DIM_BEHIND) > 0

  fun flatten(): Sequence<ViewNode> {
    return children.asSequence().flatMap { it.flatten() }.plus(this)
  }

  companion object {
    private val lock = ReentrantReadWriteLock()

    fun <T> readDrawChildren(fn: (ViewNode.() -> List<DrawViewNode>) -> T): T =
      lock.read {
        fn(ViewNode::drawChildren)
      }

    fun writeDrawChildren(fn: (ViewNode.() -> MutableList<DrawViewNode>) -> Unit) =
      lock.write {
        fn(ViewNode::drawChildren)
      }
  }
}