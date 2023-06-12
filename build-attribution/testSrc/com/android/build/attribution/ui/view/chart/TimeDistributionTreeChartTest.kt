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

import com.android.build.attribution.ui.MockUiData
import com.android.build.attribution.ui.mockTask
import com.android.build.attribution.ui.model.TasksDataPageModel
import com.android.build.attribution.ui.model.TasksDataPageModelImpl
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.google.common.truth.Expect
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.treeStructure.Tree
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import javax.swing.tree.DefaultTreeModel

@RunsInEdt
class TimeDistributionTreeChartTest {

  @get:Rule
  val applicationRule: ApplicationRule = ApplicationRule()

  @get:Rule
  val expect = Expect.createAndEnableStackTrace()!!

  @get:Rule
  val edtRule = EdtRule()

  private val task1 = mockTask(":app", "compile", "compiler.plugin", 2000)
  private val task2 = mockTask(":app", "resources", "resources.plugin", 1000)
  private val task3 = mockTask(":lib", "compile", "compiler.plugin", 1000)

  private val data = MockUiData(tasksList = listOf(task1, task2, task3))

  private val model = TasksDataPageModelImpl(data).also {
    it.selectGrouping(TasksDataPageModel.Grouping.UNGROUPED)
  }

  private val tree = Tree(DefaultTreeModel(model.treeRoot)).apply {
    isRootVisible = false
  }

  private val treeWithChart = TimeDistributionTreeChart.wrap(tree)
  private val fakeUi = FakeUi(ScrollPaneFactory.createScrollPane(treeWithChart, SideBorder.NONE))

  @Test
  fun testClicksHandledOnStack() {
    fakeUi.root.size = Dimension(600, 400)
    fakeUi.layoutAndDispatchEvents()
    fakeUi.render()

    fakeUi.mouse.click(550, 100)
    expect.that(tree.leadSelectionRow).isEqualTo(0)

    fakeUi.mouse.click(550, 350)
    expect.that(tree.leadSelectionRow).isEqualTo(2)
  }

  @Test
  fun testClicksHandledOnRows() {
    fakeUi.root.size = Dimension(600, 400)
    fakeUi.layoutAndDispatchEvents()
    fakeUi.render()

    val chart = TreeWalker(treeWithChart).descendants().filterIsInstance(TimeDistributionTreeChart::class.java).single()
    val chartX = fakeUi.getPosition(chart).x

    fakeUi.mouse.click(chartX + 2, rowMidY(0))
    expect.that(tree.leadSelectionRow).isEqualTo(0)

    fakeUi.mouse.click(chartX + 2, rowMidY(2))
    expect.that(tree.leadSelectionRow).isEqualTo(2)
  }

  @Test
  fun testMouseHoverIsDetected() {
    fakeUi.root.size = Dimension(600, 400)
    fakeUi.layoutAndDispatchEvents()
    fakeUi.render()
    val chart = TreeWalker(treeWithChart).descendants().filterIsInstance(TimeDistributionTreeChart::class.java).single()

    fakeUi.mouse.moveTo(550, 100)

    expect.that(chart.model.hoveredItem).isEqualTo(chart.model.chartItems[0])

    fakeUi.mouse.moveTo(550, 350)
    expect.that(chart.model.hoveredItem).isEqualTo(chart.model.chartItems[2])

    val chartX = fakeUi.getPosition(chart).x
    fakeUi.mouse.moveTo(chartX + 5, 350)
    expect.that(chart.model.hoveredItem).isEqualTo(null)

    fakeUi.mouse.moveTo(chartX + 5, rowMidY(1))
    expect.that(chart.model.hoveredItem).isEqualTo(chart.model.chartItems[1])

    fakeUi.mouse.moveTo(chartX - 100, rowMidY(1))
    expect.that(chart.model.hoveredItem).isEqualTo(null)
  }

  private fun rowMidY(row: Int) = with(tree.getRowBounds(row)) { y + height / 2 }
}
