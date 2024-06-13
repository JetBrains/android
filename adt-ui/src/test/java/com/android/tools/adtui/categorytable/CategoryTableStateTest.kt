/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.adtui.categorytable

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import javax.swing.SortOrder
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class CategoryTableStateTest {
  @get:Rule val edtRule = EdtRule()
  @get:Rule val projectRule = ProjectRule()

  @Service(Service.Level.PROJECT)
  @State(name = "CategoryTableStateTest", storages = [Storage("categoryTableStateTest.xml")])
  class StateComponent : CategoryTablePersistentStateComponent() {
    override val serializer =
      CategoryTableStateSerializer(
        listOf(Name.attribute.stringSerializer("Name"), Status.attribute.stringSerializer("Status"))
      )
  }

  @Test
  fun roundtrip() {
    val stateComponent = projectRule.project.service<StateComponent>()
    val offlineDevices = listOf(Category(Status.attribute, "Offline"))

    // Verify writing
    run {
      val table = CategoryTable(CategoryTableDemo.columns)
      stateComponent.table = table

      table.toggleSortOrder(Name.attribute)
      table.addGrouping(Status.attribute)
      table.setCollapsed(offlineDevices, true)

      val state = checkNotNull(stateComponent.state)

      assertThat(state.groupByAttributes).containsExactly("Status")
      assertThat(state.columnSorters[0].column).isEqualTo("Name")
      assertThat(state.columnSorters[0].order).isEqualTo(SortOrder.ASCENDING)
      assertThat(state.collapsedNodes[0].categories[0].attribute).isEqualTo("Status")
      assertThat(state.collapsedNodes[0].categories[0].value).isEqualTo("Offline")
    }

    // Read back the written values
    run {
      val table = CategoryTable(CategoryTableDemo.columns)
      stateComponent.table = table

      assertThat(table.groupByAttributes).containsExactly(Status.attribute)
      assertThat(table.columnSorters)
        .containsExactly(ColumnSortOrder(Name.attribute, SortOrder.ASCENDING))
      assertThat(table.collapsedNodes).containsExactly(offlineDevices)
    }
  }
}
