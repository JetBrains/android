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

import com.android.tools.idea.insights.Issue
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

class AppInsightsIssuesTableModel<IssueT : Issue>(renderer: AppInsightsTableCellRenderer) :
  ListTableModel<IssueT>() {
  init {
    columnInfos =
      arrayOf(
        object : ColumnInfo<IssueT, IssueT>("Issues") {
          override fun valueOf(item: IssueT) = item

          override fun getComparator(): Comparator<IssueT> {
            return Comparator.comparing { it.issueDetails.title }
          }

          override fun getRenderer(item: IssueT) = renderer
        },
        object : ColumnInfo<IssueT, Int>("Events") {
          override fun valueOf(item: IssueT) = item.issueDetails.eventsCount.toInt()

          override fun getComparator(): Comparator<IssueT> {
            return Comparator.comparingInt { it.issueDetails.eventsCount.toInt() }
          }

          override fun getWidth(table: JTable?) = 60

          override fun getRenderer(item: IssueT?): TableCellRenderer = NumberColumnRenderer
        },
        object : ColumnInfo<IssueT, Int>("Users") {
          override fun valueOf(item: IssueT) = item.issueDetails.impactedDevicesCount.toInt()

          override fun getComparator(): Comparator<IssueT> {
            return Comparator.comparingInt { it.issueDetails.impactedDevicesCount.toInt() }
          }

          override fun getWidth(table: JTable?) = 60

          override fun getRenderer(item: IssueT?): TableCellRenderer = NumberColumnRenderer
        }
      )
  }
}

private object NumberColumnRenderer : DefaultTableCellRenderer() {
  init {
    horizontalAlignment = JLabel.RIGHT
  }

  override fun getTableCellRendererComponent(
    table: JTable?,
    value: Any?,
    isSelected: Boolean,
    hasFocus: Boolean,
    row: Int,
    column: Int
  ): Component =
    super.getTableCellRendererComponent(table, value.ifZero("-"), isSelected, false, row, column)
      .apply { border = BorderFactory.createCompoundBorder(JBUI.Borders.emptyRight(5), border) }
}
