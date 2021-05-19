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

import com.google.common.truth.Truth
import com.intellij.ui.tree.TreePathUtil
import org.junit.Test
import java.awt.Color
import java.awt.Rectangle
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class TimeDistributionTreeChartCalculationModelTest {

  private val tenEqualNodes = DefaultMutableTreeNode().apply {
    // 10 nodes with equal value, sum is 100.
    add(FakeTreeNode(10.0))
    add(FakeTreeNode(10.0))
    add(FakeTreeNode(10.0))
    add(FakeTreeNode(10.0))
    add(FakeTreeNode(10.0))
    add(FakeTreeNode(10.0))
    add(FakeTreeNode(10.0))
    add(FakeTreeNode(10.0))
    add(FakeTreeNode(10.0))
    add(FakeTreeNode(10.0))
  }

  private val fiveDecreasingNodes = DefaultMutableTreeNode().apply {
    // Sum is 10.
    add(FakeTreeNode(5.0))
    add(FakeTreeNode(3.0))
    add(FakeTreeNode(1.0))
    add(FakeTreeNode(0.5))
    add(FakeTreeNode(0.5))
  }

  private val twoLevelNodes = DefaultMutableTreeNode().apply {
    // First level nodes sum is 10.
    add(FakeTreeNode(5.0).apply {
      add(FakeTreeNode(3.0))
      add(FakeTreeNode(2.0))
    })
    add(FakeTreeNode(3.0).apply {
      add(FakeTreeNode(3.0))
    })
    add(FakeTreeNode(2.0))
  }

  @Test
  fun testCoordinatesCalculated() {
    val treeChartModel = createChartModel(tenEqualNodes, 20)

    treeChartModel.recalculateCoordinates(Rectangle(0, 0, 200, 320))

    // Total available height is 320, stack height is 300, 30 for each element. 1px goes for the items spacing
    val expectedItemCoordinates = """
      0,20 - 1,29
      20,20 - 31,29
      40,20 - 61,29
      60,20 - 91,29
      80,20 - 121,29
      100,20 - 151,29
      120,20 - 181,29
      140,20 - 211,29
      160,20 - 241,29
      180,20 - 271,29
    """.trimIndent()
    val coordinates = dumpCoordinates(treeChartModel)
    Truth.assertThat(coordinates).isEqualTo(expectedItemCoordinates)
  }

  @Test
  fun testCoordinatesCalculatedForBiggerVisibleRect() {
    val treeChartModel = createChartModel(tenEqualNodes, 20)

    treeChartModel.recalculateCoordinates(Rectangle(0, 0, 200, 1020))

    // Total available height is 1020, stack height is 1000, 100 for each element. 1px goes for the items spacing
    val expectedItemCoordinates = """
      0,20 - 1,99
      20,20 - 101,99
      40,20 - 201,99
      60,20 - 301,99
      80,20 - 401,99
      100,20 - 501,99
      120,20 - 601,99
      140,20 - 701,99
      160,20 - 801,99
      180,20 - 901,99
    """.trimIndent()
    val coordinates = dumpCoordinates(treeChartModel)
    Truth.assertThat(coordinates).isEqualTo(expectedItemCoordinates)
  }

  @Test
  fun testCoordinatesCalculatedForSmallerVisibleRectAndSmallerTreeRows() {
    val treeChartModel = createChartModel(tenEqualNodes, 10)

    treeChartModel.recalculateCoordinates(Rectangle(0, 0, 200, 120))

    // Total available height is 120, stack height is 100, 10 for each element. 1px goes for the items spacing
    val expectedItemCoordinates = """
      0,10 - 1,9
      10,10 - 11,9
      20,10 - 21,9
      30,10 - 31,9
      40,10 - 41,9
      50,10 - 51,9
      60,10 - 61,9
      70,10 - 71,9
      80,10 - 81,9
      90,10 - 91,9
    """.trimIndent()
    val coordinates = dumpCoordinates(treeChartModel)
    Truth.assertThat(coordinates).isEqualTo(expectedItemCoordinates)
  }

  @Test
  fun testCoordinatesCalculatedWhenVisibleAreaIsTooSmall() {
    val treeChartModel = createChartModel(tenEqualNodes, 10)

    treeChartModel.recalculateCoordinates(Rectangle(0, 0, 200, 50))

    // Total available height is 50, stack height is 30, 3 for each element.
    // It is below min bar size so all items should be merged.
    Truth.assertThat(treeChartModel.chartItems.map { it.shownAsSeparateBar })
      .isEqualTo(listOf(false, false, false, false, false, false, false, false, false, false))

    Truth.assertThat(treeChartModel.mergedItemsBar.mergedItems).hasSize(10)
    Truth.assertThat(treeChartModel.mergedItemsBar.posY).isEqualTo(1)
    Truth.assertThat(treeChartModel.mergedItemsBar.heightPx).isEqualTo(29)
  }

  @Test
  fun testCoordinatesCalculatedForDecreasingNodes() {
    val treeChartModel = createChartModel(fiveDecreasingNodes, 20)

    treeChartModel.recalculateCoordinates(Rectangle(0, 0, 200, 320))

    // Total available height is 320, stack height is 300, 3px per percent.
    // Heights: 50%, 30%, 10%, 5%, 5%
    val expectedItemCoordinates = """
      0,20 - 1,149
      20,20 - 151,89
      40,20 - 241,29
      60,20 - 271,14
      80,20 - 286,14
    """.trimIndent()
    val coordinates = dumpCoordinates(treeChartModel)
    Truth.assertThat(coordinates).isEqualTo(expectedItemCoordinates)
  }

  @Test
  fun testCoordinatesCalculatedForDecreasingNodesForSmallerVisibleRect() {
    val treeChartModel = createChartModel(fiveDecreasingNodes, 20)

    treeChartModel.recalculateCoordinates(Rectangle(0, 0, 200, 70))

    // Total available height is 70, stack height is 50, 0.5px per percent.
    // Heights: 50% - 25, 30% - 15, 10% - 5, 5% - 2.5 (Merged), 5% - 2.5 (Merged)
    val expectedItemCoordinates = """
      0,20 - 1,24
      20,20 - 26,14
      40,20 - 41,4
      60,20 - 46,1
      80,20 - 46,1
    """.trimIndent()
    val coordinates = dumpCoordinates(treeChartModel)
    Truth.assertThat(coordinates).isEqualTo(expectedItemCoordinates)

    // Last two bars should be merged being below minimal size.
    Truth.assertThat(treeChartModel.chartItems.map { it.shownAsSeparateBar })
      .isEqualTo(listOf(true, true, true, false, false))

    Truth.assertThat(treeChartModel.mergedItemsBar.mergedItems).hasSize(2)
    Truth.assertThat(treeChartModel.mergedItemsBar.posY).isEqualTo(46)
    Truth.assertThat(treeChartModel.mergedItemsBar.heightPx).isEqualTo(4)
  }

  @Test
  fun testCoordinatesCalculatedForVisibleAreaScrolledDown() {
    val treeChartModel = createChartModel(fiveDecreasingNodes, 20)

    treeChartModel.recalculateCoordinates(Rectangle(0, 100, 200, 320))

    // Total available height is 320, stack height is 300, 3px per percent.
    // Heights: 50%, 30%, 10%, 5%, 5%
    // When area is scrolled down left coordinates should stay the same (attached to the tree rows)
    // and right stack part should move together with the visible rect.
    val expectedItemCoordinates = """
      0,20 - 101,149
      20,20 - 251,89
      40,20 - 341,29
      60,20 - 371,14
      80,20 - 386,14
    """.trimIndent()
    val coordinates = dumpCoordinates(treeChartModel)
    Truth.assertThat(coordinates).isEqualTo(expectedItemCoordinates)
  }

  @Test
  fun testCoordinatesCalculatedForTwoLevelTree() {
    val treeChartModel = createChartModel(twoLevelNodes, 20)

    treeChartModel.recalculateCoordinates(Rectangle(0, 0, 200, 320))

    // Total available height is 320, stack height is 300, 3px per percent.
    // In this tree we have the following structure and values:
    // - 5 (1st row, 50%, 150px)
    //    - 3
    //    - 2
    // - 3 (4th row, 30%, 90px)
    //    - 3
    // - 2 (6th row, 20%, 60px)
    val expectedItemCoordinates = """
      0,20 - 1,149
      60,20 - 151,89
      100,20 - 241,59
    """.trimIndent()
    val coordinates = dumpCoordinates(treeChartModel)
    Truth.assertThat(coordinates).isEqualTo(expectedItemCoordinates)
  }

  @Test
  fun testChangingNodesInTreeModelIsApplied() {
    val treeModel = DefaultTreeModel(twoLevelNodes)
    val treeChartModel = TimeDistributionTreeChartCalculationModel(treeModel, rowBoundsProvider(twoLevelNodes, 20))

    // Verify initial state.
    Truth.assertThat(treeChartModel.chartItems).hasSize(3)

    // Update tree structure.
    treeModel.setRoot(tenEqualNodes)
    treeChartModel.refreshModel()

    // Verify treeChartModel state updated.
    Truth.assertThat(treeChartModel.chartItems).hasSize(10)
  }

  @Test
  fun testSelectionAreaUpdate() {
    val treeChartModel = createChartModel(twoLevelNodes, 20)

    // Verify initially empty.
    Truth.assertThat(treeChartModel.selectionArea.selectedChartRowItem).isNull()

    val pathToSelect = TreePathUtil.pathToTreeNode(twoLevelNodes.firstChild)
    treeChartModel.refreshSelectionArea(pathToSelect, true)

    Truth.assertThat(treeChartModel.selectionArea.selectedChartRowItem).isEqualTo(treeChartModel.chartItems.first())

    treeChartModel.refreshSelectionArea(null, true)

    Truth.assertThat(treeChartModel.selectionArea.selectedChartRowItem).isNull()
  }

  @Test
  fun testSelectionAreaCalculationForFirstNode() {
    val treeChartModel = createChartModel(twoLevelNodes, 20)
    val pathToSelect = TreePathUtil.pathToTreeNode(twoLevelNodes.firstChild)
    treeChartModel.refreshSelectionArea(pathToSelect, true)

    treeChartModel.recalculateCoordinates(Rectangle(0, 0, 200, 320))
    Truth.assertThat(treeChartModel.selectionArea.polygon?.contains(0, 0)).isTrue()
    Truth.assertThat(treeChartModel.selectionArea.polygon?.contains(0, 19)).isTrue()
    Truth.assertThat(treeChartModel.selectionArea.polygon?.contains(202, 0)).isTrue()
    Truth.assertThat(treeChartModel.selectionArea.polygon?.contains(202, 150)).isTrue()
  }

  @Test
  fun testSelectionAreaCalculationForLastNode() {
    val treeChartModel = createChartModel(fiveDecreasingNodes, 20)
    val pathToSelect = TreePathUtil.pathToTreeNode(fiveDecreasingNodes.lastChild)
    treeChartModel.refreshSelectionArea(pathToSelect, true)

    // Last node chart item coordinates in this case are (80,20 - 286,14).
    // Selection area should go all around it.
    treeChartModel.recalculateCoordinates(Rectangle(0, 0, 200, 320))
    Truth.assertThat(treeChartModel.selectionArea.polygon?.contains(0, 80)).isTrue()
    Truth.assertThat(treeChartModel.selectionArea.polygon?.contains(0, 99)).isTrue()
    Truth.assertThat(treeChartModel.selectionArea.polygon?.contains(202, 285)).isTrue()
    Truth.assertThat(treeChartModel.selectionArea.polygon?.contains(202, 300)).isTrue()
  }

  @Test
  fun testSelectionAreaCalculationForMergedChartItem() {
    val treeChartModel = createChartModel(fiveDecreasingNodes, 20)
    val pathToSelect = TreePathUtil.pathToTreeNode(fiveDecreasingNodes.lastChild)
    treeChartModel.refreshSelectionArea(pathToSelect, true)

    // Total available height is 70, stack height is 50, 0.5px per percent.
    // Last node has 5%, making it too small to be show separately.
    // Selection area should only highlight tree row part in this case.
    treeChartModel.recalculateCoordinates(Rectangle(0, 0, 200, 70))
    Truth.assertThat(treeChartModel.selectionArea.polygon?.contains(0, 80)).isTrue()
    Truth.assertThat(treeChartModel.selectionArea.polygon?.contains(0, 99)).isTrue()
    Truth.assertThat(treeChartModel.selectionArea.polygon?.contains(13, 80)).isTrue()
    Truth.assertThat(treeChartModel.selectionArea.polygon?.contains(13, 99)).isTrue()
    Truth.assertThat(treeChartModel.selectionArea.polygon?.contains(14, 80)).isFalse()
    Truth.assertThat(treeChartModel.selectionArea.polygon?.contains(14, 99)).isFalse()
  }

  private fun dumpCoordinates(treeChartModel: TimeDistributionTreeChartCalculationModel): String {
    return treeChartModel.chartItems.joinToString(separator = "\n") {
      "${it.treeRowY},${it.treeRowHeight} - ${it.stackBarY},${it.stackBarHeight}"
    }
  }

  private fun createChartModel(root: DefaultMutableTreeNode, rowHeight: Int): TimeDistributionTreeChartCalculationModel {
    val treeModel = DefaultTreeModel(root)
    return TimeDistributionTreeChartCalculationModel(treeModel, rowBoundsProvider(root, rowHeight))
  }

  private fun rowBoundsProvider(root: DefaultMutableTreeNode, rowHeight: Int): ((TreePath) -> Rectangle?) = { treePath: TreePath ->
    val nodesBefore = root.preorderEnumeration().asSequence().takeWhile { treePath.lastPathComponent != it }.count() - 1
    Rectangle(0, nodesBefore * rowHeight, 100, rowHeight)
  }

  class FakeTreeNode(
    override val relativeWeight: Double,
    override val itemColor: Color = Color.BLACK
  ) : DefaultMutableTreeNode(), ChartValueProvider
}