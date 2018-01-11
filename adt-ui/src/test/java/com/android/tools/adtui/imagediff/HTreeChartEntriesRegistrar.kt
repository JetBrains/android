/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui.imagediff

import com.android.tools.adtui.chart.hchart.DefaultHRenderer
import com.android.tools.adtui.chart.hchart.HTreeChart
import com.android.tools.adtui.model.DefaultHNode
import com.android.tools.adtui.model.Range
import java.awt.Color
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.geom.Rectangle2D

private const val RANGE_MIN = 0.0
private const val RANGE_MAX = 100.0

private class HTreeChartEntriesRegistrar : ImageDiffEntriesRegistrar() {
  init {
    register(object : HTreeChartImageDiffEntry(HTreeChart.Orientation.TOP_DOWN, "htreechart_topdown_baseline.png") {
      override fun createNodeTree(): HTree {
        val root = HTreeNode("l0", 0, 100).apply {
          addChild(HTreeNode("l1", 0, 30).apply {
            addChild(HTreeNode("l11", 5, 20))
            addChild(HTreeNode("l12", 22, 30).apply {
              addChild(HTreeNode("l121", 24, 28))
            })
          })
          addChild(HTreeNode("l2", 40, 60))
          addChild(HTreeNode("l3", 60, 100).apply {
            addChild(HTreeNode("l31", 70, 90).apply {
              addChild(HTreeNode("l311", 75, 85).apply {
                addChild(HTreeNode("l3111", 78, 83))
              })
            })
          })
        }

        return HTree(root, "l12")
      }
    })

    register(object : HTreeChartImageDiffEntry(HTreeChart.Orientation.BOTTOM_UP, "htreechart_bottomup_baseline.png") {
      override fun createNodeTree(): HTree {
        val root = HTreeNode("l0", 0, 100).apply {
          addChild(HTreeNode("l1", 0, 30).apply {
            addChild(HTreeNode("l11", 5, 20))
            addChild(HTreeNode("l12", 22, 30).apply {
              addChild(HTreeNode("l121", 24, 28))
            })
          })
          addChild(HTreeNode("l2", 40, 60))
          addChild(HTreeNode("l3", 60, 100).apply {
            addChild(HTreeNode("l31", 70, 90).apply {
              addChild(HTreeNode("l311", 75, 85).apply {
                addChild(HTreeNode("l3111", 78, 83))
              })
            })
          })
        }

        return HTree(root, "l311")
      }
    })
  }

  /**
   * Create an [HTreeChart] with the range [RANGE_MIN] to [RANGE_MAX].
   */
  private abstract class HTreeChartImageDiffEntry(private val orientation: HTreeChart.Orientation, baselineFilename: String)
    : AnimatedComponentImageDiffEntry(baselineFilename) {

    protected class HTreeModel(val id: String)
    protected class HTreeNode(id: String, start: Long, end: Long) : DefaultHNode<HTreeModel>(HTreeModel(id)) {
      init {
        setStart(start)
        setEnd(end)
      }
    }
    protected class HTree(val root: HTreeNode, focusedId: String?) {
      private var _focusedNode: HTreeNode? = null
      val focusedNode
          get() = _focusedNode

      init {
        val nodes = mutableListOf(root)
        while (nodes.isNotEmpty()) {
          val node = nodes.removeAt(0)
          node.children.forEach { nodes.add(it as HTreeNode) }
          node.parent?.let { parent ->
            node.depth = parent.depth + 1
          }
          if (node.data.id == focusedId) {
            _focusedNode = node
          }
        }
      }
    }

    protected lateinit var chart: HTreeChart<DefaultHNode<HTreeModel>>

    override final fun setUp() {
      val fillColors = listOf(Color.WHITE, Color.LIGHT_GRAY, Color.DARK_GRAY)
      var colorIndex = 0
      val nextFillColor = { fillColors[(colorIndex++) % fillColors.size] }

      chart = HTreeChart(null, Range(RANGE_MIN, RANGE_MAX), orientation)
      chart.setHRenderer(object : DefaultHRenderer<HTreeModel>({ _ -> Color.YELLOW }) {
        // Don't draw any text because it doesn't compare well across platforms
        override fun generateFittingText(nodeData: HTreeModel, rect: Rectangle2D, fontMetrics: FontMetrics) = ""
        override fun getFillColor(nodeData: HTreeModel) = nextFillColor()
        override fun getBorderColor(nodeData: HTreeModel) = Color.BLACK
      })
      val nodeTree = createNodeTree()
      chart.setRootVisible(true)
      chart.setHTree(nodeTree.root)
      chart.setFocusedNode(nodeTree.focusedNode)

      myContentPane.add(chart)
    }

    override final fun generateTestData() = Unit
    override final fun generateComponent() {
      // Our hchart images are nowhere near as tall as the default size settings
      val reducedHeight = Dimension(myContentPane.size.width, myContentPane.size.height / 4)
      myContentPane.size = reducedHeight
      myContentPane.preferredSize = reducedHeight
    }

    abstract fun createNodeTree(): HTree
  }
}
