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
package com.android.tools.idea.uibuilder.property.inspector

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_START
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_TOP
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.ATTR_TEXT_COLOR
import com.android.SdkConstants.ATTR_TEXT_SIZE
import com.android.SdkConstants.ATTR_VISIBILITY
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.PREFIX_ANDROID
import com.android.SdkConstants.TEXT_VIEW
import com.android.SdkConstants.VALUE_WRAP_CONTENT
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property.NlNewPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertiesModel
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.idea.uibuilder.property.support.NlEnumSupportProvider
import com.android.tools.idea.uibuilder.property.testutils.InspectorTestUtil
import com.android.tools.property.ptable.PTable
import com.android.tools.property.ptable.PTableColumn
import com.android.tools.property.ptable.PTableItem
import com.android.tools.property.ptable.PTableModel
import com.android.tools.property.ptable.PTableModelUpdateListener
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.invokeLater
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import javax.swing.JTable
import javax.swing.TransferHandler
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.verify

@RunsInEdt
class DeclaredAttributesInspectorBuilderTest {
  @JvmField @Rule val projectRule = AndroidProjectRule.inMemory()

  @JvmField @Rule val edtRule = EdtRule()

  @Test
  fun testDeclaredAttributes() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    util.checkTitle(0, InspectorSection.DECLARED.title, true)
    val tableModel = util.checkTable(1).tableModel
    util.checkEmptyTableIndicator(2)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertThat(util.inspector.lines).hasSize(3)

    // Check that there are 3 attributes
    assertThat(tableModel.items.map { it.name })
      .containsExactly(ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT, ATTR_TEXT)
      .inOrder()

    // Also check the values
    assertThat(tableModel.items.map { it.value })
      .containsExactly(VALUE_WRAP_CONTENT, VALUE_WRAP_CONTENT, "Testing")
      .inOrder()
  }

  @Test
  fun testInspectorWithAddedNewProperty() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    val titleModel = util.checkTitle(0, InspectorSection.DECLARED.title, true)
    val tableModel = util.checkTable(1).tableModel
    util.checkEmptyTableIndicator(2)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertThat(util.inspector.lines).hasSize(3)

    titleModel.expanded = false
    util.performAction(0, 0, AllIcons.General.Add)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    // Check that the "Declared Attributes" title is now expanded
    assertThat(titleModel.expanded).isTrue()

    // Check that there are 4 attributes
    assertThat(tableModel.items.map { it.name })
      .containsExactly(ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT, ATTR_TEXT, "")
      .inOrder()

    // Also check the values
    assertThat(tableModel.items.map { it.value })
      .containsExactly(VALUE_WRAP_CONTENT, VALUE_WRAP_CONTENT, "Testing", null)
      .inOrder()
  }

  @Test
  fun testAcceptMoveToNextEditorWithEmptyNewPropertyValue() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    util.performAction(0, 0, AllIcons.General.Add)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val declared = util.checkTable(1).tableModel
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

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
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    util.performAction(0, 0, AllIcons.General.Add)

    val declared = util.checkTable(1).tableModel
    val newProperty = declared.items.last() as NlNewPropertyItem
    newProperty.name = PREFIX_ANDROID + ATTR_TEXT_SIZE
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    newProperty.delegate?.value = "10sp"
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

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
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    util.performAction(0, 0, AllIcons.General.Add)

    val declared = util.checkTable(1).tableModel
    val listener: PTableModelUpdateListener = mock()
    declared.addListener(listener)

    util.inspector.refresh()
    verify(listener).itemsUpdated(Mockito.eq(false), Mockito.any())
  }

  @Test
  fun testUpdateItemsWhenPropertyAdded() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    util.performAction(0, 0, AllIcons.General.Add)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val declared = util.checkTable(1).tableModel
    val listener: PTableModelUpdateListener = mock()
    declared.addListener(listener)

    util.properties[ANDROID_URI, ATTR_TEXT_SIZE].value = "12sp"
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    util.inspector.refresh()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    verify(listener).itemsUpdated(Mockito.eq(true), Mockito.any())
  }

  @Test
  fun testDeletePropertyItem() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    val tableLine = util.checkTable(1)
    val model = tableLine.tableModel
    tableLine.selectedItem = model.items[2] // select ATTR_TEXT
    util.performAction(0, 1, AllIcons.General.Remove)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    // Check that there are only 2 declared attributes left
    assertThat(model.items.map { it.name })
      .containsExactly(ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT)
      .inOrder()

    assertThat(util.components[0].getAttribute(ANDROID_URI, ATTR_TEXT)).isNull()
  }

  @Test
  fun testDeletePropertyItemThatIsBeingEdited() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    val tableLine = util.checkTable(1)
    val model = tableLine.tableModel
    val textItem = model.items[2] as NlPropertyItem // ATTR_TEXT
    tableLine.selectedItem = textItem

    // Start editing the item:
    model.editedItem = textItem
    tableLine.pendingEditingAction = { invokeLater { textItem.value = "123" } }

    // Remove the item:
    util.performAction(0, 1, AllIcons.General.Remove)
    tableLine.stopEditing()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    // Check that there are only 2 declared attributes left
    assertThat(model.items.map { it.name })
      .containsExactly(ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT)
      .inOrder()

    assertThat(util.components[0].getAttribute(ANDROID_URI, ATTR_TEXT)).isNull()
  }

  @Test
  fun testDeleteNewlyAddedPropertyItem() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    util.performAction(0, 0, AllIcons.General.Add)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    util.performAction(0, 1, AllIcons.General.Remove)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    // Check that there are only the 3 declared attributes left (the place holder is gone)
    val declared = util.checkTable(1).tableModel
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertThat(declared.items.map { it.name })
      .containsExactly(ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT, ATTR_TEXT)
      .inOrder()
  }

  @Test
  fun testListenersAreConcurrentModificationSafe() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)

    val declared = util.checkTable(1).tableModel
    val listener = RecursiveUpdateListener(declared)
    declared.addListener(listener)

    util.performAction(0, 0, AllIcons.General.Add)
    assertThat(listener.called).isTrue()
  }

  @Test
  fun testPasteFromClipboard() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    val declared = util.checkTable(1).tableModel
    val listener = RecursiveUpdateListener(declared)
    declared.addListener(listener)

    val table = PTable.create(declared).component
    val transferHandler = table.transferHandler
    assertThat(declared.items.map { it.name })
      .containsExactly("layout_width", "layout_height", "text")
    transferHandler.importData(table, StringSelection("textColor\t#22FF22"))
    assertThat(declared.items.map { it.name })
      .containsExactly("layout_width", "layout_height", "text", "textColor")
    assertThat(listener.called).isTrue()
  }

  @Test
  fun testCutToClipboard() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    val declared = util.checkTable(1).tableModel
    val listener = RecursiveUpdateListener(declared)
    declared.addListener(listener)

    val table = PTable.create(declared).component as JTable
    val transferHandler = table.transferHandler
    assertThat(transferHandler.getSourceActions(table)).isEqualTo(TransferHandler.COPY_OR_MOVE)
    table.setRowSelectionInterval(1, 1)
    val clipboard: Clipboard = mock()
    assertThat(declared.items.map { it.name })
      .containsExactly("layout_width", "layout_height", "text")
    transferHandler.exportToClipboard(table, clipboard, TransferHandler.MOVE)
    assertThat(declared.items.map { it.name }).containsExactly("layout_width", "text")
    assertThat(listener.called).isTrue()

    val transferableCaptor = ArgumentCaptor.forClass(Transferable::class.java)
    verify(clipboard).setContents(transferableCaptor.capture(), eq(null))
    val transferable = transferableCaptor.value
    assertThat(transferable.isDataFlavorSupported(DataFlavor.stringFlavor)).isTrue()
    assertThat(transferable.getTransferData(DataFlavor.stringFlavor))
      .isEqualTo("layout_height\twrap_content")
  }

  private fun createBuilder(model: NlPropertiesModel): DeclaredAttributesInspectorBuilder {
    val enumSupportProvider = NlEnumSupportProvider(model)
    return DeclaredAttributesInspectorBuilder(model, enumSupportProvider)
  }

  private class RecursiveUpdateListener(private val model: PTableModel) :
    PTableModelUpdateListener {
    var called = false

    override fun itemsUpdated(modelChanged: Boolean, nextEditedItem: PTableItem?) {
      model.addListener(RecursiveUpdateListener(model))
      called = true
    }
  }

  private fun addProperties(util: InspectorTestUtil) {
    util.addProperty(ANDROID_URI, ATTR_TEXT, NlPropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_TEXT_SIZE, NlPropertyType.FONT_SIZE)
    util.addProperty(ANDROID_URI, ATTR_TEXT_COLOR, NlPropertyType.COLOR)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_WIDTH, NlPropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_HEIGHT, NlPropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_CONTENT_DESCRIPTION, NlPropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_MARGIN, NlPropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT, NlPropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT, NlPropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_MARGIN_START, NlPropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_MARGIN_END, NlPropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_MARGIN_TOP, NlPropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_MARGIN_BOTTOM, NlPropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_VISIBILITY, NlPropertyType.ENUM)

    util.properties[ANDROID_URI, ATTR_TEXT].value = "Testing"
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    util.properties[ANDROID_URI, ATTR_LAYOUT_WIDTH].value = VALUE_WRAP_CONTENT
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    util.properties[ANDROID_URI, ATTR_LAYOUT_HEIGHT].value = VALUE_WRAP_CONTENT
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }
}
