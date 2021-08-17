/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui.chart.statechart

import com.android.tools.adtui.AnimatedComponent
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.common.AdtUiUtils.shrinkToFit
import com.android.tools.adtui.model.SeriesData
import com.android.tools.adtui.model.StateChartModel
import com.android.tools.adtui.model.Stopwatch
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.MouseEventHandler
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import java.awt.geom.Rectangle2D
import javax.swing.JList
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A chart component that renders series of state change events as rectangles.
 */
class StateChart<T: Any>
  @JvmOverloads constructor (private var model: StateChartModel<T>,
                             private val colorProvider: StateChartColorProvider<T>,
                             private val textConverter: StateChartTextConverter<T> = defaultTextConverter(),
                             private val config: StateChartConfig<T> = defaultConfig())
  : AnimatedComponent() {
  /**
   * The gap value as a percentage {0...1} of the height given to each data series
   */
  var heightGap = config.heightGap
    set(gap) {
      field = max(min(gap, 1f), 0f)
    }

  var renderMode = RenderMode.BAR
  private var needsTransformToViewSpace = true
  private val rectangles = mutableListOf<Rectangle2D.Float>()
  private val rectangleValues = mutableListOf<T>()
  private var rowPoint: Point? = null

  init {
    font = AdtUiUtils.DEFAULT_FONT

    fun modelChanged() {
      needsTransformToViewSpace = true
      opaqueRepaint()
    }
    model.addDependency(myAspectObserver).onChange(StateChartModel.Aspect.MODEL_CHANGED, ::modelChanged)
    modelChanged()
    registerMouseEvents()
    preferredSize = Dimension(preferredSize.width, JBUI.scale(PREFERRED_ROW_HEIGHT) * model.series.size)
  }

  /**
   * @param colors map of a state to corresponding color
   */
  @VisibleForTesting
  constructor(model: StateChartModel<T>, colors: Map<T, Color>) :
    this(model, colorProvider = object : StateChartColorProvider<T>() {
      override fun getColor(isMouseOver: Boolean, value: T): Color = colors[value]!!.let {
        if (isMouseOver) ColorUtil.brighter(it, 2) else it
      }
    })

  private fun clearRectangles() {
    rectangles.clear()
    rectangleValues.clear()
  }

  private fun transformToViewSpace() {
    if (!needsTransformToViewSpace) {
      return
    }
    needsTransformToViewSpace = false
    val series = model.series
    val seriesSize = series.size
    if (seriesSize == 0) {
      return
    }

    // TODO support interpolation.
    val rectHeight = 1.0f / seriesSize
    val gap = rectHeight * heightGap
    val barHeight = rectHeight - gap
    clearRectangles()
    for (seriesIndex in 0 until seriesSize) {
      val data = series[seriesIndex]
      val min = data.xRange.min.toFloat()
      val max = data.xRange.max.toFloat()
      val invRange = 1.0f / (max - min)
      val startHeight = 1.0f - rectHeight * (seriesIndex + 1)
      val barY = startHeight + gap * 0.5f
      val seriesDataList = data.series

      fun addRectangleDelta(value: T, previousX: Float, currentX: Float) {
        // Because we start our activity line from the bottom and grow up we offset the height from the bottom of the component
        // instead of the top by subtracting our height from 1.
        rectangles.add(Rectangle2D.Float((previousX - min) * invRange, barY,
                                         (currentX - previousX) * invRange, barHeight))
        rectangleValues.add(value)
      }

      if (seriesDataList.isNotEmpty()) {
        // Construct rectangles.
        var previousX = seriesDataList[0].x.toFloat()
        var previousValue = seriesDataList[0].value
        for ((x, value) in seriesDataList.subList(1, seriesDataList.size)) {
          if (value != previousValue!!) { // Ignore repeated values.
            // Don't draw if this block doesn't intersect with [min..max]
            if (x >= min) {
              // Draw the previous block.
              addRectangleDelta(previousValue, max(min, previousX), min(max, x.toFloat()))
            }

            // Start a new block.
            previousValue = value
            previousX = x.toFloat()
            if (previousX >= max) break // Drawn past max range, stop.
          }
        }
        // The last data point continues till max
        if (previousX < max && previousValue != null) {
          addRectangleDelta(previousValue, max(min, previousX), max)
        }
      }
    }
  }

  override fun draw(g2d: Graphics2D, dim: Dimension) {
    val stopwatch = Stopwatch().start()
    transformToViewSpace()
    val transformTime = stopwatch.elapsedSinceLastDeltaNs
    g2d.font = font
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
    assert(rectangles.size == rectangleValues.size)
    val scaleX = width.toFloat()
    val scaleY = height.toFloat()
    val clipRect = g2d.clipBounds

    fun List<Rectangle2D.Float>.searchByX(x: Float, w: Float) = binarySearch { when {
      it.x + it.width < x -> -1
      it.x > x + w -> 1
      else -> 0
    } }

    val startIndexInclusive = when {
      clipRect != null && clipRect.x != 0 -> rectangles.searchByX(clipRect.x / scaleX, 0f).let {
        if (it < 0) -(it+1) else it
      }
      else -> 0
    }
    val endIndexExclusive = when {
      clipRect != null && clipRect.width != width -> rectangles.searchByX((clipRect.x + clipRect.width) / scaleX, 0f).let {
        if (it < 0) -(it+1) else (it+1) // add 1 because exclusive
      }
      else -> rectangles.size
    }
    val transformedValues = rectangleValues.subList(startIndexInclusive, endIndexExclusive).mapTo(mutableListOf()) { it }
    val transformedShapes = rectangles.subList(startIndexInclusive, endIndexExclusive).mapTo(mutableListOf()) {
      // Manually scaling the rectangle results in ~6x performance improvement over calling
      // AffineTransform::createTransformedShape. The reason for this is the shape created is a Point2D.Double.
      // This shape has to support all types of points as such cannot be transformed as efficiently as a
      // rectangle. Furthermore, AffineTransform uses doubles, which is about half as fast for LS
      // when compared to floats (doubles memory bandwidth).
      Rectangle2D.Float(it.x * scaleX, it.y * scaleY, it.width * scaleX, it.height * scaleY)
    }
    val scalingTime = stopwatch.elapsedSinceLastDeltaNs
    config.reducer.reduce(transformedShapes, transformedValues)
    assert(transformedShapes.size == transformedValues.size)
    val reducerTime = stopwatch.elapsedSinceLastDeltaNs
    val hoverIndex = rowPoint?.x?.toFloat()?.let { transformedShapes.searchByX(it, 1f) }
                     ?: INVALID_INDEX
    for (i in transformedShapes.indices) {
      val value = transformedValues[i]
      val rect = transformedShapes[i]
      val isMouseOver = i == hoverIndex
      g2d.color = colorProvider.getColor(isMouseOver, value)
      g2d.fill(rect)
      if (renderMode == RenderMode.TEXT) {
        val text = shrinkToFit(textConverter.convertToString(value), mDefaultFontMetrics, rect.width - TEXT_PADDING * 2)
        if (text.isNotEmpty()) {
          g2d.color = colorProvider.getFontColor(isMouseOver, value)
          val textOffset = rect.y + (rect.height - mDefaultFontMetrics.height) * 0.5f + mDefaultFontMetrics.ascent.toFloat()
          g2d.drawString(text, rect.x + TEXT_PADDING, textOffset)
        }
      }
    }
    val drawTime = stopwatch.elapsedSinceLastDeltaNs
    addDebugInfo("XS ms: %.2fms, %.2fms", transformTime / 1000000f, scalingTime / 1000000f)
    addDebugInfo(
      "RDT ms: %.2f, %.2f, %.2f", reducerTime / 1000000f, drawTime / 1000000f,
      (scalingTime + reducerTime + drawTime) / 1000000f
    )
    addDebugInfo("# of drawn rects: %d", transformedShapes.size)
  }

  private fun registerMouseEvents() {
    /**
     * In some cases, StateChart is delegated to by a parent containing component (e.g. a JList or
     * a table). In order to preform some painting optimizations, we need access to that source
     * component.
     *
     * TODO(b/116747281): It seems like we shouldn't have to know about this. Otherwise, almost
     * every component would need special-case logic like this. We should revisit how this class
     * is being used by CpuCellRenderer.
     */
    var mouseEventSource: Any? = null
    var mousePoint: Point? = null
    var rowIndex = INVALID_INDEX

    val handler = object : MouseEventHandler() {
      override fun handle(event: MouseEvent) {
        if (event.point == mousePoint) return
        val src = event.source
        if (rowIndex != INVALID_INDEX) {
          val oldRowOriginInEventSpace = when (src) {
            // First convert the event mouse position into row index for the list.
            is JList<*> -> src.ui.indexToLocation(src, rowIndex)
            else -> Point(0, 0)
          }
          oldRowOriginInEventSpace?.let { renderUnion(mouseEventSource, it) }
        }
        if (event.id == MouseEvent.MOUSE_EXITED) {
          mousePoint = null
          rowPoint = null
          mouseEventSource = null
          rowIndex = INVALID_INDEX
        }
        else {
          var rowOrigin = Point(0, 0)
          val eventPoint = event.point
          mousePoint = eventPoint
          mouseEventSource = src
          when (src) {
            is JList<*> -> {
              // Since JList uses CellRenderers to render each list item, we actually need to translate the source location (in the JList's
              // space) to the cell's coordinate space. We do this by simply getting the row index that the mouse location corresponds to,
              // and then translate the index back to the List's coordinate space (which uses the origin of the row automatically). Then we
              // subtract/translate the mouse point (which is still in the JLists's space) by the origin to get the mouse coordinate in the
              // row's origin. This is akin to calculating the value after the decimal of a floating point number to its floor.
              rowIndex = src.ui.locationToIndex(src, eventPoint)
              if (rowIndex >= 0) {
                rowOrigin = src.ui.indexToLocation(src, rowIndex)!!
              }
              rowPoint = Point(eventPoint).apply { translate(-rowOrigin.x, -rowOrigin.y) }
            }
            else -> {
              // If the StateChart is not in a JList, then there is only one row. So we set the row to the first (and only) row to let the
              // render happen.
              rowIndex = 0
              rowPoint = eventPoint
            }
          }
          if (rowIndex != INVALID_INDEX) renderUnion(mouseEventSource, rowOrigin)
        }
      }
    }
    addMouseListener(handler)
    addMouseMotionListener(handler)
  }

  private fun renderUnion(container: Any?, containerOffset: Point) {
    if (rowPoint != null && container is Component) {
      getMouseRectanglesUnion(rowPoint!!)?.let { union ->
        // StateChart is commonly used as a cell renderer component, and therefore is not in the proper Swing hierarchy.
        // Because of this, we need to use the source (which is probably a JList) to perform the actual repaint.
        container.repaint(union.x.toInt() + containerOffset.x,
                          union.y.toInt() + containerOffset.y,
                          ceil(union.width).toInt(),
                          ceil(union.height).toInt())
      }
    }
  }

  private fun getMouseRectanglesUnion(mousePoint: Point): Rectangle2D.Float? {
    val series = model.series
    if (series.isEmpty()) return null

    val scaleX = width.toFloat()
    val scaleY = height.toFloat()
    val seriesSize = series.size
    val seriesIndex = (1.0f - mousePoint.y / scaleY).let { normalizedMouseY ->
      // Clamp just in case of Swing off-by-one-pixel-mouse-handling issues
      min(seriesSize - 1, (normalizedMouseY * seriesSize).toInt())
    }

    if (seriesIndex !in 0 until seriesSize) {
      Logger.getInstance(StateChart::class.java).warn(
        "Series index in getMouseRectanglesUnion is out of bounds: mouseY = ${mousePoint.y}, scaleY = $scaleY"
      )
      return Rectangle2D.Float(0f, 0f, scaleX, scaleY)
    }
    val data = series[seriesIndex]
    val min = data.xRange.min.toFloat()
    val max = data.xRange.max.toFloat()
    val range = max - min

    // Convert mouseX into data/series coordinate space. However, note that the mouse covers a whole pixel, which has width.
    // Therefore, we need to find all the rectangles potentially intersecting the pixel.
    val modelMouseXLeft = floor((mousePoint.x - 1f) / scaleX * range + min)
    val modelMouseXRight = ceil((mousePoint.x + 1.0f) / scaleX * range + min)
    fun compareWithMouseRange(data: SeriesData<T>) = when {
      data.x < modelMouseXLeft -> -1
      data.x > modelMouseXRight -> 1
      else -> 0
    }
    val seriesDataList = data.series
    if (seriesDataList.isEmpty()) return null

    val (leftIndex, rightIndex) = seriesDataList.binarySearch(comparison = ::compareWithMouseRange).let { when (it) {
      in seriesDataList.indices -> max(0, it - 1) to min(it + 1, seriesDataList.size - 1)
      else -> (-it - 1).let { max(0, it - 1) to min(it, seriesDataList.size - 1) }
    } }

    // Now transform the union of the left and right (or range max) index x values back into view space.
    val modelXLeft = max(min, seriesDataList[leftIndex].x.toFloat())
    val modelXRight = min(max, seriesDataList[rightIndex].x.toFloat())
    val screenXLeft = floor((modelXLeft - min) * scaleX / range)
    val screenYTop = ceil(seriesIndex * scaleY / seriesSize)
    val screenXRight = ceil((modelXRight - min) * scaleX / range)
    val screenYBottom = floor((seriesIndex + 1) * scaleY / seriesSize)
    val screenWidth = screenXRight - screenXLeft
    val screenHeight = screenYBottom - screenYTop
    return Rectangle2D.Float(screenXLeft, screenYTop, screenWidth, screenHeight)
  }

  private companion object {
    const val INVALID_INDEX = -1
    const val TEXT_PADDING = 3
    const val PREFERRED_ROW_HEIGHT = 27
    fun<T> defaultConfig() = StateChartConfig<T>(DefaultStateChartReducer<T>())
    fun<T: Any> defaultTextConverter() = StateChartTextConverter<T> { it.toString() }
  }

  enum class RenderMode {
    BAR,  // Each state is rendered as a filled rectangle until the next state changed.
    TEXT // Each state is marked with a vertical line and and corresponding state text/label at the beginning.
  }
}