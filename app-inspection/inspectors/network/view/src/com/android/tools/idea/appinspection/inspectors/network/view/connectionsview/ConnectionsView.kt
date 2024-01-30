/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.stdui.TimelineTable
import com.android.tools.adtui.table.ConfigColumnTableAspect
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorAspect
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorModel
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorModel.DetailContent
import com.android.tools.idea.appinspection.inspectors.network.model.connections.ConnectionData
import com.android.tools.idea.appinspection.inspectors.network.model.connections.GrpcData
import com.android.tools.idea.appinspection.inspectors.network.model.connections.HttpData
import com.android.tools.idea.appinspection.inspectors.network.view.NetworkInspectorViewState
import com.android.tools.idea.appinspection.inspectors.network.view.connectionsview.ConnectionColumn.TIMELINE
import com.android.tools.idea.appinspection.inspectors.network.view.constants.DEFAULT_BACKGROUND
import com.android.tools.idea.appinspection.inspectors.network.view.constants.ROW_HEIGHT_PADDING
import com.android.tools.idea.appinspection.inspectors.network.view.rules.registerEnterKeyAction
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.protobuf.ByteString
import com.google.gson.GsonBuilder
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import java.awt.KeyboardFocusManager
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import java.util.Base64
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionEvent
import javax.swing.table.TableCellRenderer
import kotlin.io.path.writer

/**
 * This class responsible for displaying table of connections information (e.g. url, duration,
 * timeline) for network inspector. Each row in the table represents a single connection.
 */
class ConnectionsView(private val model: NetworkInspectorModel) : AspectObserver() {

  private val tableModel = ConnectionsTableModel(model.selectionRangeDataFetcher)
  private val connectionsTable =
    TimelineTable.create(tableModel, model.timeline, TIMELINE.displayString, true)

  val component: JComponent
    get() = connectionsTable

  init {
    customizeConnectionsTable()
    ConfigColumnTableAspect.apply(connectionsTable, NetworkInspectorViewState.getInstance().columns)
    model.aspect.addDependency(this).onChange(NetworkInspectorAspect.SELECTED_CONNECTION) {
      updateTableSelection()
    }
  }

  private fun customizeConnectionsTable() {
    connectionsTable.autoCreateRowSorter = true

    ConnectionColumn.values().forEach {
      setRenderer(it, it.getCellRenderer(connectionsTable, model))
    }

    connectionsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    connectionsTable.addMouseListener(
      object : MouseAdapter() {
        override fun mouseMoved(e: MouseEvent) {
          connectionsTable.toolTipText = e.getConnectionData()?.url
        }

        override fun mouseClicked(e: MouseEvent) {
          val row = connectionsTable.rowAtPoint(e.point)
          if (row != -1) {
            model.detailContent = DetailContent.CONNECTION
          }
        }

        override fun mouseReleased(e: MouseEvent) {
          openContextMenu(e)
        }

        override fun mousePressed(e: MouseEvent) {
          openContextMenu(e)
        }
      }
    )
    connectionsTable.registerEnterKeyAction {
      if (connectionsTable.selectedRow != -1) {
        model.detailContent = DetailContent.CONNECTION
      }
    }
    connectionsTable.selectionModel.addListSelectionListener { e: ListSelectionEvent ->
      if (e.valueIsAdjusting) {
        return@addListSelectionListener // Only handle listener on last event, not intermediate
        // events
      }
      val selectedRow = connectionsTable.selectedRow
      if (0 <= selectedRow && selectedRow < tableModel.rowCount) {
        val modelRow = connectionsTable.convertRowIndexToModel(selectedRow)
        model.setSelectedConnection(tableModel.getConnectionData(modelRow))
      }
    }
    connectionsTable.background = DEFAULT_BACKGROUND
    connectionsTable.showVerticalLines = true
    connectionsTable.showHorizontalLines = false
    val defaultFontHeight = connectionsTable.getFontMetrics(connectionsTable.font).height
    connectionsTable.rowMargin = 0
    connectionsTable.rowHeight = defaultFontHeight + ROW_HEIGHT_PADDING
    connectionsTable.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null)
    connectionsTable.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null)
    model.selectionRangeDataFetcher.addOnChangedListener {
      // Although the selected row doesn't change on range moved, we do this here to prevent
      // flickering that otherwise occurs in our table.
      updateTableSelection()
    }
  }

  private fun openContextMenu(e: MouseEvent) {
    if (!e.isPopupTrigger) {
      return
    }
    val connectionData = e.getConnectionData() ?: return
    val actions = connectionData.getActions()
    if (actions.isEmpty()) {
      return
    }
    JBPopupFactory.getInstance()
      .createActionGroupPopup(null, DefaultActionGroup(actions), EMPTY_CONTEXT, true, null, -1)
      .show(RelativePoint(e))
  }

  private fun setRenderer(column: ConnectionColumn, renderer: TableCellRenderer) {
    connectionsTable.columnModel.getColumn(column.ordinal).cellRenderer = renderer
  }

  private fun MouseEvent.getConnectionData(): ConnectionData? {
    val row = connectionsTable.rowAtPoint(point)
    return when {
      row < 0 -> null
      else -> tableModel.getConnectionData(connectionsTable.convertRowIndexToModel(row))
    }
  }

  private fun updateTableSelection() {
    val selectedData = model.selectedConnection
    if (selectedData != null) {
      for (i in 0 until tableModel.rowCount) {
        if (tableModel.getConnectionData(i).id == selectedData.id) {
          val row = connectionsTable.convertRowIndexToView(i)
          connectionsTable.setRowSelectionInterval(row, row)
          return
        }
      }
    } else {
      connectionsTable.clearSelection()
    }
  }

  /**
   * Export connection data list to a file
   *
   * Although [ConnectionData] are data classes and can be serialized directly, that results in
   * undesirable representation of the data. Instead, we convert [HttpData] and [GrpcData] to a
   * [Map<String, Any>].
   */
  fun exportConnections(path: Path) {
    val gson = GsonBuilder().setPrettyPrinting().create()
    val export =
      tableModel.getConnectionDataList().mapNotNull {
        when (it) {
          is HttpData -> it.forExport()
          is GrpcData -> it.forExport()
          else -> null
        }
      }
    path.writer().use { it.write(gson.toJson(export)) }
  }
}

private fun HttpData.forExport(): Map<String, Any> {
  return sortedMapOf(
    "url" to url,
    "method" to method,
    "transport" to httpTransport.name,
    "stack-trace" to trace,
    "threads" to threads.joinToString { it.name },
    "request-headers" to requestHeaders.forExport(),
    "response-headers" to responseHeaders.forExport(),
    "request-content-type" to getRequestContentType().mimeType,
    "response-content-type" to getResponseContentType().mimeType,
    "request-payload-base64" to requestPayload.forExport(),
    "response-payload-base64" to getReadableResponsePayload().forExport(),
    "response-code" to responseCode,
    "duration-microseconds" to connectionEndTimeUs - requestStartTimeUs,
  )
}

private fun GrpcData.forExport(): Map<String, Any> {
  return sortedMapOf(
    "address" to address,
    "service" to service,
    "method" to method,
    "transport" to "gRPC",
    "stack-trace" to trace,
    "threads" to threads.joinToString { it.name },
    "request-headers" to requestHeaders.forExport(),
    "response-headers" to responseHeaders.forExport(),
    "request-type" to requestType,
    "response-type" to responseType,
    "request-payload-base64" to requestPayload.forExport(),
    "response-payload-base64" to responsePayload.forExport(),
    "status" to status,
    "error" to error,
    "duration-microseconds" to connectionEndTimeUs - requestStartTimeUs,
  )
}

/** Collapse [List<String>] to a single string using a join */
private fun Map<String, List<String>>.forExport() =
  entries.associate { e -> e.key to e.value.joinToString { it } }

/** Export as a [Base64] string */
private fun ByteString.forExport() = Base64.getEncoder().encode(toByteArray()).decodeToString()

private fun ConnectionData.getActions(): List<AnAction> {
  val data = this@getActions
  return buildList {
    add(CopyUrlAction(data))
    if (data is HttpData && StudioFlags.NETWORK_INSPECTOR_COPY_AS_CURL.get()) {
      add(CopyAsCurlAction(data))
    }
  }
}
