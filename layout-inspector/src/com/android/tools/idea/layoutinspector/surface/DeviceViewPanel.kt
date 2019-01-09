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
package com.android.tools.idea.layoutinspector.surface

import com.android.tools.idea.layoutinspector.model.InspectorView
import com.android.tools.idea.layoutinspector.sampledata.chromeSampleData
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JPanel
import kotlin.math.cos
import kotlin.math.sin

private const val LAYER_SPACING = 150

/**
 * Panel that shows the device screen in the layout inspector.
 */
class DeviceViewPanel : JPanel() {
  var showBorders = false
  var data = chromeSampleData

  init {
    addMouseWheelListener { e ->
      angle += e.preciseWheelRotation * 0.01
      repaint()
    }
  }

  private val HQ_RENDERING_HINTS = mapOf(
    RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON,
    RenderingHints.KEY_RENDERING to RenderingHints.VALUE_RENDER_QUALITY,
    RenderingHints.KEY_INTERPOLATION to RenderingHints.VALUE_INTERPOLATION_BILINEAR,
    RenderingHints.KEY_STROKE_CONTROL to RenderingHints.VALUE_STROKE_PURE
  )

  private var angle = 0.0

  override fun paint(g: Graphics) {
    (g as? Graphics2D)?.setRenderingHints(HQ_RENDERING_HINTS)
    g.color = Color.LIGHT_GRAY
    g.fillRect(0, 0, width, height)
    (g as? Graphics2D)?.translate(20, 0)
    (g as? Graphics2D)?.scale(.5, .5)
    draw(data.root, g, 0)
  }

  private fun draw(view: InspectorView, g: Graphics, depth: Int) {
    val g2 = g.create() as Graphics2D
    val bufferedImage = view.image

    g2.translate((sin(angle) * depth * LAYER_SPACING).toInt(), 0)
    g2.scale(cos(angle), 1.0)
    if (bufferedImage != null) {
      g2.drawImage(bufferedImage, view.x, view.y, null)
    }
    if (showBorders) {
      g2.color = Color.BLUE
      g2.drawRect(view.x, view.y, view.width, view.height)
      g2.color = Color.BLACK
      g2.font = g2.font.deriveFont(20f)
      g2.drawString(view.type, view.x + 5, view.y + 25)
    }
    view.children.forEach { draw(it, g, depth + 1) }
  }

}