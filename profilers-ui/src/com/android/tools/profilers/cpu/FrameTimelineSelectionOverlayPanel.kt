/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.common.missedDeadlineJank
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.MultiSelectionModel
import com.android.tools.adtui.model.Range
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameTimelineEvent
import com.intellij.ui.JBColor
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.OverlayLayout
import kotlin.math.max
import kotlin.math.min


object FrameTimelineSelectionOverlayPanel {
  @JvmStatic @JvmOverloads
  fun of(content: JComponent, captureRange: Range, selection: MultiSelectionModel<*>,
         grayOut: GrayOutMode, deadLineBar: Boolean, text: String = ""): JComponent =
    object: JPanel() {
      override fun isOptimizedDrawingEnabled() = false
    }.apply {
      layout = OverlayLayout(this)
      isOpaque = false
      content.isOpaque = false
      add(overlay(captureRange, selection, grayOut, deadLineBar, text))
      add(content)
      val handler = object : MouseListener, MouseMotionListener {
        override fun mouseClicked(e: MouseEvent) = content.dispatchEvent(e)
        override fun mousePressed(e: MouseEvent) = content.dispatchEvent(e)
        override fun mouseReleased(e: MouseEvent) = content.dispatchEvent(e)
        override fun mouseEntered(e: MouseEvent) = content.dispatchEvent(e)
        override fun mouseExited(e: MouseEvent) = content.dispatchEvent(e)
        override fun mouseDragged(e: MouseEvent) = content.dispatchEvent(e)
        override fun mouseMoved(e: MouseEvent) = content.dispatchEvent(e)
      }
      addMouseListener(handler)
      addMouseMotionListener(handler)
    }

  private fun overlay(captureRange: Range, selection: MultiSelectionModel<*>,
                      grayOutMode: GrayOutMode, deadLineBar: Boolean, text: String) = object : JComponent() {
    val observer = AspectObserver()
    init {
      selection.addDependency(observer)
        .onChange(MultiSelectionModel.Aspect.ACTIVE_SELECTION_CHANGED, ::repaint)
        .onChange(MultiSelectionModel.Aspect.SELECTIONS_CHANGED, ::repaint)
    }
    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      (selection.activeSelectionKey as? AndroidFrameTimelineEvent)?.let { event ->
        when (grayOutMode) {
          GrayOutMode.All -> g.grayOutPixels(0, width)
          is GrayOutMode.Outside -> {
            val highlightedRange = grayOutMode.getRangeForActiveEvent(event)
            val start = highlightedRange.min
            val end = highlightedRange.max
            if (start > captureRange.min || end < captureRange.max) {
              g.grayOut(captureRange.min, max(captureRange.min, start))
              g.grayOut(min(captureRange.max, end), captureRange.max)
            }
          }
          GrayOutMode.None -> { }
        }
        if (deadLineBar && event.isActionableJank) {
          val expectedEnd = event.expectedEndUs
          if (expectedEnd < event.actualEndUs) {
            with (g as Graphics2D) {
              val x = valueToPixelCoord(expectedEnd.toDouble())
              color = missedDeadlineJank
              stroke = DASHED_LINE_STROKE
              drawLine(x, 0, x, height)

              if (text.isNotEmpty()) {
                setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val oldFont = font
                font = font.deriveFont(10f)
                drawString(text, x.toFloat() + 4, (height - fontMetrics.height) * .5f + fontMetrics.ascent.toFloat())
                font = oldFont
              }
            }
          }
        }
      }
    }

    private fun Graphics.grayOut(left: Double, right: Double) =
      grayOutPixels(valueToPixelCoord(left), valueToPixelCoord(right))

    private fun Graphics.grayOutPixels(left: Int, right: Int) {
      color = TRANSLUCENT_GRAY
      fillRect(left, 0, right - left, height)
    }

    private fun valueToPixelCoord(value: Double) = ((value - captureRange.min) / captureRange.length * width).toInt()
  }

  sealed class GrayOutMode {
    object All: GrayOutMode()
    // Only gray out outside the given range for the active frame
    class Outside(val getRangeForActiveEvent: (AndroidFrameTimelineEvent) -> Range): GrayOutMode()
    object None: GrayOutMode()
  }
}

private val TRANSLUCENT_GRAY = JBColor(Color(.5f, .5f, .5f, .5f), Color(.5f, .5f, .5f, .5f))
private val DASHED_LINE_STROKE = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(5f,2f), 0f)