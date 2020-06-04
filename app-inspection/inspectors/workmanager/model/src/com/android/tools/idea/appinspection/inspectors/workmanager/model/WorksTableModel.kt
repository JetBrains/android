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
package com.android.tools.idea.appinspection.inspectors.workmanager.model

import androidx.work.inspector.WorkManagerInspectorProtocol
import javax.swing.table.AbstractTableModel

class WorksTableModel(private val client: WorkManagerInspectorClient) : AbstractTableModel() {
  /**
   * Columns of work info data.
   */
  enum class Column(val widthPercentage: Double, val type: Class<*>, private val myDisplayName: String) {
    ID(0.2, String::class.java, "ID") {
      override fun getValueFrom(data: WorkManagerInspectorProtocol.WorkInfo): Any? {
        return data.id
      }
    },
    TAGS(0.3, Long::class.java, "Tags") {
      override fun getValueFrom(data: WorkManagerInspectorProtocol.WorkInfo): Any? {
        return data.tagsList
      }
    },
    RUN_ATTEMPT_COUNT(0.1, Int::class.java, "Run Attempt Count") {
      override fun getValueFrom(data: WorkManagerInspectorProtocol.WorkInfo): Any? {
        return data.runAttemptCount
      }
    },
    STATE(0.1, String::class.java, "State") {
      override fun getValueFrom(data: WorkManagerInspectorProtocol.WorkInfo): Any? {
        return data.state.name
      }
    },
    DATA(0.3, String::class.java, "Output Data") {
      override fun getValueFrom(data: WorkManagerInspectorProtocol.WorkInfo): Any? {
        return data.data
      }
    };

    companion object {
      init {
        assert(values().sumByDouble { it.widthPercentage } == 1.0)
      }
    }

    fun toDisplayString(): String {
      return myDisplayName
    }

    abstract fun getValueFrom(data: WorkManagerInspectorProtocol.WorkInfo): Any?

  }

  init {
    client.addWorksChangedListener { fireTableDataChanged() }
  }

  override fun getRowCount() = client.works.count()

  override fun getColumnCount() = Column.values().size

  override fun getColumnName(column: Int) = Column.values()[column].toDisplayString()

  override fun getColumnClass(columnIndex: Int) = Column.values()[columnIndex].type

  override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
    val work = client.works[rowIndex]
    return Column.values()[columnIndex].getValueFrom(work) ?: "Not Available"
  }
}
