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
package com.android.tools.idea.appinspection.inspectors.network.view

import com.android.tools.adtui.AxisComponent
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.stdui.TooltipLayeredPane
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.laf.HeadlessTableUI
import com.android.tools.idea.appinspection.inspectors.network.model.FakeCodeNavigationProvider
import com.android.tools.idea.appinspection.inspectors.network.model.FakeNetworkInspectorDataSource
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorModel
import com.android.tools.idea.appinspection.inspectors.network.model.TestNetworkInspectorServices
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpDataModel
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.JavaThread
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.createFakeHttpData
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import java.awt.Component
import java.awt.Dimension
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import javax.swing.JTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private val FAKE_DATA: ImmutableList<HttpData> =
  ImmutableList.Builder<HttpData>()
    .add(newData(1, 1, 10, 11, "threadA"))
    .add(newData(2, 5, 12, 12, "threadB"))
    .add(newData(3, 13, 15, 11, "threadA"))
    .add(newData(4, 20, 25, 11, "threadA"))
    .add(newData(5, 14, 21, 12, "threadB"))
    .add(newData(11, 100, 110, 13, "threadC"))
    .add(newData(12, 115, 120, 14, "threadC"))
    .build()

private fun newData(
  id: Long,
  startS: Long,
  endS: Long,
  threadId: Long,
  threadName: String
): HttpData {
  return createFakeHttpData(
    id,
    TimeUnit.SECONDS.toMicros(startS),
    TimeUnit.SECONDS.toMicros(startS),
    TimeUnit.SECONDS.toMicros(endS),
    TimeUnit.SECONDS.toMicros(endS),
    TimeUnit.SECONDS.toMicros(endS),
    listOf(JavaThread(threadId, threadName))
  )
}

private fun JTable.getFirstHttpDataAtRow(row: Int): HttpData =
  (getValueAt(row, 1) as List<*>).first() as HttpData

@RunsInEdt
class ThreadsViewTest {
  private val timer = FakeTimer()

  @get:Rule val edtRule = EdtRule()

  @get:Rule val projectRule = ProjectRule()

  private lateinit var model: NetworkInspectorModel
  private lateinit var inspectorView: NetworkInspectorView
  private lateinit var threadsView: ThreadsView
  private lateinit var fakeUi: FakeUi
  private lateinit var table: JTable
  private lateinit var scope: CoroutineScope

  @Before
  fun setUp() {
    scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())
    val codeNavigationProvider = FakeCodeNavigationProvider()
    val services = TestNetworkInspectorServices(codeNavigationProvider, timer)
    model =
      NetworkInspectorModel(
        services,
        FakeNetworkInspectorDataSource(),
        scope,
        object : HttpDataModel {
          private val dataList = FAKE_DATA
          override fun getData(timeCurrentRangeUs: Range): List<HttpData> {
            return dataList.filter {
              it.requestStartTimeUs >= timeCurrentRangeUs.min &&
                it.requestStartTimeUs <= timeCurrentRangeUs.max
            }
          }
        }
      )
    val parentPanel = JPanel()
    val component = TooltipLayeredPane(parentPanel)
    inspectorView =
      NetworkInspectorView(
        projectRule.project,
        model,
        FakeUiComponentsProvider(),
        component,
        services,
        scope
      )
    parentPanel.add(inspectorView.component)

    threadsView = ThreadsView(model, component)
    threadsView.component.size = Dimension(300, 50)
    table =
      TreeWalker(threadsView.component)
        .descendantStream()
        .filter { c -> c is JTable }
        .findFirst()
        .get() as
        JTable
    table.setUI(HeadlessTableUI())
    // Normally, when ThreadsView changes size, it updates the size of its table which in turn
    // fires an event that updates the preferred size of its columns. This requires multiple layout
    // passes, as well as firing a event that happens on another thread, so the timing is not
    // deterministic. For testing, we short-circuit the process and set the size of the table
    // directly, so when the FakeUi is created below (which performs a layout pass), the table will
    // already be in its final size.
    table.size = threadsView.component.size
    fakeUi = FakeUi(threadsView.component)
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun showsCorrectThreadData() {
    val selection = model.timeline.selectionRange
    selection[0.0] = TimeUnit.SECONDS.toMicros(22).toDouble()
    assertThat(table.model.rowCount).isEqualTo(2)
    assertThat(table.model.getValueAt(0, 0)).isEqualTo("threadA")
    assertThat(table.model.getValueAt(0, 1) as List<*>)
      .containsExactly(FAKE_DATA[0], FAKE_DATA[2], FAKE_DATA[3])
    assertThat(table.model.getValueAt(1, 0)).isEqualTo("threadB")
    assertThat(table.model.getValueAt(1, 1) as List<*>).containsExactly(FAKE_DATA[1], FAKE_DATA[4])
  }

  @Test
  fun shouldHandleEmptySelection() {
    model.timeline.reset(0, TimeUnit.SECONDS.toNanos(150))
    val selection = model.timeline.selectionRange
    assertThat(table.model.rowCount).isEqualTo(4)
    selection[0.0] = TimeUnit.SECONDS.toMicros(22).toDouble()
    assertThat(table.model.rowCount).isEqualTo(2)
    selection.clear()
    assertThat(table.model.rowCount).isEqualTo(4)
  }

  @Test
  fun shouldHandleThreadsWithTheSameNameButDifferentID() {
    val selection = model.timeline.selectionRange
    selection[TimeUnit.SECONDS.toMicros(99).toDouble()] = TimeUnit.SECONDS.toMicros(120).toDouble()
    assertThat(table.model.rowCount).isEqualTo(2)
    assertThat(table.model.getValueAt(0, 0)).isEqualTo("threadC")
    assertThat(table.model.getValueAt(0, 1) as List<*>).containsExactly(FAKE_DATA[5])
    assertThat(table.model.getValueAt(1, 0)).isEqualTo("threadC")
    assertThat(table.model.getValueAt(1, 1) as List<*>).containsExactly(FAKE_DATA[6])
  }

  @Test
  fun tableCanBeSortedByInitiatingThreadColumn() {
    val selection = model.timeline.selectionRange
    selection[TimeUnit.SECONDS.toMicros(0).toDouble()] = TimeUnit.SECONDS.toMicros(200).toDouble()
    table.rowSorter.toggleSortOrder(table.getColumn("Initiating thread").modelIndex)
    assertThat(table.getValueAt(0, 0)).isEqualTo("threadA")
    assertThat(table.getValueAt(1, 0)).isEqualTo("threadB")
    assertThat(table.getValueAt(2, 0)).isEqualTo("threadC")
    assertThat(table.getValueAt(3, 0)).isEqualTo("threadC")
    table.rowSorter.toggleSortOrder(table.getColumn("Initiating thread").modelIndex)
    assertThat(table.getValueAt(0, 0)).isEqualTo("threadC")
    assertThat(table.getValueAt(1, 0)).isEqualTo("threadC")
    assertThat(table.getValueAt(2, 0)).isEqualTo("threadB")
    assertThat(table.getValueAt(3, 0)).isEqualTo("threadA")
  }

  @Test
  fun tableCanBeSortedByTimelineColumn() {
    val selection = model.timeline.selectionRange
    selection[TimeUnit.SECONDS.toMicros(0).toDouble()] = TimeUnit.SECONDS.toMicros(200).toDouble()
    table.rowSorter.toggleSortOrder(table.getColumn("Timeline").modelIndex)
    assertThat(table.getFirstHttpDataAtRow(0).requestStartTimeUs)
      .isEqualTo(TimeUnit.SECONDS.toMicros(1))
    assertThat(table.getFirstHttpDataAtRow(1).requestStartTimeUs)
      .isEqualTo(TimeUnit.SECONDS.toMicros(5))
    assertThat(table.getFirstHttpDataAtRow(2).requestStartTimeUs)
      .isEqualTo(TimeUnit.SECONDS.toMicros(100))
    assertThat(table.getFirstHttpDataAtRow(3).requestStartTimeUs)
      .isEqualTo(TimeUnit.SECONDS.toMicros(115))
    table.rowSorter.toggleSortOrder(table.getColumn("Timeline").modelIndex)
    assertThat(table.getFirstHttpDataAtRow(0).requestStartTimeUs)
      .isEqualTo(TimeUnit.SECONDS.toMicros(115))
    assertThat(table.getFirstHttpDataAtRow(1).requestStartTimeUs)
      .isEqualTo(TimeUnit.SECONDS.toMicros(100))
    assertThat(table.getFirstHttpDataAtRow(2).requestStartTimeUs)
      .isEqualTo(TimeUnit.SECONDS.toMicros(5))
    assertThat(table.getFirstHttpDataAtRow(3).requestStartTimeUs)
      .isEqualTo(TimeUnit.SECONDS.toMicros(1))
  }

  @Test
  fun ensureAxisInList() {
    val selection = model.timeline.selectionRange
    selection[0.0] = TimeUnit.SECONDS.toMicros(22).toDouble()
    val renderer = table.getCellRenderer(0, 1)
    val comp: Component = table.prepareRenderer(renderer, 0, 1)
    assertThat(TreeWalker(comp).descendantStream().anyMatch { c -> c is AxisComponent }).isTrue()
  }

  @Test
  fun clickingOnARequestSelectsIt() {
    val selection = model.timeline.selectionRange
    // The following selection puts threads in the first and second rows on the left
    // half of the view. The right half is mostly blank.
    selection[0.0] = TimeUnit.SECONDS.toMicros(44).toDouble()
    val badX = threadsView.component.width - 1
    val goodX = table.columnModel.getColumn(0).width + 10
    val goodY = table.rowHeight / 2
    assertThat(model.selectedConnection).isNull()
    // Click on empty space - doesn't select anything
    fakeUi.mouse.click(badX, goodY)
    assertThat(model.selectedConnection).isNull()
    fakeUi.mouse.click(goodX, goodY)
    assertThat(model.selectedConnection).isNotNull()

    // After clicking on a request, clicking on empty space doesn't deselect
    fakeUi.mouse.click(badX, goodY)
    assertThat(model.selectedConnection).isNotNull()
  }
}
