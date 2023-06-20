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
import com.android.tools.adtui.model.StateChartModel
import com.android.tools.adtui.model.Stopwatch
import com.google.common.annotations.VisibleForTesting
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.MouseEventHandler
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import java.awt.geom.Rectangle2D
import java.util.function.Consumer
import java.util.function.IntConsumer
import javax.swing.JList
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A renderer is a function performing arbitrary drawing on the graphics.
 * Other parameters such as boundary and defaultFontMetrics meant to be
 * read-only.
 */
typealias Renderer<T> = (g: Graphics2D,
                         boundary: Rectangle2D.Float,
                         defaultFontMetrics: FontMetrics,
                         hoverred: Boolean,
                         value: T) -> Unit

/**
 * A chart component that renders series of state change events as rectangles.
 */
class StateChart<T : Any>(private val model: StateChartModel<T>,
                          private val render: Renderer<T>,
                          private val config: StateChartConfig<T> = defaultConfig())
  : AnimatedComponent() {
  /**
   * The gap value as a percentage {0...1} of the height given to each data series
   */
  var heightGap = config.heightGap
    set(gap) {
      field = max(min(gap, 1f), 0f)
    }

  private var needsTransformToViewSpace = true

  /**
   * For each series, cache a pair of:
   * - List of rectangles for the events in the current range, and
   * - List of values for the events in the current range
   * The lists are parallel and should always have the same size. We maintain 2 lists just for
   * compatibility with the code from other places.
   */
  private var rectangleCache = listOf<Pair<List<Rectangle2D.Float>, List<T>>>()
  private var rowPoint: Point? = null
    set(point) {
      field = point
      hoveredSeriesIndex = point?.let(::seriesIndexAtPoint) ?: INVALID_INDEX
    }
  private var hoveredSeriesIndex = INVALID_INDEX
    set(index) {
      if (field != index) {
        field = index
        rowIndexChangeListeners.forEach { it.accept(index) }
      }
    }
  private val itemClickedListeners = mutableListOf<Consumer<T>>()
  private val rowIndexChangeListeners = mutableListOf<IntConsumer>()

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

  @JvmOverloads
  constructor(model: StateChartModel<T>,
              colorProvider: StateChartColorProvider<T>,
              config: StateChartConfig<T> = defaultConfig())
    : this(model, fillRectRenderer(colorProvider), config)

  @JvmOverloads
  constructor(model: StateChartModel<T>,
              colorProvider: StateChartColorProvider<T>,
              textConverter: StateChartTextConverter<T>,
              config: StateChartConfig<T> = defaultConfig())
    : this(model, fillRectAndTextRenderer(colorProvider, textConverter), config)

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

    rectangleCache = series.mapIndexed { seriesIndex, data ->
      val min = data.xRange.min
      val max = data.xRange.max
      val invRange = 1.0 / (max - min)
      val startHeight = 1.0 - rectHeight * (seriesIndex + 1)
      val barY = startHeight + gap * 0.5f
      val seriesDataList = data.series
      val rectangles = mutableListOf<Rectangle2D.Float>()
      val rectangleValues = mutableListOf<T>()

      fun addRectangleDelta(value: T, previousX: Double, currentX: Double) {
        // Because we start our activity line from the bottom and grow up we offset the height from the bottom of the component
        // instead of the top by subtracting our height from 1.
        rectangles.add(Rectangle2D.Float(((previousX - min) * invRange).toFloat(), barY.toFloat(),
                                         ((currentX - previousX) * invRange).toFloat(), barHeight))
        rectangleValues.add(value)
      }

      if (seriesDataList.isNotEmpty()) {
        // Construct rectangles.
        var previousX = seriesDataList[0].x.toDouble()
        var previousValue = seriesDataList[0].value
        for ((x, value) in seriesDataList.subList(1, seriesDataList.size)) {
          if (value != previousValue) { // Ignore repeated values.
            // Don't draw if this block doesn't intersect with [min..max]
            if (x >= min) {
              // Draw the previous block.
              addRectangleDelta(previousValue, previousX, x.toDouble())
            }

            // Start a new block.
            previousValue = value
            previousX = x.toDouble()
            if (previousX >= max) break // Drawn past max range, stop.
          }
        }
        // The last data point continues till max
        if (previousX < max && previousValue != null) {
          addRectangleDelta(previousValue, max(min, previousX), max)
        }
      }

      rectangles to rectangleValues
    }
  }

  override fun draw(g2d: Graphics2D, dim: Dimension) {
    val stopwatch = Stopwatch().start()
    var scalingTime = 0L
    var reducerTime = 0L
    var transformedShapesCount = 0

    transformToViewSpace()
    val transformTime = stopwatch.elapsedSinceLastDeltaNs
    g2d.font = font
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
    val scaleX = width.toFloat()
    val scaleY = height.toFloat()
    val clipRect = g2d.clipBounds

    rectangleCache.forEachIndexed { seriesIndex, (rectangles, rectangleValues) ->
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
      transformedShapesCount += transformedShapes.size
      scalingTime += stopwatch.elapsedSinceLastDeltaNs
      config.reducer.reduce(transformedShapes, transformedValues)
      assert(transformedShapes.size == transformedValues.size)
      reducerTime += stopwatch.elapsedSinceLastDeltaNs
      val hoverIndex = when {
        seriesIndex != hoveredSeriesIndex || rowPoint == null -> INVALID_INDEX
        else -> rowPoint!!.x.toFloat().let { transformedShapes.searchByX(it, 1f) }
      }

      transformedShapes.indices.forEach {
        val rect = transformedShapes[it]
        // the rectangles are allowed to go outside this component, so we clip it
        g2d.clip = when {
          0 <= rect.x && rect.x + rect.width <= width -> rect
          else -> {
            val x = max(0f, rect.x)
            val w = min(scaleX - x, rect.width)
            Rectangle2D.Float(x, rect.y, w, rect.height)
          }
        }
        render(g2d, rect, mDefaultFontMetrics, it == hoverIndex, transformedValues[it])
      }
    }

    val drawTime = stopwatch.elapsedSinceLastDeltaNs
    addDebugInfo("XS ms: %.2fms, %.2fms", transformTime / 1000000f, scalingTime / 1000000f)
    addDebugInfo(
      "RDT ms: %.2f, %.2f, %.2f", reducerTime / 1000000f, drawTime / 1000000f,
      (scalingTime + reducerTime + drawTime) / 1000000f
    )
    addDebugInfo("# of drawn rects: %d", transformedShapesCount)
  }

  fun addItemClickedListener(onClicked: Consumer<T>) {
    itemClickedListeners.add(onClicked)
  }

  fun addRowIndexChangeListener(onNewRowIndex: IntConsumer) {
    rowIndexChangeListeners.add(onNewRowIndex)
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
        if (event.id == MouseEvent.MOUSE_CLICKED) {
          itemAtMouse(event.point)?.let { itemClickedListeners.forEach { handler -> handler.accept(it) } }
          return
        }

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

  @VisibleForTesting
  fun itemAtMouse(point: Point): T? = seriesIndexAtMouse(point)?.let { (seriesIndex, i) ->
    model.series[seriesIndex].series.getOrNull(i)?.value
  }

  /**
   * Find the item index in the model that corresponds to the mouse position.
   * @return - null if the mouse isn't on any series, or
   *         - a pair of the series index, and the item index within the series.
   *           The item index corresponds to the right-most edge that's to the
   *           mouse's left, or (-1) if the mouse is to the left of all items
   */
  @VisibleForTesting
  fun seriesIndexAtMouse(point: Point): Pair<Int, Int>? {
    val series = model.series
    if (series.isEmpty()) return null

    val scaleX = width.toDouble()
    return seriesIndexAtPoint(point)?.let { seriesIndex ->
      val seriesAtMouse = series[seriesIndex]
      val seriesData = seriesAtMouse.series
      val min = seriesAtMouse.xRange.min
      val max = seriesAtMouse.xRange.max
      val range = max - min

      // Convert mouseX into data/series coordinate space
      val modelMouseX = point.x / scaleX * range + min
      when {
        seriesData.isEmpty() -> null
        else -> seriesIndex to when (val i = seriesData.binarySearch { it.x.compareTo(modelMouseX) }) {
          in seriesData.indices -> i // mouse right on edge
          else -> -i - 1 - 1         // mouse to the right of insertion index
        }
      }
    }
  }

  private fun seriesIndexAtPoint(point: Point) = (1f - point.y / height.toFloat()).let { normalizedY ->
    val n = model.series.size
    val i = (normalizedY * n).toInt()
    val tolerancePixels = 2 // just in case of Swing off-by-one-pixel-mouse-handling issues
    when {
      i in 0 until n -> i
      point.y in height .. (height + tolerancePixels) -> 0 // a hair too low
      point.y in -tolerancePixels .. 0 -> n - 1 // a hair too high
      else -> null
    }
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

  private fun getMouseRectanglesUnion(mousePoint: Point) = seriesIndexAtMouse(mousePoint)?.let { (seriesIndex, i) ->
    val series = model.series[seriesIndex]
    val seriesDataList = series.series
    val min = series.xRange.min
    val max = series.xRange.max
    val range = max - min
    val seriesSize = model.series.size
    val scaleX = width.toDouble()
    val scaleY = height.toDouble()

    // Transform the union of the left and right (or range max) index x values back into view space.
    val modelXLeft = seriesDataList.getOrNull(i)?.x?.toDouble() ?: min
    val modelXRight = seriesDataList.getOrNull(i + 1)?.x?.toDouble() ?: max
    val screenXLeft = floor((modelXLeft - min) * scaleX / range)
    val screenYTop = floor(scaleY - (seriesIndex + 1) * scaleY / seriesSize)
    val screenXRight = ceil((modelXRight - min) * scaleX / range)
    val screenYBottom = ceil(scaleY - seriesIndex * scaleY / seriesSize)
    val screenWidth = screenXRight - screenXLeft
    val screenHeight = screenYBottom - screenYTop
    Rectangle2D.Float(screenXLeft.toFloat(), screenYTop.toFloat(), screenWidth.toFloat(), screenHeight.toFloat())
  }

  companion object {
    private const val INVALID_INDEX = -1
    private const val TEXT_PADDING = 3
    private const val PREFERRED_ROW_HEIGHT = 27
    private fun<T> defaultConfig() = StateChartConfig<T>(DefaultStateChartReducer<T>())
    @JvmStatic fun<T> defaultTextConverter() = StateChartTextConverter<T>(Any?::toString)

    private fun <T : Any> fillRectRenderer(colorProvider: StateChartColorProvider<T>): Renderer<T> = { g, rect, _, hovered, value ->
      g.color = colorProvider.getColor(hovered, value)
      g.fill(rect)
    }

    private fun <T : Any> fillRectAndTextRenderer(colorProvider: StateChartColorProvider<T>,
                                           textConverter: StateChartTextConverter<T>): Renderer<T> =
      fillRectRenderer(colorProvider).let { fillRect -> { g, rect, fontMetrics, hovered, value ->
        fillRect(g, rect, fontMetrics, hovered, value)
        val text = shrinkToFit(textConverter.convertToString(value), fontMetrics, rect.width - TEXT_PADDING * 2)
        if (text.isNotEmpty()) {
          g.color = colorProvider.getFontColor(hovered, value)
          val textOffset = rect.y + (rect.height - fontMetrics.height) * 0.5f + fontMetrics.ascent.toFloat()
          g.drawString(text, rect.x + TEXT_PADDING, textOffset)
        }
      }}
  }
}

private fun<T: Any> List<T>.getOrNull(i: Int): T? = if (i in indices) get(i) else null