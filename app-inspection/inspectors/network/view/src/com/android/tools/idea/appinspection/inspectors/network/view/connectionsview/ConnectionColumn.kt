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

import com.android.tools.adtui.stdui.BorderlessTableCellRenderer
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorModel
import com.android.tools.idea.appinspection.inspectors.network.model.connections.ConnectionData
import com.intellij.openapi.util.NlsContexts.ColumnName
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

private val simpleRenderer = BorderlessTableCellRenderer()
private val sizeRenderer = SizeRenderer()
private val timeRenderer = TimeRenderer()

/** Columns for each connection information */
internal enum class ConnectionColumn(
  @ColumnName var displayString: String,
  val widthRatio: Double,
  val type: Class<*>,
  val visible: Boolean,
) {
  URL("URL", 0.25, String::class.java, visible = false) {
    override fun getValueFrom(data: ConnectionData) = data.url

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) = simpleRenderer
  },
  NAME("Name", 0.25, String::class.java, visible = true) {
    override fun getValueFrom(data: ConnectionData) = data.name

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) = simpleRenderer
  },
  PATH("Path", 0.1, String::class.java, visible = false) {
    override fun getValueFrom(data: ConnectionData) = data.path

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) = simpleRenderer
  },
  HOST("Host", 0.1, String::class.java, visible = false) {
    override fun getValueFrom(data: ConnectionData) = data.address

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) = simpleRenderer
  },
  METHOD("Method", 0.05, String::class.java, visible = false) {
    override fun getValueFrom(data: ConnectionData) = data.method

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) = simpleRenderer
  },
  SCHEME("Scheme", 0.05, String::class.java, visible = false) {
    override fun getValueFrom(data: ConnectionData) = data.schema

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) = simpleRenderer
  },
  TRANSPORT("Transport", 0.05, String::class.java, visible = false) {
    override fun getValueFrom(data: ConnectionData) = data.transport

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) = simpleRenderer
  },
  REQUEST_SIZE("Request Size", 0.05, java.lang.Integer::class.java, visible = false) {
    override fun getValueFrom(data: ConnectionData) = data.requestPayload.size()

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) = sizeRenderer
  },
  SIZE("Size", 0.05, java.lang.Integer::class.java, visible = true) {
    override fun getValueFrom(data: ConnectionData) = data.responsePayload.size()

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) = sizeRenderer
  },
  REQUEST_HEADERS("Request Headers", 0.05, java.lang.Integer::class.java, visible = false) {
    override fun getValueFrom(data: ConnectionData) = data.requestHeaders.size

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) = simpleRenderer
  },
  RESPONSE_HEADERS("Response Headers", 0.05, java.lang.Integer::class.java, visible = false) {
    override fun getValueFrom(data: ConnectionData) = data.responseHeaders.size

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) = simpleRenderer
  },
  TYPE("Type", 0.25 / 4, String::class.java, visible = true) {
    override fun getValueFrom(data: ConnectionData) = data.responseType.split("/").last()

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) = simpleRenderer
  },
  STATUS("Status", 0.05, String::class.java, visible = true) {
    override fun getValueFrom(data: ConnectionData) = data.status

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) = simpleRenderer
  },
  TIME("Time", 0.05, java.lang.Long::class.java, visible = true) {
    override fun getValueFrom(data: ConnectionData) =
      data.connectionEndTimeUs - data.requestStartTimeUs

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) = timeRenderer
  },
  REQUEST_TIME("Request Time", 0.05, java.lang.Long::class.java, visible = false) {
    override fun getValueFrom(data: ConnectionData): Long {
      return data.requestCompleteTimeUs - data.requestStartTimeUs
    }

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) = timeRenderer
  },
  RESPONSE_TIME("Response Time", 0.05, java.lang.Long::class.java, visible = false) {
    override fun getValueFrom(data: ConnectionData) =
      data.responseCompleteTimeUs - data.responseStartTimeUs

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) = timeRenderer
  },
  TIMELINE("Timeline", 0.5, java.lang.Long::class.java, visible = true) {
    override fun getValueFrom(data: ConnectionData) = data.requestStartTimeUs

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) =
      TimelineRenderer(table, model.timeline)
  };

  abstract fun getValueFrom(data: ConnectionData): Any

  abstract fun getCellRenderer(table: JTable, model: NetworkInspectorModel): TableCellRenderer
}
