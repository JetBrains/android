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
package com.android.tools.profilers.memory

import com.android.tools.adtui.chart.linechart.AbstractDurationDataRenderer
import com.android.tools.adtui.chart.linechart.LineChart
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.common.linkForeground
import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.adtui.common.selectionOverlayBackground
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.DurationDataModel
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.RangedContinuousSeries
import com.android.tools.adtui.model.formatter.TimeAxisFormatter
import com.android.tools.profilers.ProfilerColors.MEMORY_HEAP_DUMP_BG
import com.android.tools.profilers.memory.adapters.CaptureObject
import com.intellij.ui.JBColor
import it.unimi.dsi.fastutil.doubles.DoubleArrayList
import java.awt.BasicStroke
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.util.function.Consumer
import javax.swing.JLabel

class HeapDumpRenderer(private val model: DurationDataModel<CaptureDurationData<out CaptureObject>>,
                       private val viewRange: Range)
      : AspectObserver(), AbstractDurationDataRenderer {
  private val startsCache = DoubleArrayList()
  private val durationsCache = DoubleArrayList()
  private val labelCache = mutableListOf<JLabel>()
  private var mousePosition = Point()
  private val heapDumpHoveredListener = mutableListOf<Consumer<Boolean>>()

  var isMouseOverHeapDump = false
    private set(mouseOverHeapDump) {
      field = mouseOverHeapDump
      heapDumpHoveredListener.forEach { it.accept(mouseOverHeapDump) }
    }

  init {
    model.addDependency(this).onChange(DurationDataModel.Aspect.DURATION_DATA, ::modelChanged)
  }

  fun addHeapDumpHoverListener(listener: Consumer<Boolean>) {
    listener.accept(isMouseOverHeapDump)
    heapDumpHoveredListener.add(listener)
  }

  override fun renderLines(lineChart: LineChart,
                           g2d: Graphics2D,
                           transformedPaths: MutableList<Path2D>,
                           transformedSeries: MutableList<RangedContinuousSeries>) {
    val origClip = g2d.clip
    val origX = origClip.bounds.x.toDouble()
    val origY = origClip.bounds.y
    val origWidth = origClip.bounds.width
    val origHeight = origClip.bounds.height

    val dim = lineChart.size
    val dimWidth = dim.width
    val dimHeight = dim.height

    val height = Math.min(origHeight, dimHeight).toDouble()
    val clipRect = Rectangle2D.Float()

    for (i in 0 until startsCache.size) {
      val xStart = startsCache.getDouble(i)
      val xLen = durationsCache.getDouble(i)
      val scaledXStart = xStart * dimWidth
      val scaledDuration = xLen * dimWidth
      val newX = Math.max(scaledXStart, origX)
      clipRect.setRect(newX, origY.toDouble(),
                       Math.min(scaledDuration + scaledXStart - newX, origX + origWidth - newX),
                       height)

      // Draw blank box for heap dump period to indicate the lack of data,
      // as well as other visual hints that the heap dump is at an instance in time
      g2d.apply {
        color = MEMORY_HEAP_DUMP_BG
        clip = clipRect
        fill(clipRect)

        val left = clipRect.x.toInt()
        val right = (clipRect.x + clipRect.width).toInt()
        val height = clipRect.height.toInt()
        val top = clipRect.y.toInt()
        val bottom = clipRect.y.toInt() + height
        val midY = top + height / 2
        color =
          if (scaledXStart < mousePosition.x && mousePosition.x < scaledXStart + scaledDuration)
            selectionOverlayBackground
          else primaryContentBackground
        fill(clipRect)
        color = linkForeground
        stroke = BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(8f, 8f), 0f)
        drawLine(left, midY, right, midY)
        stroke = BasicStroke(4f)
        drawLine(left, top, left, bottom)
        drawLine(right, top, right, bottom)
      }

      // Draw the label
      labelCache[i].let { label ->
        g2d.color = labelBgColor
        g2d.fillRect(clipRect.x.toInt(), (clipRect.height - 2 * labelYPadding - label.height).toInt(),
                     (label.width + 2 * labelXPadding).toInt(), (label.height + 2 * labelYPadding).toInt())
        val labelXOffset = scaledXStart + labelXPadding
        val labelYOffset = (clipRect.height - label.height) - labelYPadding
        g2d.translate(labelXOffset, labelYOffset)
        label.paint(g2d)
        g2d.translate(-labelXOffset, -labelYOffset)
      }
    }

    g2d.clip = origClip
  }

  override fun renderOverlay(host: Component, g2d: Graphics2D) {}

  override fun handleMouseEvent(overlayComponent: Component, selectionComponent: Component, event: MouseEvent) = false.also {
    mousePosition = event.point
    isMouseOverHeapDump = (0 until startsCache.size).any { i ->
      val xStart = startsCache.getDouble(i)
      val xLen = durationsCache.getDouble(i)
      val scaledStartX = xStart * overlayComponent.width
      val scaledDur = xLen * overlayComponent.width
      scaledStartX < mousePosition.x && mousePosition.x < scaledStartX + scaledDur &&
      overlayComponent.y < mousePosition.y && mousePosition.y < overlayComponent.y + overlayComponent.height
    }
  }

  private fun modelChanged() {
    startsCache.clear()
    durationsCache.clear()
    labelCache.clear()

    val series = model.series
    val xMin = series.xRange.min
    val xLen = series.xRange.length
    for (data in series.series) {
      startsCache.add((data.x - xMin) / xLen)
      durationsCache.add(data.value.durationUs / xLen)
      val text =
        if (data.value.durationUs == Long.MAX_VALUE) "in progress"
        else TimeAxisFormatter.DEFAULT.getFormattedString(viewRange.length, data.value.durationUs.toDouble(), true)
      labelCache.add(JLabel("Dump ($text)").apply {
        font = AdtUiUtils.DEFAULT_FONT.deriveFont(9f)
        foreground = labelColor
        background = JBColor.DARK_GRAY
        val size = preferredSize
        setBounds(0, 0, size.width, size.height)
      })
    }
  }

  private companion object {
    const val labelXPadding = 4.0
    const val labelYPadding = 4.0
    val labelBgColor = JBColor.DARK_GRAY
    val labelColor = JBColor.WHITE
  }
}