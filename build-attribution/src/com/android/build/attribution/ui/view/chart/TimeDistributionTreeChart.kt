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
package com.android.build.attribution.ui.view.chart

import com.google.common.annotations.VisibleForTesting
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.tree.TreePathUtil
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeModelAdapter
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point
import java.awt.Polygon
import java.awt.Rectangle
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JViewport
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.TreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath
import kotlin.math.max

private const val STACK_WIDTH_PX = 100
private const val STACK_LEFT_BORDER_PX = 100
private const val RIGHT_SELECTION_MARGIN_PX = 3
private const val FULL_WIDTH_PX = STACK_WIDTH_PX + STACK_LEFT_BORDER_PX + RIGHT_SELECTION_MARGIN_PX
private val MERGED_ITEMS_COLOR = Gray._189

class TimeDistributionTreeChart(
  val model: TimeDistributionTreeChartCalculationModel,
  val tree: Tree
) : JComponent() {

  private val treeModelListener = TreeModelAdapter.create { _, _ ->
    model.refreshModel()
    repaint()
  }

  private val treeExpansionListener = object : TreeExpansionListener {
    override fun treeExpanded(event: TreeExpansionEvent?) = repaint()
    override fun treeCollapsed(event: TreeExpansionEvent?) = repaint()
  }

  private val focusListener = object : FocusListener {
    override fun focusLost(e: FocusEvent?) = refreshSelectionArea()
    override fun focusGained(e: FocusEvent?) = refreshSelectionArea()
  }

  private val treeSelectionListener = TreeSelectionListener { refreshSelectionArea() }

  private fun refreshSelectionArea() {
    model.refreshSelectionArea(tree.selectionPath, tree.isFocusOwner)
    repaint()
  }

  private val mouseListener = object : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent) {
      model.itemByCoordinates(e.point)?.let {
        tree.selectionModel.selectionPath = it.treePath
        tree.requestFocusInWindow()
      }
    }

    override fun mouseMoved(e: MouseEvent) {
      if (model.hoveredItem?.contains(e.point) == true) return
      model.hoveredItem = model.itemByCoordinates(e.point)
      repaint()
    }

    override fun mouseExited(e: MouseEvent) {
      model.hoveredItem = null
      repaint()
    }
  }

  init {
    background = UIUtil.getTreeBackground()

    tree.model.addTreeModelListener(treeModelListener)
    tree.addTreeExpansionListener(treeExpansionListener)
    tree.addFocusListener(focusListener)
    tree.addTreeSelectionListener(treeSelectionListener)

    addMouseListener(mouseListener)
    addMouseMotionListener(mouseListener)
  }

  private fun Dimension.makeFullWidth() = JBDimension.size(this)
    .withWidth(FULL_WIDTH_PX)

  override fun getPreferredSize(): Dimension = tree.getPreferredSize().makeFullWidth()
  override fun getMinimumSize(): Dimension = tree.getMinimumSize().makeFullWidth()
  override fun getMaximumSize(): Dimension = tree.getMaximumSize().makeFullWidth()

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    model.recalculateCoordinates(visibleRect)

    GraphicsUtil.setupAntialiasing(g)
    GraphicsUtil.setupAAPainting(g)

    model.selectionArea.draw(g)
    model.chartItems.forEach { it.draw(g) }
    model.mergedItemsBar.draw(g)
  }

  companion object {
    fun wrap(tree: Tree): JComponent {
      val model = TimeDistributionTreeChartCalculationModel(tree.model) { treePath -> tree.getPathBounds(treePath) }
      return MyScrollPane(tree, TimeDistributionTreeChart(model, tree))
    }
  }

  private class MyScrollPane(
    private val tree: Tree,
    chart: TimeDistributionTreeChart
  ) : JBScrollPane(tree, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED) {
    init {
      setRowHeaderView(chart)
      rowHeader.scrollMode = JViewport.SIMPLE_SCROLL_MODE
      border = JBUI.Borders.empty()
      layout = object : Layout() {
        override fun layoutContainer(parent: Container?) {
          super.layoutContainer(parent)
          // Re-Arrange components horizontally. We don't expect and support any other order.
          viewport?.apply { setLocation(0, y) }
          rowHead?.apply { setLocation(viewport.width - tree.insets.right, y) }
          horizontalScrollBar?.apply { setLocation(0, y) }
        }
      }
    }
  }
}

interface ChartDrawableElement {
  fun draw(g: Graphics)
}

interface ChartValueProvider : TreeNode {
  val relativeWeight: Double
  val itemColor: Color
}

/**
 * This class abstracts away chart objects coordinates calculation from the component.
 * It retrieves data from the provided tree model and expects nodes to inherit ChartValueProvider.
 * It does not observe any changes itself, all recalculations should be triggered by the client.
 */
class TimeDistributionTreeChartCalculationModel(
  private val treeModel: TreeModel,
  private val treePathToCoordinates: (TreePath) -> Rectangle?
) {

  var chartItems: List<ChartRowItem> = emptyList()
  var hoveredItem: ChartRowItem? = null
  val mergedItemsBar = MergedItemsBar()
  val selectionArea = ChartSelectionArea()

  init {
    refreshModel()
  }

  // Common horizontal coordinates and sizes that are shared between all the elements.
  // These values are effectively constants but need to be recalculated to support scaling.
  var rowColorBulletSizeScaledPx: Int = 0
  var leftLineBreakingPointScaledPx: Int = 0
  var rightLineBreakingPointScaledPx: Int = 0
  var stackLeftBorderScaledPx: Int = 0
  var stackWidthScaledPx: Int = 0
  var selectionRightPointScaledPx: Int = 0
  private var stackBottomMarginScaledPx: Int = 0
  var stackBarsSpacingScaledPx: Int = 0
  var minStackBarSizeScaledPx: Int = 0

  private fun calculateScaledConstantValues() {
    // Mind the order of values calculation so that value is not used before it is recalculated!
    rowColorBulletSizeScaledPx = JBUIScale.scale(10)
    stackLeftBorderScaledPx = JBUIScale.scale(STACK_LEFT_BORDER_PX)
    stackWidthScaledPx = JBUIScale.scale(STACK_WIDTH_PX)
    selectionRightPointScaledPx = JBUIScale.scale(FULL_WIDTH_PX)
    stackBottomMarginScaledPx = JBUIScale.scale(20)
    stackBarsSpacingScaledPx = JBUIScale.scale(1)
    minStackBarSizeScaledPx = JBUIScale.scale(4)

    leftLineBreakingPointScaledPx = rowColorBulletSizeScaledPx + JBUIScale.scale(4)
    rightLineBreakingPointScaledPx = stackLeftBorderScaledPx - JBUIScale.scale(4)
  }

  /**
   * Recalculate all drawable items coordinates to the current visible state.
   * To be called on every paint before drawing any elements.
   */
  fun recalculateCoordinates(visibleRect: Rectangle) {
    mergedItemsBar.mergedItems.clear()

    calculateScaledConstantValues()

    val availableStackHeight = visibleRect.height - stackBottomMarginScaledPx
    val pxPerPercent = availableStackHeight.toDouble() / 100
    var curY = visibleRect.y
    chartItems.forEach {
      curY = it.recalculateCoordinates(pxPerPercent, curY)
    }
    curY = mergedItemsBar.recalculateCoordinates(pxPerPercent, curY)
    selectionArea.recalculateCoordinates()
  }

  /**
   * Load new data from the TreeModel and rebuild chart items.
   * To be called when tree structure changes.
   */
  @VisibleForTesting
  fun refreshModel() {
    val firstLevelNodes = (treeModel.root as TreeNode).children()
      .asSequence()
      .filterIsInstance<ChartValueProvider>()
      .toList()

    // Calculate Stack items
    val itemsSum = firstLevelNodes.sumOf { it.relativeWeight }

    chartItems = firstLevelNodes.map { node ->
      val color = node.itemColor
      val treePath = TreePathUtil.toTreePath(node)

      val itemPercentage = 100 * node.relativeWeight / itemsSum
      ChartRowItem(treePath, itemPercentage, color)
    }
  }

  fun refreshSelectionArea(selectedPath: TreePath?, isFocused: Boolean) {
    if (selectedPath == null) {
      selectionArea.selectedChartRowItem = null
      selectionArea.selectionColor = null
    }
    else {
      selectionArea.selectedChartRowItem = chartItems.find { selectedPath == it.treePath }
      selectionArea.selectionColor = UIUtil.getTreeSelectionBackground(isFocused)
    }
  }

  fun itemByCoordinates(p: Point): ChartRowItem? = chartItems.firstOrNull { it.contains(p) }

  /**
   * Selection area that is drawn under the currently selected [ChartRowItem].
   */
  inner class ChartSelectionArea : ChartDrawableElement {
    var selectedChartRowItem: ChartRowItem? = null
    var selectionColor: Color? = null

    var polygon: Polygon? = null

    fun recalculateCoordinates() {
      polygon = selectedChartRowItem?.toSelectionAreaPolygon()
    }

    override fun draw(g: Graphics) {
      polygon?.let {
        g.color = selectionColor
        g.fillPolygon(it)
      }
    }
  }

  /**
   * Chart item that represents corresponding node on the tree.
   * Consists of bullet point attached to a corresponding tree row, area on the stack chart and line connecting them.
   * If item is too small to be shown separately on the chart then it is added to the special merged element [MergedItemsBar].
   * In this case only row bullet point is drawn and it is half gray to match the 'merged' state.
   */
  inner class ChartRowItem(
    val treePath: TreePath,
    /* 0 to 1 */
    val itemNormalizedHeightPercentage: Double,
    private val keyColor: Color
  ) : ChartDrawableElement {

    private val shadowColor = JBColor(keyColor.darker(), keyColor.brighter())

    val selected: Boolean
      get() = selectionArea.selectedChartRowItem == this

    private val hovered: Boolean
      get() = hoveredItem == this

    var treeRowY: Int = 0
    var treeRowHeight: Int = 0
    var stackBarY: Int = 0
    var stackBarHeight: Int = 0
    private var clickHandlingArea: Polygon? = null

    /**
     * [curY] start position on the stack chart for this element.
     * Returns new position on the stack for the next element. Should be increased by height allocated for this element.
     */
    fun recalculateCoordinates(stackHeightPxPerPercent: Double, curY: Int): Int {
      val rowBounds = treePathToCoordinates(treePath)
      if (rowBounds == null) {
        // Null means that node is hidden under collapsed parent.
        // This can not normally happen in our scenario as we are working with first-level nodes.
        treeRowY = 0
        treeRowHeight = 0
      }
      else {
        treeRowY = rowBounds.y
        treeRowHeight = rowBounds.height
      }

      stackBarY = curY + stackBarsSpacingScaledPx
      stackBarHeight = (stackHeightPxPerPercent * itemNormalizedHeightPercentage).toInt() - stackBarsSpacingScaledPx

      clickHandlingArea = toSelectionAreaPolygon()
      if (!shownAsSeparateBar) {
        mergedItemsBar.mergedItems.add(this)
        return curY
      }
      else {
        return stackBarY + stackBarHeight
      }
    }

    override fun draw(g: Graphics) {
      drawBullet(g)
      if (shownAsSeparateBar) {
        drawStackBar(g)
        if (!selected) {
          drawConnectorLine(g)
        }
      }
    }

    private fun drawBullet(g: Graphics) {
      val size = rowColorBulletSizeScaledPx
      val y = treeRowY + (treeRowHeight - size) / 2

      g.color = keyColor
      g.fillRect(0, y, size, size)
      if (!shownAsSeparateBar) {
        g.color = MERGED_ITEMS_COLOR
        g.fillPolygon(intArrayOf(0, size, size), intArrayOf(y + size, y + size, y), 3)
      }
      if (hovered) {
        g.color = shadowColor
        g.drawRect(0, y, size, size)
      }
    }

    private fun drawStackBar(g: Graphics) {
      g.color = keyColor
      g.fillRect(stackLeftBorderScaledPx, stackBarY, stackWidthScaledPx, stackBarHeight)

      if (hovered) {
        g.color = shadowColor
        g.drawRect(stackLeftBorderScaledPx, stackBarY, stackWidthScaledPx, stackBarHeight)
      }
    }

    private fun drawConnectorLine(g: Graphics) {
      val leftMidY = treeRowY + treeRowHeight / 2
      val rightMidY = stackBarY + stackBarHeight / 2

      g.color = if (hovered) shadowColor else keyColor
      g.drawPolyline(
        intArrayOf(rowColorBulletSizeScaledPx, leftLineBreakingPointScaledPx, rightLineBreakingPointScaledPx, stackLeftBorderScaledPx),
        intArrayOf(leftMidY, leftMidY, rightMidY, rightMidY),
        4
      )
    }

    fun toSelectionAreaPolygon(): Polygon = Polygon().apply {
      val leftTopY: Int = treeRowY
      val leftBottomY: Int = leftTopY + treeRowHeight
      addPoint(0, leftTopY)
      addPoint(leftLineBreakingPointScaledPx, leftTopY)
      if (shownAsSeparateBar) {
        val rightTopY: Int = stackBarY - stackBarsSpacingScaledPx
        val rightBottomY: Int = rightTopY + stackBarHeight + 2 * stackBarsSpacingScaledPx
        addPoint(rightLineBreakingPointScaledPx, rightTopY)
        addPoint(selectionRightPointScaledPx, rightTopY)
        addPoint(selectionRightPointScaledPx, rightBottomY)
        addPoint(rightLineBreakingPointScaledPx, rightBottomY)
      }
      addPoint(leftLineBreakingPointScaledPx, leftBottomY)
      addPoint(0, leftBottomY)
    }

    fun contains(p: Point): Boolean = clickHandlingArea?.contains(p) ?: false

    val shownAsSeparateBar: Boolean
      get() = stackBarHeight >= minStackBarSizeScaledPx
  }

  /**
   * Special part on the stack chart that represents all items that are too small to be shown separately on the chart.
   */
  inner class MergedItemsBar : ChartDrawableElement {

    val mergedItems = mutableListOf<ChartRowItem>()

    var posY: Int = 0
    var heightPx: Int = 0

    fun recalculateCoordinates(stackHeightPxPerPercent: Double, curY: Int): Int {
      posY = 0
      heightPx = 0
      if (mergedItems.isNotEmpty()) {
        val accumulatedNormalizedPercentageHeight = mergedItems.sumOf { it.itemNormalizedHeightPercentage }
        val stackBarHeightPx = (stackHeightPxPerPercent * accumulatedNormalizedPercentageHeight).toInt() - stackBarsSpacingScaledPx
        posY = curY + stackBarsSpacingScaledPx
        heightPx = max(stackBarHeightPx, minStackBarSizeScaledPx)
        return posY + heightPx
      }
      else {
        return curY
      }
    }

    override fun draw(g: Graphics) {
      if (heightPx > 0) {
        g.color = MERGED_ITEMS_COLOR
        g.fillRect(stackLeftBorderScaledPx, posY, stackWidthScaledPx, heightPx)
      }
    }
  }
}