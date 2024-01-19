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
package com.android.tools.idea.appinspection.inspectors.network.view.connectionsview

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.stdui.TimelineTable
import com.android.tools.adtui.stdui.TooltipLayeredPane
import com.android.tools.adtui.swing.FakeMouse.Button.RIGHT
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.FakeListPopup
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.appinspection.inspectors.network.model.FakeCodeNavigationProvider
import com.android.tools.idea.appinspection.inspectors.network.model.FakeNetworkInspectorDataSource
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorModel
import com.android.tools.idea.appinspection.inspectors.network.model.TestNetworkInspectorServices
import com.android.tools.idea.appinspection.inspectors.network.model.connections.ConnectionData
import com.android.tools.idea.appinspection.inspectors.network.model.connections.ConnectionDataModel
import com.android.tools.idea.appinspection.inspectors.network.model.connections.FAKE_CONTENT_TYPE
import com.android.tools.idea.appinspection.inspectors.network.model.connections.FAKE_RESPONSE_CODE
import com.android.tools.idea.appinspection.inspectors.network.model.connections.GrpcData
import com.android.tools.idea.appinspection.inspectors.network.model.connections.JavaThread
import com.android.tools.idea.appinspection.inspectors.network.model.connections.createFakeHttpData
import com.android.tools.idea.appinspection.inspectors.network.model.connections.fakeResponseHeaders
import com.android.tools.idea.appinspection.inspectors.network.view.FakeUiComponentsProvider
import com.android.tools.idea.appinspection.inspectors.network.view.NetworkInspectorView
import com.android.tools.idea.appinspection.inspectors.network.view.connectionsview.ConnectionColumn.NAME
import com.android.tools.idea.appinspection.inspectors.network.view.connectionsview.ConnectionColumn.SIZE
import com.android.tools.idea.appinspection.inspectors.network.view.connectionsview.ConnectionColumn.STATUS
import com.android.tools.idea.appinspection.inspectors.network.view.connectionsview.ConnectionColumn.TIME
import com.android.tools.idea.appinspection.inspectors.network.view.connectionsview.ConnectionColumn.TIMELINE
import com.android.tools.idea.appinspection.inspectors.network.view.connectionsview.ConnectionColumn.TYPE
import com.android.tools.idea.protobuf.ByteString
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.popup.PopupFactoryImpl.ActionItem
import java.awt.Color
import java.awt.Dimension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.MICROSECONDS
import java.util.concurrent.TimeUnit.SECONDS
import javax.swing.JPanel
import javax.swing.JTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.Header

private val FAKE_DATA =
  listOf(
    createHttpData(1, 1, 2, "1"),
    createHttpData(2, 3, 5, "12"),
    createHttpData(3, 8, 13, "1234"),
    createHttpData(4, 21, 34, "123", fakeResponseHeaders(4, "bmp")),
    createGrpcData(5, 23, 30, "12345"),
  )

@RunsInEdt
class ConnectionsViewTest {
  private val edtRule = EdtRule()
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val popupRule = JBPopupRule()

  @get:Rule val rule = RuleChain(projectRule, edtRule, popupRule, disposableRule)

  private lateinit var model: NetworkInspectorModel
  private lateinit var inspectorView: NetworkInspectorView
  private lateinit var scope: CoroutineScope

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
    val codeNavigationProvider = FakeCodeNavigationProvider()
    val services = TestNetworkInspectorServices(codeNavigationProvider, timer)
    scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())
    val fakeNetworkInspectorDataSource = FakeNetworkInspectorDataSource()
    model =
      NetworkInspectorModel(
        services,
        fakeNetworkInspectorDataSource,
        scope,
        object : ConnectionDataModel {
          private val dataList = FAKE_DATA

          override fun getData(timeCurrentRangeUs: Range): List<ConnectionData> {
            return dataList.filter {
              it.requestStartTimeUs >= timeCurrentRangeUs.min &&
                it.requestStartTimeUs <= timeCurrentRangeUs.max
            }
          }
        },
      )
    model.timeline.dataRange.set(0.0, SECONDS.toMicros(34).toDouble())
    val parentPanel = JPanel()
    val component = TooltipLayeredPane(parentPanel)
    inspectorView =
      NetworkInspectorView(
        projectRule.project,
        model,
        FakeUiComponentsProvider(),
        component,
        services,
        scope,
        disposableRule.disposable,
      )
    parentPanel.add(inspectorView.component)
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun logicToExtractColumnValuesFromDataWorks() {
    val data = FAKE_DATA[2] // Request: id = 3, time = 8->13
    assertThat(data.id).isEqualTo(3)

    // ID is set as the URL name, e.g. example.com/{id}, by TestHttpData
    assertThat(NAME.getValueFrom(data)).isEqualTo(data.id.toString())
    assertThat(SIZE.getValueFrom(data)).isEqualTo(4)
    assertThat(FAKE_CONTENT_TYPE).endsWith(TYPE.getValueFrom(data) as String)
    assertThat(STATUS.getValueFrom(data)).isEqualTo(FAKE_RESPONSE_CODE.toString())
    assertThat(TIME.getValueFrom(data)).isEqualTo(SECONDS.toMicros(5))
    assertThat(TIMELINE.getValueFrom(data)).isEqualTo(SECONDS.toMicros(8))
  }

  @Test
  fun mimeTypeContainingMultipleComponentsIsTruncated() {
    val data = FAKE_DATA[0] // Request: id = 1
    assertThat(data.id).isEqualTo(1)
    assertThat(FAKE_CONTENT_TYPE).endsWith(TYPE.getValueFrom(data) as String)
    assertThat(FAKE_CONTENT_TYPE).isNotEqualTo(TYPE.getValueFrom(data) as String)
  }

  @Test
  fun simpleMimeTypeIsCorrectlyDisplayed() {
    val data = FAKE_DATA[3] // Request: id = 4
    assertThat(data.id).isEqualTo(4)
    assertThat("bmp").isEqualTo(TYPE.getValueFrom(data) as String)
  }

  @Test
  fun dataRangeControlsVisibleConnections() {
    val view = inspectorView.connectionsView
    val table = getConnectionsTable(view)
    assertThat((table.getCellRenderer(0, 5) as TimelineTable.CellRenderer).activeRange)
      .isEqualTo(model.timeline.dataRange)
    assertThat(table.rowCount).isEqualTo(FAKE_DATA.size)
    // When a range is selected, table should only show connections within.
    model.timeline.selectionRange.set(
      SECONDS.toMicros(3).toDouble(),
      SECONDS.toMicros(10).toDouble(),
    )
    assertThat(table.rowCount).isEqualTo(2)
    // Once selection is cleared, table goes back to showing everything.
    model.timeline.selectionRange.set(0.0, -1.0)
    assertThat(table.rowCount).isEqualTo(FAKE_DATA.size)
    assertThat((table.getCellRenderer(0, 5) as TimelineTable.CellRenderer).activeRange)
      .isEqualTo(model.timeline.dataRange)
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
    model.timeline.selectionRange.set(0.0, activeData.requestStartTimeUs.toDouble() - 1)
    table.selectionModel.addListSelectionListener { e ->
      selectedRow = e.firstIndex
      latchSelected.countDown()
    }
    model.timeline.selectionRange.set(0.0, SECONDS.toMicros(100).toDouble())
    latchSelected.await()
    assertThat(selectedRow).isEqualTo(arbitraryIndex)
  }

  @Test
  fun tableCanBeSortedByTime() {
    model.timeline.selectionRange.set(0.0, SECONDS.toMicros(100).toDouble())
    val view = inspectorView.connectionsView
    val table = getConnectionsTable(view)

    // Times: 1, 2, 5, 13. Should sort numerically, not alphabetically (e.g. not 1, 13, 2, 5)
    // Toggle once for ascending, twice for descending
    table.rowSorter.toggleSortOrder(TIME.ordinal)
    table.rowSorter.toggleSortOrder(TIME.ordinal)

    // After reverse sorting, data should be backwards
    assertThat(table.getTimesInSeconds()).containsExactly(13, 7, 5, 2, 1)

    model.timeline.selectionRange.set(0.0, 0.0)
    assertThat(table.rowCount).isEqualTo(0)

    // Include middle two requests: 3->5 (time = 2), and 8->13 (time=5)
    // This should still be shown in reverse sorted over
    model.timeline.selectionRange.set(
      SECONDS.toMicros(3).toDouble(),
      SECONDS.toMicros(10).toDouble(),
    )
    assertThat(table.rowCount).isEqualTo(2)
    assertThat(table.convertRowIndexToView(0)).isEqualTo(1)
    assertThat(table.convertRowIndexToView(1)).isEqualTo(0)
  }

  @Test
  fun tableCanBeSortedBySize() {
    model.timeline.selectionRange.set(0.0, SECONDS.toMicros(100).toDouble())
    val view = inspectorView.connectionsView
    val table = getConnectionsTable(view)

    // Size should be sorted by raw size as opposed to alphabetically.
    // Toggle once for ascending, twice for descending
    table.rowSorter.toggleSortOrder(SIZE.ordinal)
    assertThat(table.getPayloads()).containsExactly("1", "12", "123", "1234", "12345")

    table.rowSorter.toggleSortOrder(SIZE.ordinal)
    assertThat(table.getPayloads()).containsExactly("12345", "1234", "123", "12", "1")
  }

  @Test
  fun testTableRowHighlight() {
    model.timeline.selectionRange.set(0.0, SECONDS.toMicros(100).toDouble())
    val view = inspectorView.connectionsView
    val table = getConnectionsTable(view)
    val columns = table.columnModel.columns.asSequence()
    val timelineColumn = columns.indexOfFirst { it.headerValue == TIMELINE.displayString }
    val backgroundColor = Color.YELLOW
    val selectionColor = Color.BLUE
    table.background = backgroundColor
    table.selectionBackground = selectionColor
    val renderer = table.getCellRenderer(1, timelineColumn)
    assertThat(table.prepareRenderer(renderer, 1, timelineColumn).background)
      .isEqualTo(backgroundColor)
    table.setRowSelectionInterval(1, 1)
    assertThat(table.prepareRenderer(renderer, 1, timelineColumn).background)
      .isEqualTo(selectionColor)
  }

  @Test
  fun connectionTableItemPopupMenu_http() {
    model.timeline.selectionRange.set(0.0, SECONDS.toMicros(100).toDouble())
    val view = inspectorView.connectionsView
    view.component.size = Dimension(500, 500)
    val table = getConnectionsTable(view)
    val fakeUi = FakeUi(table, createFakeWindow = true)
    val rect = table.getCellRect(0, 0, true)

    fakeUi.clickRelativeTo(table, rect.x + rect.width / 2, rect.y + rect.height / 2, RIGHT)

    val popupMenu = popupRule.fakePopupFactory.getNextPopup<ActionItem, FakeListPopup<ActionItem>>()
    assertThat(popupMenu.actions.map { it::class })
      .containsExactly(CopyUrlAction::class, CopyAsCurlAction::class)
  }

  @Test
  fun connectionTableItemPopupMenu_grpc() {
    model.timeline.selectionRange.set(0.0, SECONDS.toMicros(100).toDouble())
    val view = inspectorView.connectionsView
    view.component.size = Dimension(500, 500)
    val table = getConnectionsTable(view)
    val fakeUi = FakeUi(table, createFakeWindow = true)
    val rect = table.getCellRect(4, 0, true)

    fakeUi.clickRelativeTo(table, rect.x + rect.width / 2, rect.y + rect.height / 2, RIGHT)

    val popupMenu = popupRule.fakePopupFactory.getNextPopup<ActionItem, FakeListPopup<ActionItem>>()
    assertThat(popupMenu.actions.map { it::class }).containsExactly(CopyUrlAction::class)
  }
}

private fun createHttpData(
  id: Long,
  startS: Long,
  endS: Long,
  responsePayload: String,
  responseHeaders: List<Header> = fakeResponseHeaders(id),
) =
  createFakeHttpData(
    id,
    SECONDS.toMicros(startS),
    SECONDS.toMicros(startS),
    SECONDS.toMicros(endS),
    SECONDS.toMicros(endS),
    SECONDS.toMicros(endS),
    responsePayload = ByteString.copyFromUtf8(responsePayload),
    responseHeaders = responseHeaders,
  )

@Suppress("SameParameterValue")
private fun createGrpcData(id: Long, startS: Long, endS: Long, responsePayload: String) =
  GrpcData.createGrpcData(
    id = id,
    threads = listOf(JavaThread(1, "thread-1")),
    updateTimeUs = SECONDS.toMicros(startS),
    requestStartTimeUs = SECONDS.toMicros(startS),
    requestCompleteTimeUs = SECONDS.toMicros(endS),
    responseStartTimeUs = SECONDS.toMicros(endS),
    responseCompleteTimeUs = SECONDS.toMicros(endS),
    connectionEndTimeUs = SECONDS.toMicros(endS),
    responsePayload = ByteString.copyFromUtf8(responsePayload),
  )

private fun JTable.getTimesInSeconds() =
  getRowItems().map { MICROSECONDS.toSeconds(TIME.getValueFrom(it) as Long).toInt() }

private fun JTable.getPayloads() = getRowItems().map { it.responsePayload.toStringUtf8() }

private fun JTable.getRowItems() = (0 until rowCount).map { FAKE_DATA[convertRowIndexToModel(it)] }
