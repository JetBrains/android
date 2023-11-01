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
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.getMimeType
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.getUrlName
import com.intellij.openapi.util.text.StringUtil
import java.util.Locale
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

/** Columns for each connection information */
internal enum class ConnectionColumn(
  var widthRatio: Double,
  val type: Class<*>,
  val visible: Boolean
) {
  NAME(0.25, String::class.java, visible = true) {
    override fun getValueFrom(data: HttpData) = data.getUrlName()

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) =
      BorderlessTableCellRenderer()
  },
  SIZE(0.25 / 4, java.lang.Integer::class.java, visible = true) {
    override fun getValueFrom(data: HttpData) = data.responsePayload.size()

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) = SizeRenderer()
  },
  TYPE(0.25 / 4, String::class.java, visible = true) {
    override fun getValueFrom(data: HttpData) = data.getMimeType()

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) =
      BorderlessTableCellRenderer()
  },
  STATUS(0.25 / 4, java.lang.Integer::class.java, visible = true) {
    override fun getValueFrom(data: HttpData) = data.responseHeader.statusCode

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) = StatusRenderer()
  },
  TIME(0.25 / 4, java.lang.Long::class.java, visible = true) {
    override fun getValueFrom(data: HttpData) = data.connectionEndTimeUs - data.requestStartTimeUs

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) = TimeRenderer()
  },
  TIMELINE(0.5, java.lang.Long::class.java, visible = true) {
    override fun getValueFrom(data: HttpData) = data.requestStartTimeUs

    override fun getCellRenderer(table: JTable, model: NetworkInspectorModel) =
      TimelineRenderer(table, model.timeline)
  };

  fun toDisplayString() = StringUtil.capitalize(name.lowercase(Locale.getDefault()))

  abstract fun getValueFrom(data: HttpData): Any

  abstract fun getCellRenderer(table: JTable, model: NetworkInspectorModel): TableCellRenderer
}
