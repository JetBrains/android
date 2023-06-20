/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.property

import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT_END_TO_END_OF
import com.android.SdkConstants.ATTR_ORIENTATION
import com.android.SdkConstants.ATTR_TEXT_SIZE
import com.android.SdkConstants.MotionSceneTags.CUSTOM_ATTRIBUTE
import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs
import com.android.tools.idea.uibuilder.handlers.motion.property.testutil.MotionAttributeRule
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.idea.uibuilder.property.support.NlEnumSupportProvider
import com.android.tools.idea.uibuilder.property.support.NlTwoStateBooleanControlTypeProvider
import com.android.tools.property.panel.api.EditorProvider.Companion.create
import com.android.tools.property.panel.api.TableUIProvider
import com.android.tools.property.panel.impl.model.util.FakeInspectorPanel
import com.android.tools.property.ptable.PTable
import com.android.tools.property.ptable.PTableColumn
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import javax.swing.JTable
import javax.swing.TransferHandler

@RunsInEdt
class MotionLayoutAttributesViewTest {
  private val projectRule = AndroidProjectRule.withSdk()
  private val motionRule = MotionAttributeRule(projectRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(motionRule).around(EdtRule())!!

  @Before
  fun beforeEachTest() {
    val actionManager = projectRule.mockService(ActionManager::class.java)
    Mockito.doAnswer { invocation -> SomeAction(invocation.getArgument(0)) }.whenever(actionManager).getAction(ArgumentMatchers.anyString())
  }

  @Test
  fun testCutConstraint() {
    motionRule.selectConstraint("start", "widget")
    val properties = motionRule.properties.getValue(MotionSceneAttrs.Tags.CONSTRAINT)
    val inspector = FakeInspectorPanel()
    val builder = createBuilder()
    builder.attachToInspector(inspector, properties)
    val constraints = inspector.checkTable(2).tableModel
    val table = PTable.create(constraints).component as JTable
    val transferHandler = table.transferHandler
    table.setRowSelectionInterval(2, 2)
    val clipboard: Clipboard = MockitoKt.mock()
    assertThat(constraints.items.size).isEqualTo(17)
    assertThat(constraints.items.map { it.name }).contains(ATTR_LAYOUT_END_TO_END_OF)
    transferHandler.exportToClipboard(table, clipboard, TransferHandler.MOVE)
    assertThat(constraints.items.size).isEqualTo(16)
    assertThat(constraints.items.map { it.name }).doesNotContain(ATTR_LAYOUT_END_TO_END_OF)

    val transferableCaptor = ArgumentCaptor.forClass(Transferable::class.java)
    Mockito.verify(clipboard).setContents(transferableCaptor.capture(), MockitoKt.eq(null))
    val transferable = transferableCaptor.value
    assertThat(transferable.isDataFlavorSupported(DataFlavor.stringFlavor)).isTrue()
    assertThat(transferable.getTransferData(DataFlavor.stringFlavor)).isEqualTo("$ATTR_LAYOUT_END_TO_END_OF\tparent")
  }

  @Test
  fun testPasteConstraint() {
    motionRule.selectConstraint("start", "widget")
    val properties = motionRule.properties.getValue(MotionSceneAttrs.Tags.CONSTRAINT)
    val inspector = FakeInspectorPanel()
    val builder = createBuilder()
    builder.attachToInspector(inspector, properties)
    val constraints = inspector.checkTable(2).tableModel
    val table = PTable.create(constraints).component as JTable
    val transferHandler = table.transferHandler
    assertThat(constraints.items.map { it.name }).doesNotContain(ATTR_ORIENTATION)
    transferHandler.importData(table, StringSelection("$ATTR_ORIENTATION\t#vertical"))
    assertThat(constraints.items.map { it.name }).contains(ATTR_ORIENTATION)
  }

  @Test
  fun testCutCustomConstraint() {
    motionRule.selectConstraint("start", "widget")
    val properties = motionRule.properties.getValue(MotionSceneAttrs.Tags.CONSTRAINT)
    val inspector = FakeInspectorPanel()
    val builder = createBuilder()
    builder.attachToInspector(inspector, properties)
    val constraints = inspector.checkTable(23).tableModel
    val table = PTable.create(constraints).component as JTable
    val transferHandler = table.transferHandler
    table.setRowSelectionInterval(0, 0)
    val clipboard: Clipboard = MockitoKt.mock()
    assertThat(constraints.items.size).isEqualTo(1)
    assertThat(constraints.items.map { it.name }).contains(ATTR_TEXT_SIZE)
    transferHandler.exportToClipboard(table, clipboard, TransferHandler.MOVE)
    assertThat(constraints.items.size).isEqualTo(0)
    assertThat(constraints.items.map { it.name }).doesNotContain(ATTR_TEXT_SIZE)

    val transferableCaptor = ArgumentCaptor.forClass(Transferable::class.java)
    Mockito.verify(clipboard).setContents(transferableCaptor.capture(), MockitoKt.eq(null))
    val transferable = transferableCaptor.value
    assertThat(transferable.isDataFlavorSupported(DataFlavor.stringFlavor)).isTrue()
    assertThat(transferable.getTransferData(DataFlavor.stringFlavor)).isEqualTo("$ATTR_TEXT_SIZE\t2sp")
  }

  @Test
  fun testPasteCustomConstraint() {
    motionRule.selectConstraint("start", "widget")
    val properties = motionRule.properties.getValue(MotionSceneAttrs.Tags.CONSTRAINT)
    val inspector = FakeInspectorPanel()
    val builder = createBuilder()
    builder.attachToInspector(inspector, properties)
    val constraints = inspector.checkTable(23).tableModel
    val table = PTable.create(constraints).component as JTable
    val transferHandler = table.transferHandler
    assertThat(constraints.items.map { it.name }).doesNotContain(ATTR_BACKGROUND)
    transferHandler.importData(table, StringSelection("$ATTR_BACKGROUND\t@color/drawableA"))
    val background = constraints.items.find { it.name == ATTR_BACKGROUND } as? NlPropertyItem
    assertThat(background).isNotNull()
    assertThat(background?.optionalValue2).isEqualTo(CUSTOM_ATTRIBUTE)
    assertThat(background?.type).isEqualTo(NlPropertyType.COLOR_STATE_LIST)
  }

  @Test
  fun testIdFieldsAreReadonly() {
    motionRule.selectConstraint("start", "widget")
    val properties = motionRule.properties.getValue(MotionSceneAttrs.Tags.CONSTRAINT)
    val inspector = FakeInspectorPanel()
    val builder = createBuilder()
    builder.attachToInspector(inspector, properties)
    val constraints = inspector.checkTable(2).tableModel
    val nonEditable = properties.values.filter { !constraints.isCellEditable(it, PTableColumn.VALUE) }
    assertThat(nonEditable).hasSize(1)
    assertThat(nonEditable.single().name).isEqualTo(ATTR_ID)
  }

  private fun createBuilder(): MotionLayoutAttributesView.MotionInspectorBuilder {
    val enumSupportProvider = NlEnumSupportProvider(motionRule.attributesModel)
    val controlTypeProvider = NlTwoStateBooleanControlTypeProvider(enumSupportProvider)
    val editorProvider = create(enumSupportProvider, controlTypeProvider)
    val tableUIProvider = TableUIProvider(controlTypeProvider, editorProvider)
    return MotionLayoutAttributesView.MotionInspectorBuilder(motionRule.attributesModel, tableUIProvider, enumSupportProvider)
  }

  private class SomeAction(title: String) : AnAction(title) {
    override fun actionPerformed(event: AnActionEvent) {}
  }
}