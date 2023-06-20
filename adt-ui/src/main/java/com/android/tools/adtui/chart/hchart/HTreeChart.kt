/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.adtui.chart.hchart

import com.android.tools.adtui.AnimatedComponent
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.common.AdtUiUtils.isActionKeyDown
import com.android.tools.adtui.model.HNode
import com.android.tools.adtui.model.Range
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.ui.UISettings
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import java.awt.AlphaComposite
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Image
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.util.LinkedList
import java.util.Queue
import javax.swing.AbstractAction
import javax.swing.KeyStroke
import kotlin.math.max
import kotlin.math.min

/**
 * A chart which renders nodes using a horizontal flow. That is, while normal trees are vertical,
 * rendering nested rows top-to-bottom, this chart renders nested columns left-to-right.
 *
 * @param <N> The type of the node used by this tree chart
</N> */
class HTreeChart<N : HNode<N>> private constructor(builder: Builder<N>) : AnimatedComponent() {
  val orientation: Orientation = builder.orientation
  private val renderer = builder.renderer
  private var root = builder.root
  private val xRange = builder.xRange

  /**
   * The X range that myXRange could possibly be. Any changes to X range should be limited within it.
   */
  private val globalXRange = builder.globalXRange
  val yRange: Range = Range(INITIAL_Y_POSITION.toDouble(), INITIAL_Y_POSITION.toDouble())
  private val rectangles = ArrayList<Rectangle2D.Float>()
  private val nodes = ArrayList<N>()
  private val rootVisible = builder.rootVisible

  /**
   * Normally, the focused node is set by mouse hover. However, for tests, it can be a huge
   * convenience to set this directly.
   *
   *
   * It is up to the caller to make sure that the node specified here actually belongs to this
   * chart. Otherwise, the call will have no effect.
   */
  @set:VisibleForTesting
  var focusedNode: N? = null
  private val nodeSelectionEnabled = builder.nodeSelectionEnabled

  /**
   * Updates the selected node. This is called by mouse click event handler and also from other instances of HTreeChart selects a node and
   * wants to update the (un)selected state of this instance.
   */
  @get: VisibleForTesting
  var selectedNode: N? = null
    set(node) {
      if (field !== node) {
        dataUpdated = true
        field = node
      }
    }

  private val drawnRectangles = ArrayList<Rectangle2D.Float>()
  private val drawnNodes = ArrayList<N>()
  private val reducer = builder.reducer
  private var canvas: Image? = null

  /**
   * If true, the next render pass will forcefully rebuild this chart's canvas (an expensive
   * operation which doesn't have to be done too often as usually the contents are static)
   */
  private var dataUpdated = false
  var maximumHeight = 0
    private set

  /**
   * Height of a tree node in pixels. If not set, we use the default font height.
   */
  private val customNodeHeightPx = builder.customNodeHeightPx

  /**
   * Vertical and horizontal padding in pixels between tree nodes.
   */
  private val nodeXPaddingPx = builder.nodeXPaddingPx
  private val nodeYPaddingPx = builder.nodeYPaddingPx

  private val nodeHeight: Int
    get() = if (customNodeHeightPx > 0) customNodeHeightPx else mDefaultFontMetrics.height

  init {
    isFocusable = true
    initializeInputMap()
    initializeMouseEvents()
    font = AdtUiUtils.DEFAULT_FONT
    xRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, ::rangeChanged)
    yRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, ::rangeChanged)
    rootChanged()
  }

  private fun rangeChanged() {
    dataUpdated = true
    opaqueRepaint()
  }

  private fun rootChanged() {
    maximumHeight = calculateMaximumHeight()
    // Update preferred size using calculated height to make sure containers of this chart account for the height change during layout.
    preferredSize = Dimension(preferredSize.width, maximumHeight)
    rangeChanged()
  }

  override fun draw(g: Graphics2D, dim: Dimension) {
    val startTime = System.nanoTime()
    if (dataUpdated) {
      // Nulling out the canvas will trigger a render pass, below
      updateNodesAndClearCanvas()
      dataUpdated = false
    }
    g.font = font
    if (root == null || root!!.childCount == 0) {
      g.drawString(NO_HTREE, dim.width / 2 - mDefaultFontMetrics.stringWidth(NO_HTREE), dim.height / 2)
      return
    }
    if (xRange.length == 0.0) {
      g.drawString(NO_RANGE, dim.width / 2 - mDefaultFontMetrics.stringWidth(NO_RANGE), dim.height / 2)
      return
    }
    if (canvas == null
        || ImageUtil.getUserHeight(canvas!!) != dim.height
        || ImageUtil.getUserWidth(canvas!!) != dim.width) {
      redrawToCanvas(dim)
    }
    UIUtil.drawImage(g, canvas!!, 0, 0, null)
    addDebugInfo("Draw time %.2fms", (System.nanoTime() - startTime) / 1e6)
    addDebugInfo("# of nodes %d", nodes.size)
    addDebugInfo("# of reduced nodes %d", drawnNodes.size)
  }

  private fun redrawToCanvas(dim: Dimension) {
    if (canvas == null
        || ImageUtil.getUserWidth(canvas!!) < dim.width
        || ImageUtil.getUserHeight(canvas!!) < dim.height) {
      // Note: We intentionally create an RGB image, not an ARGB image, because this allows nodes
      // to render their text clearly (ARGB prevents LCD rendering from working).
      canvas = ImageUtil.createImage(dim.width, dim.height, BufferedImage.TYPE_INT_ARGB)
    }
    val g = canvas!!.graphics as Graphics2D
    g.color = background
    g.composite = AlphaComposite.Clear
    g.fillRect(0, 0, dim.width, dim.height)
    g.composite = AlphaComposite.Src
    UISettings.setupAntialiasing(g)
    g.font = font
    drawnNodes.clear()
    drawnNodes.addAll(nodes)
    drawnRectangles.clear()
    // Transform
    for (rect in rectangles) {
      val newRect = Rectangle2D.Float()
      newRect.x = rect.x * dim.getWidth().toFloat()
      newRect.y = rect.y
      newRect.width = max(0f, rect.width * dim.getWidth().toFloat() - nodeXPaddingPx)
      newRect.height = rect.height
      if (orientation == Orientation.BOTTOM_UP) {
        newRect.y = (dim.getHeight() - newRect.y - newRect.getHeight()).toFloat()
      }
      drawnRectangles.add(newRect)
    }
    reducer.reduce(drawnRectangles, drawnNodes)
    assert(drawnRectangles.size == drawnNodes.size)
    for (i in drawnNodes.indices) {
      val node = drawnNodes[i]
      val drawingArea = drawnRectangles[i]
      val clampedDrawingArea = Rectangle2D.Float(
        max(0f, drawingArea.x),
        drawingArea.y,
        min(drawingArea.x + drawingArea.width, (dim.width - nodeXPaddingPx).toFloat()) - max(0f, drawingArea.x),
        drawingArea.height
      )
      renderer.render(g, node, drawingArea, clampedDrawingArea, node === focusedNode, selectedNode != null && node !== selectedNode)
    }
    g.dispose()
  }

  private fun updateNodesAndClearCanvas() {
    nodes.clear()
    rectangles.clear()
    canvas = null
    if (root == null) {
      return
    }
    if (inRange(root!!)) {
      nodes.add(root!!)
      rectangles.add(createRectangle(root!!))
    }
    var head = 0
    while (head < nodes.size) {
      val curNode = nodes[head++]
      for (i in 0 until curNode.childCount) {
        val child = curNode.getChildAt(i)
        if (inRange(child)) {
          nodes.add(child)
          rectangles.add(createRectangle(child))
        }
      }
    }
    if (!rootVisible && nodes.isNotEmpty()) {
      nodes.removeAt(0)
      rectangles.removeAt(0)
    }
  }

  private fun inRange(node: N) = node.start <= xRange.max && node.end >= xRange.min

  private fun createRectangle(node: N): Rectangle2D.Float {
    val left = ((node.start - xRange.min) / xRange.length).toFloat()
    val right = ((node.end - xRange.min) / xRange.length).toFloat()
    return Rectangle2D.Float().apply {
      x = left
      y = ((nodeHeight + nodeYPaddingPx) * node.depth - yRange.min).toFloat()
      width = right - left
      height = nodeHeight.toFloat()
    }
  }

  private fun positionToRange(x: Double) = x / width * xRange.length + xRange.min

  fun setHTree(root: N?) {
    this.root = root
    rootChanged()
  }

  fun getNodeAt(point: Point): N? = (drawnNodes zip drawnRectangles).find { (_, rect) -> point in rect }?.first

  private fun initializeInputMap() {
    fun bindKey(key: Int, action: String) = inputMap.put(KeyStroke.getKeyStroke(key, 0), action)
    bindKey(KeyEvent.VK_UP, ACTION_ZOOM_IN)
    bindKey(KeyEvent.VK_W, ACTION_ZOOM_IN)
    bindKey(KeyEvent.VK_DOWN, ACTION_ZOOM_OUT)
    bindKey(KeyEvent.VK_S, ACTION_ZOOM_OUT)
    bindKey(KeyEvent.VK_LEFT, ACTION_MOVE_LEFT)
    bindKey(KeyEvent.VK_A, ACTION_MOVE_LEFT)
    bindKey(KeyEvent.VK_RIGHT, ACTION_MOVE_RIGHT)
    bindKey(KeyEvent.VK_D, ACTION_MOVE_RIGHT)

    fun bindMovementAction(action: String, perform: (Double) -> Unit) = actionMap.put(action, object: AbstractAction() {
      override fun actionPerformed(e: ActionEvent) = perform(xRange.length / ACTION_MOVEMENT_FACTOR)
    })
    bindMovementAction(ACTION_ZOOM_IN) { delta ->
      xRange.set(xRange.min + delta, xRange.max - delta)
    }
    bindMovementAction(ACTION_ZOOM_OUT) { delta ->
      xRange.set(max(globalXRange.min, xRange.min - delta), min(globalXRange.max, xRange.max + delta))
    }
    bindMovementAction(ACTION_MOVE_LEFT) { delta ->
      xRange.shift(-min(xRange.min - globalXRange.min, delta))
    }
    bindMovementAction(ACTION_MOVE_RIGHT) { delta ->
      xRange.shift(min(globalXRange.max - xRange.max, delta))
    }
  }

  private fun initializeMouseEvents() {
    val adapter: MouseAdapter = object : MouseAdapter() {
      private var lastPoint: Point? = null
      override fun mouseMoved(e: MouseEvent) {
        val node = getNodeAt(e.point)
        if (node !== focusedNode) {
          dataUpdated = true
          focusedNode = node
          eventSourceRepaint(e)
        }
      }

      override fun mouseClicked(e: MouseEvent) {
        if (!hasFocus()) {
          requestFocusInWindow()
        }
      }

      override fun mousePressed(e: MouseEvent) {
        lastPoint = e.point
      }

      override fun mouseDragged(e: MouseEvent) {
        // First, handle Y range.
        val deltaY = (e.point.y - lastPoint!!.y).toDouble()
        shiftYRange(if (orientation == Orientation.BOTTOM_UP) deltaY else -deltaY)

        // Second, handle X Range.
        val deltaX = (e.point.x - lastPoint!!.x).toDouble()
        var deltaXToShift = xRange.length / width * -deltaX
        when {
          // User attempts to move the chart towards left to view the area to the right.
          deltaXToShift > 0 -> deltaXToShift = min(globalXRange.max - xRange.max, deltaXToShift)
          // User attempts to move the chart towards right to view the area to the left.
          deltaXToShift < 0 -> deltaXToShift = max(globalXRange.min - xRange.min, deltaXToShift)
        }
        xRange.shift(deltaXToShift)
        lastPoint = e.point
      }

      private fun shiftYRange(deltaY: Double) {
        // The height of the contents we can show, including those not currently shown because of vertical scrollbar's position.
        var deltaY = deltaY
        val contentHeight = maximumHeight
        // The height of the GUI component to draw the contents.
        val viewHeight = height
        when {
          // User attempts to drag the chart's head (the outermost frame on call stacks) away from the boundary. No.
          yRange.min + deltaY < INITIAL_Y_POSITION -> deltaY = INITIAL_Y_POSITION - yRange.min
          // User attempts to drag the chart's toe (the innermost frame on call stacks) away from the boundary. No.
          // Note that the chart may be taller than the stacks, so we need to limit the delta.
          yRange.min + viewHeight + deltaY > contentHeight -> deltaY = max(0.0, contentHeight - viewHeight - yRange.min)
        }
        yRange.shift(deltaY)
      }

      override fun mouseWheelMoved(e: MouseWheelEvent) {
        if (isActionKeyDown(e)) {
          val cursorRange = positionToRange(e.x.toDouble())
          val leftDelta = (cursorRange - xRange.min) / ZOOM_FACTOR * e.wheelRotation
          val rightDelta = (xRange.max - cursorRange) / ZOOM_FACTOR * e.wheelRotation
          xRange.set(max(globalXRange.min, xRange.min - leftDelta), min(globalXRange.max, xRange.max + rightDelta))
        } else {
          val deltaY = e.preciseWheelRotation * MOUSE_WHEEL_SCROLL_FACTOR
          shiftYRange(if (orientation == Orientation.TOP_DOWN) deltaY else -deltaY)
        }
      }
    }
    addMouseWheelListener(adapter)
    addMouseListener(adapter)
    addMouseMotionListener(adapter)
  }

  private fun calculateMaximumHeight(): Int {
    if (root == null) {
      return 0
    }
    var maxDepth = -1
    val queue: Queue<N> = LinkedList()
    queue.add(root)
    while (!queue.isEmpty()) {
      val n = queue.poll()!!
      if (n.depth > maxDepth) {
        maxDepth = n.depth
      }
      for (i in 0 until n.childCount) {
        queue.add(n.getChildAt(i))
      }
    }
    maxDepth += 1
    // The HEIGHT_PADDING is for the chart's toe (the innermost frame on call stacks).
    // We have this because the padding near the chart's head (the outermost frame on call stacks)
    // is there because the root node of the tree is invisible.
    return (nodeHeight + nodeYPaddingPx) * maxDepth + HEIGHT_PADDING
  }

  class Builder<N : HNode<N>>(internal val root: N?,
                              // the range of the chart's visible area
                              internal val xRange: Range,
                              // a [<] which is responsible for rendering a single node.
                              internal val renderer: HRenderer<N>) {
    internal var orientation = Orientation.TOP_DOWN
    internal var globalXRange = Range(-Double.MAX_VALUE, Double.MAX_VALUE)
    internal var rootVisible = true
    internal var nodeSelectionEnabled = false
    internal var reducer: HTreeChartReducer<N> = DefaultHTreeChartReducer()
    internal var customNodeHeightPx = 0
    internal var nodeXPaddingPx = PADDING
    internal var nodeYPaddingPx = PADDING
    fun setOrientation(orientation: Orientation) = this.also {
      this.orientation = orientation
    }

    fun setRootVisible(visible: Boolean) = this.also {
      this.rootVisible = visible
    }

    fun setNodeSelectionEnabled(nodeSelectionEnabled: Boolean) = this.also {
      this.nodeSelectionEnabled = nodeSelectionEnabled
    }

    /**
     * @param globalXRange the bounding range of chart's visible area,
     * if it's not set, it assumes that there is no bounding range of chart.
     */
    fun setGlobalXRange(globalXRange: Range) = this.also {
      this.globalXRange = globalXRange
    }

    @VisibleForTesting
    fun setReducer(reducer: HTreeChartReducer<N>) = this.also {
      this.reducer = reducer
    }

    fun setCustomNodeHeightPx(customNodeHeightPx: Int) = this.also {
      this.customNodeHeightPx = customNodeHeightPx
    }

    fun setNodeXPaddingPx(nodeXPaddingPx: Int) = this.also {
      this.nodeXPaddingPx = nodeXPaddingPx
    }

    fun setNodeYPaddingPx(nodeYPaddingPx: Int) = this.also {
      this.nodeYPaddingPx = nodeYPaddingPx
    }

    fun build(): HTreeChart<N> = HTreeChart(this)
  }

  enum class Orientation { TOP_DOWN, BOTTOM_UP }

  companion object {
    private const val NO_HTREE = "No data available."
    private const val NO_RANGE = "X range width is zero: Please use a wider range."
    private const val ZOOM_FACTOR = 20
    private const val ACTION_ZOOM_IN = "zoom in"
    private const val ACTION_ZOOM_OUT = "zoom out"
    private const val ACTION_MOVE_LEFT = "move left"
    private const val ACTION_MOVE_RIGHT = "move right"
    private const val ACTION_MOVEMENT_FACTOR = 5

    @VisibleForTesting
    const val PADDING = 1
    private const val INITIAL_Y_POSITION = 0

    @VisibleForTesting
    const val HEIGHT_PADDING = 15
    private const val MOUSE_WHEEL_SCROLL_FACTOR = 8
    //private operator fun Rectangle2D.contains(p: Point) = p.getX() in minX..maxX && p.getY() in minY .. maxY
  }
}