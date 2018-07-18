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
package com.android.tools.idea.uibuilder.property2.inspector

import com.android.SdkConstants.*
import com.android.tools.adtui.ptable2.DefaultPTableCellEditorProvider
import com.android.tools.adtui.ptable2.DefaultPTableCellRendererProvider
import com.android.tools.adtui.ptable2.PTableColumn
import com.android.tools.adtui.ptable2.PTableModelUpdateListener
import com.android.tools.idea.common.property2.api.TableUIProvider
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property2.NeleNewPropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.testutils.FakeTableLine
import com.android.tools.idea.uibuilder.property2.testutils.InspectorTestUtil
import com.android.tools.idea.uibuilder.property2.testutils.LineType
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.*

@RunsInEdt
class AdvancedInspectorBuilderTest {
  @JvmField @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @JvmField @Rule
  val edtRule = EdtRule()

  @Test
  fun testAdvancedInspector() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, LINEAR_LAYOUT)
    addProperties(util)
    val builder = AdvancedInspectorBuilder(util.model, TestTableUIProvider())
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(4)
    assertThat(util.inspector.lines[0].type).isEqualTo(LineType.TITLE)
    assertThat(util.inspector.lines[1].type).isEqualTo(LineType.TABLE)
    assertThat(util.inspector.lines[2].type).isEqualTo(LineType.TITLE)
    assertThat(util.inspector.lines[3].type).isEqualTo(LineType.TABLE)

    assertThat(util.inspector.lines[0].title).isEqualTo("Declared Attributes")
    assertThat(util.inspector.lines[0].expandable).isTrue()
    assertThat(util.inspector.lines[2].title).isEqualTo("All Attributes")
    assertThat(util.inspector.lines[2].expandable).isTrue()

    // Check the 3 declared attributes
    assertThat(util.inspector.lines[1].tableModel?.items?.map { it.name })
      .containsExactly(ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT, ATTR_TEXT).inOrder()

    // Also check the values of the 3 declared attributes
    assertThat(util.inspector.lines[1].tableModel?.items?.map { it.value })
      .containsExactly(VALUE_WRAP_CONTENT, VALUE_WRAP_CONTENT, "Testing").inOrder()

    // Check all 6 attributes:
    assertThat(util.inspector.lines[3].tableModel?.items?.map { it.name })
      .containsExactly(ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT, ATTR_CONTENT_DESCRIPTION,
                       ATTR_TEXT, ATTR_TEXT_COLOR, ATTR_TEXT_SIZE).inOrder()
  }

  @Test
  fun testAdvancedInspectorWithAddedNewProperty() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, LINEAR_LAYOUT)
    addProperties(util)
    val builder = AdvancedInspectorBuilder(util.model, TestTableUIProvider())
    builder.attachToInspector(util.inspector, util.properties)
    performAddNewRowAction(util)

    assertThat(util.inspector.lines).hasSize(4)
    assertThat(util.inspector.lines[0].type).isEqualTo(LineType.TITLE)
    assertThat(util.inspector.lines[1].type).isEqualTo(LineType.TABLE)
    assertThat(util.inspector.lines[2].type).isEqualTo(LineType.TITLE)
    assertThat(util.inspector.lines[3].type).isEqualTo(LineType.TABLE)

    assertThat(util.inspector.lines[0].title).isEqualTo("Declared Attributes")
    assertThat(util.inspector.lines[0].expandable).isTrue()
    assertThat(util.inspector.lines[0].actions.size).isEqualTo(2)
    assertThat(util.inspector.lines[2].title).isEqualTo("All Attributes")
    assertThat(util.inspector.lines[2].expandable).isTrue()

    // Check the 4 declared attributes including a new property place holder
    assertThat(util.inspector.lines[1].tableModel?.items?.map { it.name })
      .containsExactly(ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT, ATTR_TEXT, "").inOrder()

    // Also check the values of the 4 declared attributes including a new property place holder (which value is blank)
    assertThat(util.inspector.lines[1].tableModel?.items?.map { it.value })
      .containsExactly(VALUE_WRAP_CONTENT, VALUE_WRAP_CONTENT, "Testing", null).inOrder()

    // Check all 6 attributes:
    assertThat(util.inspector.lines[3].tableModel?.items?.map { it.name })
      .containsExactly(ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT, ATTR_CONTENT_DESCRIPTION,
                       ATTR_TEXT, ATTR_TEXT_COLOR, ATTR_TEXT_SIZE).inOrder()
  }

  @Test
  fun testAcceptMoveToNextEditorWithEmptyNewPropertyValue() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, LINEAR_LAYOUT)
    addProperties(util)
    val builder = AdvancedInspectorBuilder(util.model, TestTableUIProvider())
    builder.attachToInspector(util.inspector, util.properties)
    performAddNewRowAction(util)

    val declared = util.inspector.lines[1].tableModel!!
    assertThat(declared.acceptMoveToNextEditor(declared.items[0], PTableColumn.NAME)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[0], PTableColumn.VALUE)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[1], PTableColumn.NAME)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[1], PTableColumn.VALUE)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[2], PTableColumn.NAME)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[2], PTableColumn.VALUE)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[3], PTableColumn.NAME)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[3], PTableColumn.VALUE)).isTrue()
  }

  @Test
  fun testAcceptMoveToNextEditorWithSpecifiedNewPropertyValue() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, LINEAR_LAYOUT)
    addProperties(util)
    val builder = AdvancedInspectorBuilder(util.model, TestTableUIProvider())
    builder.attachToInspector(util.inspector, util.properties)
    performAddNewRowAction(util)

    val declared = util.inspector.lines[1].tableModel!!
    val newProperty = declared.items.last() as NeleNewPropertyItem
    newProperty.name = PREFIX_ANDROID + ATTR_TEXT_SIZE
    newProperty.delegate?.value = "10sp"

    assertThat(declared.acceptMoveToNextEditor(declared.items[0], PTableColumn.NAME)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[0], PTableColumn.VALUE)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[1], PTableColumn.NAME)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[1], PTableColumn.VALUE)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[2], PTableColumn.NAME)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[2], PTableColumn.VALUE)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[3], PTableColumn.NAME)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[3], PTableColumn.VALUE)).isFalse()
  }

  @Test
  fun testUpdateItemsWhenNoChanges() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, LINEAR_LAYOUT)
    addProperties(util)
    val builder = AdvancedInspectorBuilder(util.model, TestTableUIProvider())
    builder.attachToInspector(util.inspector, util.properties)
    performAddNewRowAction(util)

    val declared = util.inspector.lines[1].tableModel!!
    val listener = mock(PTableModelUpdateListener::class.java)
    declared.addListener(listener)

    forcePropertyValueChangedNotification(util)
    verifyZeroInteractions(listener)
  }

  @Test
  fun testUpdateItemsWhenPropertyAdded() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, LINEAR_LAYOUT)
    addProperties(util)
    val builder = AdvancedInspectorBuilder(util.model, TestTableUIProvider())
    builder.attachToInspector(util.inspector, util.properties)
    performAddNewRowAction(util)

    val declared = util.inspector.lines[1].tableModel!!
    val listener = mock(PTableModelUpdateListener::class.java)
    declared.addListener(listener)

    util.properties[ANDROID_URI, ATTR_TEXT_SIZE].value = "12sp"
    forcePropertyValueChangedNotification(util)
    verify(listener).itemsUpdated()
  }

  @Test
  fun testDeletePropertyItem() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, LINEAR_LAYOUT)
    addProperties(util)
    val builder = AdvancedInspectorBuilder(util.model, TestTableUIProvider())
    builder.attachToInspector(util.inspector, util.properties)
    val tableLine = util.inspector.lines[1] as FakeTableLine
    val model = tableLine.tableModel
    tableLine.selectedItem = model.items[2] // select ATTR_TEXT
    performDeleteRowAction(util)

    // Check that there are only 2 declared attributes left
    assertThat(model.items.map { it.name })
      .containsExactly(ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT).inOrder()

    assertThat(util.components[0].getAttribute(ANDROID_URI, ATTR_TEXT)).isNull()
  }

  @Test
  fun testDeleteNewlyAddedPropertyItem() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, LINEAR_LAYOUT)
    addProperties(util)
    val builder = AdvancedInspectorBuilder(util.model, TestTableUIProvider())
    builder.attachToInspector(util.inspector, util.properties)
    performAddNewRowAction(util)
    performDeleteRowAction(util)

    // Check that there are only the 3 declared attributes left (the place holder is gone)
    assertThat(util.inspector.lines[1].tableModel?.items?.map { it.name })
      .containsExactly(ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT, ATTR_TEXT).inOrder()
  }

  private class TestTableUIProvider : TableUIProvider {
    override val tableCellRendererProvider = DefaultPTableCellRendererProvider()
    override val tableCellEditorProvider = DefaultPTableCellEditorProvider()
  }

  private fun addProperties(util: InspectorTestUtil) {
    util.addProperty(ANDROID_URI, ATTR_TEXT, NelePropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_TEXT_SIZE, NelePropertyType.FONT_SIZE)
    util.addProperty(ANDROID_URI, ATTR_TEXT_COLOR, NelePropertyType.COLOR)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_WIDTH, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_HEIGHT, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_CONTENT_DESCRIPTION, NelePropertyType.STRING)

    util.properties[ANDROID_URI, ATTR_TEXT].value = "Testing"
    util.properties[ANDROID_URI, ATTR_LAYOUT_WIDTH].value = VALUE_WRAP_CONTENT
    util.properties[ANDROID_URI, ATTR_LAYOUT_HEIGHT].value = VALUE_WRAP_CONTENT
  }

  private fun performAddNewRowAction(util: InspectorTestUtil) {
    // Add a new property line in the table
    val addNewPropertyAction = util.inspector.lines[0].actions[0]
    val event = mock(AnActionEvent::class.java)
    addNewPropertyAction.actionPerformed(event)
  }

  private fun performDeleteRowAction(util: InspectorTestUtil) {
    // Remove the selected row in the table
    val addNewPropertyAction = util.inspector.lines[0].actions[1]
    val event = mock(AnActionEvent::class.java)
    addNewPropertyAction.actionPerformed(event)
  }

  private fun forcePropertyValueChangedNotification(util: InspectorTestUtil) {
    // This line will cause the model to dispatch a properties changed event
    util.model.showResolvedValues = false
  }
}
