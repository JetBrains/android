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
import com.android.build.attribution.ui.model.TasksDataPageModelImpl
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.google.common.truth.Truth
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.treeStructure.Tree
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.Dimension
import java.awt.Graphics2D
import javax.swing.tree.DefaultTreeModel

@RunsInEdt
class TimeDistributionTreeChartTest {

  @get:Rule
  val applicationRule: ApplicationRule = ApplicationRule()

  @get:Rule
  val edtRule = EdtRule()

  val task1 = mockTask(":app", "compile", "compiler.plugin", 2000)
  val task2 = mockTask(":app", "resources", "resources.plugin", 1000)
  val task3 = mockTask(":lib", "compile", "compiler.plugin", 1000)

  val data = MockUiData(tasksList = listOf(task1, task2, task3))

  @Test
  @Ignore("b/163480652")
  fun testTasksChartIsRendered() {
    val model = TasksDataPageModelImpl(data)
    val tree = Tree(DefaultTreeModel(model.treeRoot)).apply {
      isRootVisible = false
    }
    val treeWithChart = TimeDistributionTreeChart.wrap(tree)
    val scrollPane = ScrollPaneFactory.createScrollPane(treeWithChart, SideBorder.NONE)
    scrollPane.size = Dimension(600, 300)
    val fakeUi = FakeUi(scrollPane)

    val chart = TreeWalker(treeWithChart).descendants().filterIsInstance(TimeDistributionTreeChart::class.java).single()

    val fakeGraphics = Mockito.mock(Graphics2D::class.java)
    Mockito.`when`(fakeGraphics.create()).thenReturn(fakeGraphics)
    chart.paint(fakeGraphics)

    Truth.assertThat(chart.model.chartItems).hasSize(3)

    // TODO(b/163480652): Add rendering tests for new Visualization (using ImageDiffUtil)
  }
}