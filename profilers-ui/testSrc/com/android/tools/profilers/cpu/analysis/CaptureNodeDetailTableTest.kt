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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.model.Range
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import javax.swing.JPanel
import javax.swing.JScrollPane

class CaptureNodeDetailTableTest {
  @Test
  fun tableIsPopulated() {
    val dataSeries = listOf(NODE)
    val table = CaptureNodeDetailTable(dataSeries, Range(10.0, 100.0)).table

    assertThat(table.rowCount).isEqualTo(1)
    assertThat(table.columnCount).isEqualTo(6)
    assertThat(table.getColumnName(0)).isEqualTo("Start Time")
    assertThat(table.getColumnName(1)).isEqualTo("Name")
    assertThat(table.getColumnName(2)).isEqualTo("Wall Duration")
    assertThat(table.getColumnName(3)).isEqualTo("Self Time")
    assertThat(table.getColumnName(4)).isEqualTo("CPU Duration")
    assertThat(table.getColumnName(5)).isEqualTo("CPU Self Time")
    assertThat(table.getValueAt(0, 0)).isEqualTo(0)
    assertThat(table.getValueAt(0, 1)).isEqualTo("Foo")
    assertThat(table.getValueAt(0, 2)).isEqualTo(10)
    assertThat(table.getValueAt(0, 3)).isEqualTo(2)
    assertThat(table.getValueAt(0, 4)).isEqualTo(8)
    assertThat(table.getValueAt(0, 5)).isEqualTo(3)
  }

  @Test
  fun tableShouldBeSortedByValueNotByToString() {
    val table = CaptureNodeDetailTable(NODES_TO_SORT, Range(0.0, 100.0)).table
    assertThat(table.getValueAt(0, 0)).isEqualTo(10)
    assertThat(table.getValueAt(1, 0)).isEqualTo(2)
    assertThat(table.getValueAt(2, 0)).isEqualTo(300)
    table.rowSorter.toggleSortOrder(0)
    assertThat(table.getValueAt(0, 0)).isEqualTo(2)
    assertThat(table.getValueAt(1, 0)).isEqualTo(10)
    assertThat(table.getValueAt(2, 0)).isEqualTo(300)
  }

  @Test
  fun rowSelectionUpdatesViewRange() {
    val viewRange = Range(0.0, 100.0)
    val table = CaptureNodeDetailTable(NODE.children, Range(0.0, 100.0), viewRange).table
    assertThat(viewRange.isSameAs(Range(0.0, 100.0))).isTrue()
    table.selectionModel.setSelectionInterval(1, 1)
    assertThat(viewRange.isSameAs(Range(14.0, 19.0))).isTrue()
  }

  @Test
  fun tableCanBeScrollable() {
    val captureRange = Range(0.0, 100.0)
    assertThat(CaptureNodeDetailTable(listOf(NODE), captureRange, isScrollable = false).component).isInstanceOf(JPanel::class.java)
    assertThat(CaptureNodeDetailTable(listOf(NODE), captureRange, isScrollable = true).component).isInstanceOf(JScrollPane::class.java)
  }

  companion object {
    val NODE = CaptureNode(SingleNameModel("Foo")).apply {
      startGlobal = 10
      endGlobal = 20
      startThread = 11
      endThread = 19

      addChild(CaptureNode(SingleNameModel("Bar")).apply {
        startGlobal = 10
        endGlobal = 13
        startThread = 11
        endThread = 14
      })
      addChild(CaptureNode(SingleNameModel("bar")).apply {
        startGlobal = 14
        endGlobal = 19
        startThread = 15
        endThread = 17
      })
    }
    val NODES_TO_SORT = listOf(
      CaptureNode(SingleNameModel("Foo")).apply {
        startGlobal = 10
      },
      CaptureNode(SingleNameModel("Foo")).apply {
        startGlobal = 2
      },
      CaptureNode(SingleNameModel("Foo")).apply {
        startGlobal = 300
      }
    )
  }
}