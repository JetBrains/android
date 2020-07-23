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

import androidx.work.inspection.WorkManagerInspectorProtocol
import javax.swing.table.AbstractTableModel

class WorksTableModel(private val client: WorkManagerInspectorClient) : AbstractTableModel() {
  /**
   * Columns of work info data.
   */
  enum class Column(val widthPercentage: Double, val type: Class<*>, private val myDisplayName: String) {
    TAGS(0.2, Long::class.java, "Tags") {
      override fun getValueFrom(data: WorkManagerInspectorProtocol.WorkInfo): Any {
        return data.tagsList
      }
    },
    STATE(0.1, String::class.java, "State") {
      override fun getValueFrom(data: WorkManagerInspectorProtocol.WorkInfo): Any {
        return data.state.name
      }
    },
    TIME_STARTED(0.2, Int::class.java, "Time Started") {
      override fun getValueFrom(data: WorkManagerInspectorProtocol.WorkInfo): Any {
        return data.scheduleRequestedAt
      }
    },
    RUN_ATTEMPT_COUNT(0.1, Int::class.java, "Retry Count") {
      override fun getValueFrom(data: WorkManagerInspectorProtocol.WorkInfo): Any {
        return data.runAttemptCount
      }
    },

    DATA(0.4, String::class.java, "Output Data") {
      override fun getValueFrom(data: WorkManagerInspectorProtocol.WorkInfo): Any {
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

    abstract fun getValueFrom(data: WorkManagerInspectorProtocol.WorkInfo): Any

  }

  init {
    client.addWorksChangedListener { fireTableDataChanged() }
  }

  override fun getRowCount() = client.getWorkInfoCount()

  override fun getColumnCount() = Column.values().size

  override fun getColumnName(column: Int) = Column.values()[column].toDisplayString()

  override fun getColumnClass(columnIndex: Int) = Column.values()[columnIndex].type

  override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
    val work = client.getWorkInfo(rowIndex) ?: WorkManagerInspectorProtocol.WorkInfo.getDefaultInstance()
    return Column.values()[columnIndex].getValueFrom(work)
  }
}
