/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.property.panel.impl.model

import com.android.tools.property.panel.impl.model.util.FakePTableModel
import com.android.tools.property.panel.impl.model.util.TestGroupItem
import com.android.tools.property.ptable.PTable
import com.android.tools.property.ptable.PTableGroupItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TableLineModelTest {

  @Test
  fun testEmptyFilter() {
    val test = TableTest()
    val model = TableLineModelImpl(test.model, true)
    model.filter = ""
    test.applyTableLineModel(model)
    assertThat(test.table.itemCount).isEqualTo(5)
  }

  @Test
  fun testSimpleMatch() {
    val test = TableTest()
    val model = TableLineModelImpl(test.model, true)
    model.filter = "co"
    test.applyTableLineModel(model)
    assertThat(test.table.itemCount).isEqualTo(2)
    assertThat(test.table.item(0).name).isEqualTo("color")
    assertThat(test.table.item(1).name).isEqualTo("container")
  }

  @Test
  fun testMatchIncludesGroupsEvenWhenGroupsAreCollapsed() {
    val test = TableTest()
    val model = TableLineModelImpl(test.model, true)
    model.filter = "to"
    test.applyTableLineModel(model)
    assertThat(test.table.itemCount).isEqualTo(3)
    assertThat(test.table.item(0).name).isEqualTo("topText")
    assertThat(test.table.item(1).name).isEqualTo("border")
    assertThat(test.table.item(2).name).isEqualTo("group2")
  }

  @Test
  fun testMatchIncludesGroupItemsWhenGroupsAreExpanded() {
    val test = TableTest(true)
    val model = TableLineModelImpl(test.model, true)
    model.filter = "to"
    test.applyTableLineModel(model)
    assertThat(test.table.itemCount).isEqualTo(6)
    assertThat(test.table.item(0).name).isEqualTo("topText")
    assertThat(test.table.item(1).name).isEqualTo("border")
    assertThat(test.table.item(2).name).isEqualTo("top")
    assertThat(test.table.item(3).name).isEqualTo("bottom")
    assertThat(test.table.item(4).name).isEqualTo("group2")
    assertThat(test.table.item(5).name).isEqualTo("tone")
  }

  @Test
  fun testRefresh() {
    val test = TableTest(true)
    val model = TableLineModelImpl(test.model, true)
    model.refresh()
    assertThat(test.model.refreshCalled).isTrue()
  }

  class TableTest(expanded: Boolean = false) {
    private val group1: PTableGroupItem =
      TestGroupItem("border", mapOf("left" to "4", "right" to "4", "top" to "8", "bottom" to "8"))
    private val group2: PTableGroupItem =
      TestGroupItem("group2", mapOf("size" to "4dp", "tone" to "C"))
    val model =
      FakePTableModel(
        expanded,
        mapOf("color" to "blue", "topText" to "Hello", "container" to "id2"),
        listOf(group1, group2),
      )
    val table = PTable.create(model)

    fun applyTableLineModel(tableLineModel: TableLineModelImpl) {
      table.filter = tableLineModel.filter
    }
  }
}
