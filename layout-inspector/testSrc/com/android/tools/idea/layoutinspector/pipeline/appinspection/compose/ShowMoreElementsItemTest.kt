/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.compose

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.laf.HeadlessTableUI
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.properties.PropertySection
import com.android.tools.idea.layoutinspector.properties.PropertySection.PARAMETERS
import com.android.tools.idea.layoutinspector.properties.PropertyType
import com.android.tools.idea.layoutinspector.properties.PropertyType.STRING
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.property.ptable.DefaultPTableCellEditor
import com.android.tools.property.ptable.PTable
import com.android.tools.property.ptable.PTableCellEditor
import com.android.tools.property.ptable.PTableCellEditorProvider
import com.android.tools.property.ptable.PTableColumn
import com.android.tools.property.ptable.PTableGroupModification
import com.android.tools.property.ptable.PTableItem
import com.android.tools.property.ptable.item.PTableTestModel
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JTable
import kotlinx.coroutines.CoroutineScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

@RunsInEdt
class ShowMoreElementsItemTest {

  private val disposableRule = DisposableRule()

  @get:Rule val rules = RuleChain(ApplicationRule(), EdtRule(), disposableRule)

  @Before
  fun before() {
    // This allows normal DataManager functionality:
    HeadlessDataManager.fallbackToProductionDataManager(disposableRule.disposable)
  }

  @Test // Regression test for b/435496119
  fun clickOnShowMoreElements() {
    val id = VIEW1
    var theTable: JTable? = null
    val lookup =
      object : ViewNodeAndResourceLookup {
        override fun get(id: Long): ViewNode? = error("Not Implemented")

        override val resourceLookup: ResourceLookup
          get() = error("Not Implemented")

        override val selection: ViewNode?
          get() = error("Not Implemented")

        override val scope: CoroutineScope
          get() = error("Not Implemented")

        override fun resolve(
          rootId: Long,
          reference: ParameterReference,
          startIndex: Int,
          maxElements: Int,
          callback: (ParameterGroupItem?, PTableGroupModification?) -> Unit,
        ) {
          // Hack: Remove the current editor (the action button for ShowMoreElementsItem),
          // to emulate this callback is happening later where the focus may have changed.
          // This will make this test fail if the table is accessed in the callback to
          // ShowMoreElementsItem.
          theTable?.editingStopped(mock())

          // Create 3 more elements to add to the original group:
          val group = createGroup(this)
          val param3 = ParameterItem("[2]", STRING, "C", PARAMETERS, id, this, -1, 2)
          val param4 = ParameterItem("[3]", STRING, "D", PARAMETERS, id, this, -1, 3)
          val param5 = ParameterItem("[4]", STRING, "E", PARAMETERS, id, this, -1, 3)
          group.children.addAll(listOf(param3, param4, param5))
          callback(group, null)
        }
      }
    val reference = ParameterReference(-1L, 0, ParameterKind.Normal, 2, intArrayOf(0, 1, 2))
    val group = createGroup(lookup, reference)
    val param1 = ParameterItem("[0]", STRING, "A", PARAMETERS, id, lookup, -1, 0)
    val param2 = ParameterItem("[1]", STRING, "B", PARAMETERS, id, lookup, -1, 1)
    val showMore = ShowMoreElementsItem(group)
    group.children.add(param1)
    group.children.add(param2)
    group.children.add(showMore)
    val model = PTableTestModel(group)
    val ptable = PTable.create(model, editorProvider = createEditorProviderFor(showMore))
    val table = ptable.component as JTable
    theTable = table
    table.ui = HeadlessTableUI()
    table.size = Dimension(400, 800)
    val ui = FakeUi(table, createFakeWindow = true)

    // Expand the group
    ptable.toggle(group)

    // Click on the Show More Elements action button:
    val rect = table.getCellRect(3, 1, false)
    ui.clickRelativeTo(table, rect.centerX.toInt(), rect.centerY.toInt())

    // Check that the 3 elements were added successfully:
    assertThat(ptable.item(1).value).isEqualTo("A")
    assertThat(ptable.item(2).value).isEqualTo("B")
    assertThat(ptable.item(3).value).isEqualTo("C")
    assertThat(ptable.item(4).value).isEqualTo("D")
    assertThat(ptable.item(5).value).isEqualTo("E")
    assertThat(ptable.itemCount).isEqualTo(6)
  }

  private fun createGroup(
    lookup: ViewNodeAndResourceLookup,
    reference: ParameterReference? = null,
  ): ParameterGroupItem {
    return ParameterGroupItem(
      "group",
      PropertyType.ITERABLE,
      "value",
      PropertySection.PARAMETERS,
      -1,
      lookup,
      -1L,
      0,
      reference,
      mutableListOf(),
    )
  }

  private fun createEditorProviderFor(showMore: ShowMoreElementsItem): PTableCellEditorProvider {
    val button =
      ActionButton(showMore.link, null, ActionPlaces.UNKNOWN, DEFAULT_MINIMUM_BUTTON_SIZE)
    val defaultEditor = DefaultPTableCellEditor()
    val showMoreEditor =
      object : DefaultPTableCellEditor() {
        override val editorComponent: JComponent?
          get() = button
      }
    val editorProvider =
      object : PTableCellEditorProvider {
        override fun invoke(
          table: PTable,
          item: PTableItem,
          column: PTableColumn,
        ): PTableCellEditor {
          return if (item == showMore) showMoreEditor else defaultEditor
        }
      }
    return editorProvider
  }
}
