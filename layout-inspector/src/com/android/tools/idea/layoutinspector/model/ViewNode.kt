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
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.ComponentTreeEvent.PayloadType.SKP
import com.google.common.annotations.VisibleForTesting
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ui.UIUtil
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Image
import java.awt.Rectangle

// This must have the same value as WindowManager.FLAG_DIM_BEHIND
@VisibleForTesting
const val WINDOW_MANAGER_FLAG_DIM_BEHIND = 0x2

/**
 * A view node represents a view in the view hierarchy as seen on the device.
 *
 * @param drawId the View.getUniqueDrawingId which is also the id found in the skia image
 * @param qualifiedName the qualified class name of the view
 * @param x the left edge of the view from the device left edge
 * @param y the top edge of the view from the device top edge
 * @param viewId the id set by the developer in the View.id attribute
 * @param textValue the text value if present
 */
open class ViewNode(
  var drawId: Long,
  var qualifiedName: String,
  var layout: ResourceReference?,
  var x: Int,
  var y: Int,
  var width: Int,
  var height: Int,
  var viewId: ResourceReference?,
  var textValue: String,
  var layoutFlags: Int
) {
  val bounds: Rectangle
    get() = Rectangle(x, y, width, height)

  private var tagPointer: SmartPsiElementPointer<XmlTag>? = null

  val children = mutableListOf<ViewNode>()
  var parent: ViewNode? = null

  val parentSequence: Sequence<ViewNode>
    get() = generateSequence(this) { it.parent }

  // Views and images that will be drawn.
  // TODO: Figure out whether order of child nodes here and in [children] will always be the same.
  val drawChildren = mutableListOf<DrawViewNode>()

  // The type of image we received from the device.
  var imageType: LayoutInspectorProto.ComponentTreeEvent.PayloadType = SKP

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

  fun flatten(): Collection<ViewNode> {
    return children.flatMap { it.flatten() }.plus(this)
  }
}

/**
 * A node in the hierarchy used to paint the device view. This is separate from the basic hierarchy ([ViewNode.children]) since views
 * can do their own painting interleaved with painting their children, and we need to keep track of the order in which the operations
 * happen.
 */
sealed class DrawViewNode(val owner: ViewNode) {
  abstract fun paint(g2: Graphics2D, model: InspectorModel)
}

/**
 * A draw view corresponding directly to a ViewNode. Doesn't do any painting itself.
 */
class DrawViewChild(owner: ViewNode): DrawViewNode(owner) {
  override fun paint(g2: Graphics2D, model: InspectorModel) {}
}

/**
 * A draw view that paints an image. The [owner] should be the view that does the painting, and is also the "draw parent" of this node.
 */
class DrawViewImage(@VisibleForTesting val image: Image,
                    private val x: Int,
                    private val y: Int,
                    owner: ViewNode): DrawViewNode(owner) {
  override fun paint(g2: Graphics2D, model: InspectorModel) {
    val composite = g2.composite
    // Check hasSubImages, since it doesn't make sense to dim if we're only showing one image.
    if (model.selection != null && owner != model.selection && model.hasSubImages) {
      g2.composite = AlphaComposite.SrcOver.derive(0.6f)
    }
    UIUtil.drawImage(g2, image, x, y, null)
    g2.composite = composite
  }
}

/**
 * A draw view that draw a semi-transparent grey rectangle. Shown when a window has DIM_BEHIND set and is drawn over another window (e.g.
 * a dialog box).
 */
class Dimmer(val root: ViewNode): DrawViewNode(root) {
  override fun paint(g2: Graphics2D, model: InspectorModel) {
    if (root.width > 0 && root.height > 0) {
      val color = g2.color
      g2.color = Color(0.0f, 0.0f, 0.0f, 0.5f)
      g2.fillRect(0, 0, root.width, root.height)
      g2.color = color
    }
  }
}