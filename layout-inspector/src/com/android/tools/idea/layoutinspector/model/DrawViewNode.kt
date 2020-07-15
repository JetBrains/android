/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.BasicStroke.*
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Image
import java.awt.geom.GeneralPath
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D

private const val NORMAL_BORDER_THICKNESS = 1f
private const val EMPHASIZED_BORDER_THICKNESS = 5f
private const val EMPHASIZED_BORDER_OUTLINE_THICKNESS = 7f
private const val LABEL_FONT_SIZE = 30f
private val DASH = floatArrayOf(20f, 20f)

private val EMPHASIZED_LINE_COLOR = Color(106, 161, 211)
private val EMPHASIZED_LINE_STROKE = BasicStroke(EMPHASIZED_BORDER_THICKNESS)
private val EMPHASIZED_IMAGE_LINE_STROKE = BasicStroke(EMPHASIZED_BORDER_THICKNESS, CAP_BUTT, JOIN_MITER, 10.0f, DASH, 0f)
private val EMPHASIZED_LINE_OUTLINE_STROKE = BasicStroke(EMPHASIZED_BORDER_OUTLINE_THICKNESS)
private val EMPHASIZED_IMAGE_LINE_OUTLINE_STROKE = BasicStroke(EMPHASIZED_BORDER_OUTLINE_THICKNESS, CAP_BUTT, JOIN_MITER, 10.0f, DASH, 0f)
private val SELECTED_LINE_COLOR = Color(24, 134, 247)
private val SELECTED_LINE_STROKE = EMPHASIZED_LINE_STROKE
private val SELECTED_IMAGE_LINE_STROKE = BasicStroke(EMPHASIZED_BORDER_THICKNESS, CAP_BUTT, JOIN_MITER, 10.0f, DASH, 0f)
private val NORMAL_LINE_COLOR = JBColor(Gray.get(128, 128), Gray.get(212, 128))
private val NORMAL_LINE_STROKE = BasicStroke(NORMAL_BORDER_THICKNESS)
private val NORMAL_IMAGE_LINE_STROKE = BasicStroke(NORMAL_BORDER_THICKNESS, CAP_BUTT, JOIN_MITER, 10.0f, DASH, 0f)
private val EMPHASIZED_LINE_OUTLINE_COLOR = Color.white

/**
 * A node in the hierarchy used to paint the device view. This is separate from the basic hierarchy ([ViewNode.children]) since views
 * can do their own painting interleaved with painting their children, and we need to keep track of the order in which the operations
 * happen.
 */
sealed class DrawViewNode(var owner: ViewNode) {
  // Children at the start of the child list that have canCollapse = true will be drawn as part of the parent rather than as separate nodes.
  abstract val canCollapse: Boolean

  abstract fun paint(g2: Graphics2D, model: InspectorModel)
  abstract fun paintBorder(g2: Graphics2D, isSelected: Boolean, isHovered: Boolean, drawLabel: Boolean)
}

/**
 * A draw view corresponding directly to a ViewNode. Doesn't do any painting itself.
 */
class DrawViewChild(owner: ViewNode) : DrawViewNode(owner) {
  override val canCollapse = false

  override fun paint(g2: Graphics2D, model: InspectorModel) {}
  override fun paintBorder(g2: Graphics2D, isSelected: Boolean, isHovered: Boolean, drawLabel: Boolean) {
    if (isSelected || isHovered) {
      g2.color = EMPHASIZED_LINE_OUTLINE_COLOR
      g2.stroke = EMPHASIZED_LINE_OUTLINE_STROKE
      g2.draw(owner.bounds)
    }
    when {
      isSelected -> {
        g2.color = SELECTED_LINE_COLOR
        g2.stroke = SELECTED_LINE_STROKE
      }
      isHovered -> {
        g2.color = EMPHASIZED_LINE_COLOR
        g2.stroke = EMPHASIZED_LINE_STROKE
      }
      else -> {
        g2.color = NORMAL_LINE_COLOR
        g2.stroke = NORMAL_LINE_STROKE
      }
    }
    g2.draw(owner.bounds)

    if (isSelected && drawLabel) {
      g2.font = g2.font.deriveFont(JBUIScale.scale(LABEL_FONT_SIZE))
      val fontMetrics = g2.fontMetrics
      val textWidth = fontMetrics.stringWidth(owner.unqualifiedName)
      val height = (fontMetrics.maxAscent + fontMetrics.maxDescent).toFloat()
      val border = height * 0.3f
      g2.color = EMPHASIZED_LINE_OUTLINE_COLOR
      g2.stroke = EMPHASIZED_LINE_OUTLINE_STROKE
      val outlinePath = GeneralPath(Path2D.WIND_NON_ZERO)
      val x = owner.x.toFloat()
      val y = owner.y.toFloat()
      val ownerWidth = owner.width.toFloat()
      outlinePath.moveTo(x, y - EMPHASIZED_BORDER_OUTLINE_THICKNESS)
      outlinePath.lineTo(x, owner.y - height - border + EMPHASIZED_BORDER_THICKNESS)
      outlinePath.lineTo(x + textWidth + 2f * border - EMPHASIZED_BORDER_THICKNESS, y - height - border + EMPHASIZED_BORDER_THICKNESS)
      outlinePath.lineTo(x + textWidth + 2f * border - EMPHASIZED_BORDER_THICKNESS, y - EMPHASIZED_BORDER_OUTLINE_THICKNESS)
      if (textWidth + 2f * border - EMPHASIZED_BORDER_THICKNESS > owner.width) {
        outlinePath.lineTo(x + textWidth + 2f * border - EMPHASIZED_BORDER_THICKNESS, y)
        outlinePath.lineTo(x + ownerWidth + EMPHASIZED_BORDER_OUTLINE_THICKNESS, y)
      }
      g2.draw(outlinePath)
      g2.color = SELECTED_LINE_COLOR
      g2.fill(Rectangle2D.Float(x - EMPHASIZED_BORDER_THICKNESS / 2f,
                                y - height - border + EMPHASIZED_BORDER_THICKNESS / 2f,
                                textWidth + 2f * border, height + border))
      g2.color = Color.WHITE
      g2.drawString(owner.unqualifiedName, x + border, y - border)
    }
  }
}

/**
 * A draw view that paints an image. The [owner] should be the view that does the painting, and is also the "draw parent" of this node.
 */
class DrawViewImage(@VisibleForTesting val image: Image,
                    private val x: Int,
                    private val y: Int,
                    owner: ViewNode) : DrawViewNode(owner) {
  override val canCollapse = true

  override fun paint(g2: Graphics2D, model: InspectorModel) {
    val composite = g2.composite
    // Check hasSubImages, since it doesn't make sense to dim if we're only showing one image.
    if (model.selection != null && owner != model.selection && model.hasSubImages) {
      g2.composite = AlphaComposite.SrcOver.derive(0.6f)
    }
    UIUtil.drawImage(g2, image, x, y, null)
    g2.composite = composite
  }

  override fun paintBorder(g2: Graphics2D, isSelected: Boolean, isHovered: Boolean, drawLabel: Boolean) {
    if (isSelected || isHovered) {
      g2.color = EMPHASIZED_LINE_OUTLINE_COLOR
      g2.stroke = EMPHASIZED_IMAGE_LINE_OUTLINE_STROKE
      g2.draw(owner.bounds)
    }
    when {
      isSelected -> {
        g2.color = SELECTED_LINE_COLOR
        g2.stroke = SELECTED_IMAGE_LINE_STROKE
      }
      isHovered -> {
        g2.color = EMPHASIZED_LINE_COLOR
        g2.stroke = EMPHASIZED_IMAGE_LINE_STROKE
      }
      else -> {
        g2.color = NORMAL_LINE_COLOR
        g2.stroke = NORMAL_IMAGE_LINE_STROKE
      }
    }
    g2.draw(owner.bounds)
  }
}

/**
 * A draw view that draw a semi-transparent grey rectangle. Shown when a window has DIM_BEHIND set and is drawn over another window (e.g.
 * a dialog box).
 */
class Dimmer(val root: ViewNode) : DrawViewNode(root) {
  override val canCollapse = false

  override fun paint(g2: Graphics2D, model: InspectorModel) {
    if (root.width > 0 && root.height > 0) {
      val color = g2.color
      g2.color = Color(0.2f, 0.2f, 0.2f, 0.5f)
      g2.fillRect(0, 0, root.width, root.height)
      g2.color = color
    }
  }

  override fun paintBorder(g2: Graphics2D, isSelected: Boolean, isHovered: Boolean, drawLabel: Boolean) {
    if (root.width > 0 && root.height > 0) {
      g2.color = NORMAL_LINE_COLOR
      g2.stroke = NORMAL_IMAGE_LINE_STROKE
      g2.drawRect(0, 0, root.width, root.height)
    }
  }
}