/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import com.android.tools.idea.insights.model.issue.AppInsightsCrash
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

class AppInsightsCrashesTableModel(renderer: AppInsightsTableCellRenderer) : ListTableModel<AppInsightsCrash>() {
  init {
    columnInfos =
      arrayOf(
        object : ColumnInfo<AppInsightsCrash, AppInsightsCrash>("Issues") {
          override fun valueOf(item: AppInsightsCrash) = item

          override fun getComparator(): Comparator<AppInsightsCrash> {
            return Comparator.comparing { crash -> crash.issueDetails.getDisplayTitle().toList().joinToString(".") }
          }

          override fun getRenderer(item: AppInsightsCrash) = renderer
        },
        FormattedNumberColumnInfo("Events") { it.issueDetails.eventsCount },
        FormattedNumberColumnInfo("Users") { it.issueDetails.impactedDevicesCount },
      )
    isSortable = true
  }

  private inner class FormattedNumberColumnInfo(name: String, private val selector: (AppInsightsCrash) -> Long) :
    ColumnInfo<AppInsightsCrash, String>(name) {
    override fun valueOf(item: AppInsightsCrash): String = selector(item).formatNumberToPrettyString()

    override fun getComparator(): Comparator<AppInsightsCrash> {
      return Comparator.comparingInt { selector(it).toInt() }
    }

    override fun getMaxStringValue() = items.maxOfOrNull { selector(it) }?.formatNumberToPrettyString()

    override fun getRenderer(item: AppInsightsCrash?): TableCellRenderer = NumberColumnRenderer
  }
}

private object NumberColumnRenderer : DefaultTableCellRenderer() {
  init {
    horizontalAlignment = RIGHT
  }

  override fun getTableCellRendererComponent(
    table: JTable?,
    value: Any?,
    isSelected: Boolean,
    hasFocus: Boolean,
    row: Int,
    column: Int,
  ): Component =
    super.getTableCellRendererComponent(table, value, isSelected, false, row, column).apply {
      border = BorderFactory.createCompoundBorder(JBUI.Borders.emptyRight(5), border)
    }
}
