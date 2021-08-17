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
package com.android.tools.idea.appinspection.inspectors.network.view

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.stdui.TooltipLayeredPane
import com.android.tools.idea.appinspection.inspectors.network.model.CodeNavigationProvider
import com.android.tools.idea.appinspection.inspectors.network.model.FakeNetworkInspectorDataSource
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorModel
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorServices
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.FAKE_CONTENT_TYPE
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.FAKE_RESPONSE_CODE
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpDataModel
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.createFakeHttpData
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.fakeResponseFields
import com.android.tools.inspectors.common.api.stacktrace.CodeLocation
import com.android.tools.inspectors.common.api.stacktrace.CodeNavigator
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Color
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import javax.swing.JTable

private val FAKE_DATA = listOf(
  createFakeHttpData(1, 1, 2),
  createFakeHttpData(2, 3, 5),
  createFakeHttpData(3, 8, 13),
  createFakeHttpData(4, 21, 34).copy(responseFields = fakeResponseFields(4, "bmp"))
)

@RunsInEdt
class ConnectionsViewTest {
  @get:Rule
  val edtRule = EdtRule()

  private lateinit var model: NetworkInspectorModel
  private lateinit var inspectorView: NetworkInspectorView

  private val timer = FakeTimer()

  /**
   * The underlying table in ConnectionsView is intentionally not exposed to regular users of the
   * class. However, for tests, it is useful to inspect the contents of the table to verify it was
   * updated.
   */
  private fun getConnectionsTable(view: ConnectionsView): JTable {
    return view.component as JTable
  }


  @Before
  fun setUp() {
    val codeNavigationProvider = object : CodeNavigationProvider {
      override val codeNavigator = object : CodeNavigator() {
        override fun isNavigatable(location: CodeLocation) = true
        override fun handleNavigate(location: CodeLocation) = Unit
      }
    }
    val services = NetworkInspectorServices(codeNavigationProvider, 0, timer, MoreExecutors.directExecutor())
    model = NetworkInspectorModel(services, FakeNetworkInspectorDataSource(), object : HttpDataModel {
      private val dataList = FAKE_DATA
      override fun getData(timeCurrentRangeUs: Range): List<HttpData> {
        return dataList.filter { it.requestStartTimeUs >= timeCurrentRangeUs.min && it.requestStartTimeUs <= timeCurrentRangeUs.max }
      }
    })
    val parentPanel = JPanel()
    val component = TooltipLayeredPane(parentPanel)
    inspectorView = NetworkInspectorView(model, FakeUiComponentsProvider(), component)
    parentPanel.add(inspectorView.component)
  }

  @Test
  fun logicToExtractColumnValuesFromDataWorks() {
    val data = FAKE_DATA[2] // Request: id = 3, time = 8->13
    assertThat(data.id).isEqualTo(3)

    // ID is set as the URL name, e.g. example.com/{id}, by TestHttpData
    assertThat(ConnectionsView.Column.NAME.getValueFrom(data)).isEqualTo(data.id.toString())
    assertThat(ConnectionsView.Column.SIZE.getValueFrom(data)).isEqualTo(8)
    assertThat(FAKE_CONTENT_TYPE).endsWith(ConnectionsView.Column.TYPE.getValueFrom(data) as String)
    assertThat(ConnectionsView.Column.STATUS.getValueFrom(data)).isEqualTo(FAKE_RESPONSE_CODE)
    assertThat(ConnectionsView.Column.TIME.getValueFrom(data)).isEqualTo(TimeUnit.SECONDS.toMicros(5))
    assertThat(ConnectionsView.Column.TIMELINE.getValueFrom(data)).isEqualTo(TimeUnit.SECONDS.toMicros(8))
  }

  @Test
  fun mimeTypeContainingMultipleComponentsIsTruncated() {
    val data = FAKE_DATA[0] // Request: id = 1
    assertThat(data.id).isEqualTo(1)
    assertThat(FAKE_CONTENT_TYPE).endsWith(ConnectionsView.Column.TYPE.getValueFrom(data) as String)
    assertThat(FAKE_CONTENT_TYPE).isNotEqualTo(ConnectionsView.Column.TYPE.getValueFrom(data) as String)
  }

  @Test
  fun simpleMimeTypeIsCorrectlyDisplayed() {
    val data = FAKE_DATA[3] // Request: id = 4
    assertThat(data.id).isEqualTo(4)
    assertThat("bmp").isEqualTo(ConnectionsView.Column.TYPE.getValueFrom(data) as String)
  }

  @Test
  fun dataRangeControlsVisibleConnections() {
    val view = inspectorView.connectionsView
    val table = getConnectionsTable(view)
    assertThat(table.rowCount).isEqualTo(0)
    model.timeline.selectionRange.set(TimeUnit.SECONDS.toMicros(3).toDouble(), TimeUnit.SECONDS.toMicros(10).toDouble())
    assertThat(table.rowCount).isEqualTo(2)
    model.timeline.selectionRange.set(0.0, 0.0)
    assertThat(table.rowCount).isEqualTo(0)
  }

  @Test
  fun activeConnectionIsAutoFocusedByTable() {
    val view = inspectorView.connectionsView
    val table = getConnectionsTable(view)
    var selectedRow = -1

    // We arbitrarily select one of the fake data instances and sanity check that the table
    // auto-selects it, which is checked for below.
    val arbitraryIndex = 1
    val activeData = FAKE_DATA[arbitraryIndex]
    model.setSelectedConnection(activeData)
    val latchSelected = CountDownLatch(1)
    table.selectionModel.addListSelectionListener { e ->
      selectedRow = e.firstIndex
      latchSelected.countDown()
    }
    model.timeline.selectionRange.set(0.0, TimeUnit.SECONDS.toMicros(100).toDouble())
    latchSelected.await()
    assertThat(selectedRow).isEqualTo(arbitraryIndex)
  }

  @Test
  fun tableCanBeSorted() {
    model.timeline.selectionRange.set(0.0, TimeUnit.SECONDS.toMicros(100).toDouble())
    val view = inspectorView.connectionsView
    val table = getConnectionsTable(view)

    // Times: 1, 2, 5, 13. Should sort numerically, not alphabetically (e.g. not 1, 13, 2, 5)
    // Toggle once for ascending, twice for descending
    table.rowSorter.toggleSortOrder(ConnectionsView.Column.TIME.ordinal)
    table.rowSorter.toggleSortOrder(ConnectionsView.Column.TIME.ordinal)

    // After reverse sorting, data should be backwards
    assertThat(table.rowCount).isEqualTo(4)
    assertThat(table.convertRowIndexToView(0)).isEqualTo(3)
    assertThat(table.convertRowIndexToView(1)).isEqualTo(2)
    assertThat(table.convertRowIndexToView(2)).isEqualTo(1)
    assertThat(table.convertRowIndexToView(3)).isEqualTo(0)

    model.timeline.selectionRange.set(0.0, 0.0)
    assertThat(table.rowCount).isEqualTo(0)

    // Include middle two requests: 3->5 (time = 2), and 8->13 (time=5)
    // This should still be shown in reverse sorted over
    model.timeline.selectionRange.set(TimeUnit.SECONDS.toMicros(3).toDouble(), TimeUnit.SECONDS.toMicros(10).toDouble())
    assertThat(table.rowCount).isEqualTo(2)
    assertThat(table.convertRowIndexToView(0)).isEqualTo(1)
    assertThat(table.convertRowIndexToView(1)).isEqualTo(0)
  }

  @Test
  fun testTableRowHighlight() {
    model.timeline.selectionRange.set(0.0, TimeUnit.SECONDS.toMicros(100).toDouble())
    val view = inspectorView.connectionsView
    val timelineColumn = ConnectionsView.Column.TIMELINE.ordinal
    val table = getConnectionsTable(view)
    val backgroundColor = Color.YELLOW
    val selectionColor = Color.BLUE
    table.background = backgroundColor
    table.selectionBackground = selectionColor
    val renderer = table.getCellRenderer(1, timelineColumn)
    assertThat(table.prepareRenderer(renderer, 1, timelineColumn).background).isEqualTo(backgroundColor)
    table.setRowSelectionInterval(1, 1)
    assertThat(table.prepareRenderer(renderer, 1, timelineColumn).background).isEqualTo(selectionColor)
  }
}