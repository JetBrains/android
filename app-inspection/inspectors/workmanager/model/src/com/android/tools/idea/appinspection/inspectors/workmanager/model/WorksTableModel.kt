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
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import javax.swing.table.AbstractTableModel

class WorksTableModel(private val client: WorkManagerInspectorClient) : AbstractTableModel() {
  /**
   * Columns of work info data.
   */
  enum class Column(val widthPercentage: Double, val type: Class<*>, private val myDisplayName: String) {
    ORDER(0.02, Long::class.java, "#") {
      override fun getValueFrom(data: WorkManagerInspectorProtocol.WorkInfo): Any {
        return Any()
      }
    },
    CLASS_NAME(0.32, Long::class.java, "Class Name") {
      override fun getValueFrom(data: WorkManagerInspectorProtocol.WorkInfo): Any {
        return data.workerClassName.substringAfterLast('.')
      }
    },
    STATE(0.13, String::class.java, "State") {
      override fun getValueFrom(data: WorkManagerInspectorProtocol.WorkInfo): Any {
        return data.state.ordinal
      }
    },
    TIME_STARTED(0.1, Int::class.java, "Time Started") {
      override fun getValueFrom(data: WorkManagerInspectorProtocol.WorkInfo): Any {
        return data.scheduleRequestedAt
      }
    },
    RUN_ATTEMPT_COUNT(0.03, Int::class.java, "Retries") {
      override fun getValueFrom(data: WorkManagerInspectorProtocol.WorkInfo): Any {
        return data.runAttemptCount
      }
    },

    DATA(0.4, String::class.java, "Output Data") {
      override fun getValueFrom(data: WorkManagerInspectorProtocol.WorkInfo): Any {
        return data.state to data.data
      }
    };

    companion object {
      init {
        assert(values().sumOf { it.widthPercentage } == 1.0)
      }
    }

    fun toDisplayString(): String {
      return myDisplayName
    }

    abstract fun getValueFrom(data: WorkManagerInspectorProtocol.WorkInfo): Any

  }

  init {
    client.addWorksChangedListener {
      CoroutineScope(EdtExecutorService.getInstance().asCoroutineDispatcher()).launch {
        fireTableDataChanged()
      }
    }
  }

  override fun getRowCount() = client.lockedWorks { it.size }

  override fun getColumnCount() = Column.values().size

  override fun getColumnName(column: Int) = Column.values()[column].toDisplayString()

  override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
    if (columnIndex == Column.ORDER.ordinal) {
      return rowIndex + 1
    }
    val work = client.lockedWorks { works -> works.getOrNull(rowIndex) } ?: WorkManagerInspectorProtocol.WorkInfo.getDefaultInstance()
    return Column.values()[columnIndex].getValueFrom(work)
  }
}
