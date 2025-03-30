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

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.RangedSeries
import com.android.tools.adtui.model.SeriesData
import com.android.tools.adtui.model.StateChartModel
import com.android.tools.profilers.ProfilerColors
import com.intellij.ide.ui.UISettings
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.awt.geom.Rectangle2D
import java.util.function.BooleanSupplier
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.max
import kotlin.math.min

/**
 * Wraps the given component inside one that also draws Vsync in the background.
 */
object VsyncPanel {
  @JvmStatic
  fun of(content: JComponent, viewRange: Range, vsyncValues: List<SeriesData<Long>>, vsyncEnabler: BooleanSupplier): JComponent =
    of(content, RangedSeries(viewRange, LazyDataSeries { vsyncValues }), vsyncEnabler)

  @JvmStatic
  fun of(content: JComponent, series: RangedSeries<Long>, vsyncEnabler: BooleanSupplier): JComponent =
    // Code copied and specialized from {@link StateChart}.
    object : JPanel(BorderLayout()) {
      val model = StateChartModel<Long>().apply { addSeries(series) }
      val observer = AspectObserver()
      var intervalsInViewUpdated = false
      val intervalsInView = mutableListOf<Interval>()

      override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (vsyncEnabler.asBoolean) {
          (g.create() as Graphics2D).let { g2d ->
            UISettings.setupAntialiasing(g2d)
            draw(g2d)
            g2d.dispose()
          }
        }
      }

      private fun updateIntervalsInView() {
        if (!intervalsInViewUpdated) {
          intervalsInViewUpdated = true
          intervalsInView.clear()

          val data = model.series[0]
          val min = data.xRange.min
          val max = data.xRange.max
          val invRange = 1.0 / (max - min)
          val dataList = data.series
          fun addInterval(previousX: Double, x: Double, on: Boolean) =
            intervalsInView.add(Interval(((previousX - min) * invRange).toFloat(), ((x - previousX) * invRange).toFloat(), on))

          if (dataList.isNotEmpty()) {
            var previousX = dataList[0].x.toDouble()
            var previousVal = dataList[0].value > 0
            for (event in dataList.subList(1, dataList.size)) {
              val x = event.x.toDouble()
              if (x >= min) addInterval(max(min, previousX), min(max, x), previousVal)
              previousX = x
              previousVal = event.value > 0
              if (previousX >= max) break
            }
            if (previousX < max) addInterval(max(min, previousX), max, previousVal)
          }
        }
      }

      private fun draw(g2d: Graphics2D) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        val scaleX = width.toFloat()
        val scaleY = height.toFloat()
        val clipRect = g2d.clipBounds

        fun overlapIndex(x: Float, w: Float) = intervalsInView.binarySearch { value -> when {
          value.x + value.w < x -> -1
          value.x > x + w -> 1
          else -> 0
        } }

        updateIntervalsInView()

        val startIndexInclusive = when {
          clipRect != null && clipRect.x != 0 -> overlapIndex(clipRect.x / scaleX, 0f).let {
            if (it < 0) -(it+1) else it
          }
          else -> 0
        }
        val endIndexExclusive = when {
          clipRect != null && clipRect.width != width -> overlapIndex((clipRect.x + clipRect.width) / scaleX, 0f).let {
            if (it < 0) -(it+1) else (it+1) // add 1 if found, because exclusive
          }
          else -> intervalsInView.size
        }

        if (endIndexExclusive - startIndexInclusive < scaleX) { // only draw when each half cycle is at least a pixel
          intervalsInView.subList(startIndexInclusive, endIndexExclusive).forEach { (x, w, on) ->
            g2d.color = if (on) ProfilerColors.VSYNC_BACKGROUND else ProfilerColors.DEFAULT_BACKGROUND
            g2d.fill(Rectangle2D.Float(x * scaleX, 0f, w * scaleX, scaleY))
          }
        }
      }

    }.apply {
      add(content)
      fun modelChanged() {
        intervalsInViewUpdated = false
        // For certain scenarios (such as a non-opaque panel sitting on a JLayeredPane), repaint() may not work correctly.
        // Therefore, by forcing the repaint up the component hierarchy to the closest opaque ancestor, we cause all overlapping children
        // to render, and avoid the "top child erases things underneath it" problem.
        getOpaqueContainer().repaint()
      }
      model.addDependency(observer).onChange(StateChartModel.Aspect.MODEL_CHANGED, ::modelChanged)
      modelChanged()

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
}

private tailrec fun Container.getOpaqueContainer(): Container = when {
  !isOpaque && parent != null -> parent.getOpaqueContainer()
  else -> this
}

private data class Interval(val x: Float, val w: Float, val isOn: Boolean)