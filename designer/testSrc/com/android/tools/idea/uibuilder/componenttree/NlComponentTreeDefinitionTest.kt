/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.componenttree

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.flags.junit.FlagRule
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.laf.HeadlessTableUI
import com.android.tools.adtui.swing.laf.HeadlessTreeUI
import com.android.tools.adtui.workbench.AutoHide
import com.android.tools.adtui.workbench.Side
import com.android.tools.adtui.workbench.Split
import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.componenttree.treetable.TreeTableImpl
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.model.ItemTransferable
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlComponentReference
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.editor.LayoutNavigationManager
import com.android.tools.idea.uibuilder.scene.SyncLayoutlibSceneManager
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.XmlElementFactory
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.verify
import java.awt.Rectangle
import javax.swing.JTree
import javax.swing.SwingUtilities

class NlComponentTreeDefinitionTest {
  private val treeRule = FlagRule(StudioFlags.NELE_NEW_COMPONENT_TREE, true)
  private val projectRule = AndroidProjectRule.withSdk()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(treeRule).around(EdtRule())!!

  @RunsInEdt
  @Test
  fun testSelectionInTreeIsPropagatedToModel() {
    val content = createToolContent()
    val model = createFlowModel()
    val table = attach(content, model)
    val bounds = table.getCellRect(3, 0, true)
    val ui = FakeUi(table)
    ui.mouse.click(bounds.centerX.toInt(), bounds.centerY.toInt())
    UIUtil.dispatchAllInvocationEvents()

    val selection = model.surface.selectionModel.selection
    assertThat(selection).hasSize(1)
    assertThat(selection.first().tagName).isEqualTo(SdkConstants.SWITCH)
  }

  @RunsInEdt
  @Test
  fun testSelectionInModelIsShownInTree() {
    val content = createToolContent()
    val model = createFlowModel()
    val table = attach(content, model)
    val selectionModel = model.surface.selectionModel
    selectionModel.setSelection(listOf(model.find("b")!!))

    assertThat(table.selectedRow).isEqualTo(2)
  }

  @RunsInEdt
  @Test
  fun testStructureChange() {
    val content = createToolContent()
    val model = createFlowModel()
    val table = attach(content, model)
    assertThat(dumpTree(table.tree)).isEqualTo("""
    <android.support.constraint.ConstraintLayout>
      <TextView>
      <Button>
      <Switch>
      <android.support.constraint.helper.Flow>
        @id/a
        @id/b
        @id/c
        @id/include
        @id/linear
      <include>
      <LinearLayout>
        <CheckBox>
    """.trimIndent())

    // Change the model by adding another TextView to the ConstraintLayout
    val constraint = model.components.first()
    val newTag = XmlElementFactory.getInstance(model.project).createTagFromText("<TextView android.text=\"Hello\"/>")
    constraint.addChild(NlComponent(model, newTag))

    // Notify the component tree
    model.notifyModified(NlModel.ChangeType.ADD_COMPONENTS)
    UIUtil.dispatchAllInvocationEvents()

    assertThat(dumpTree(table.tree)).isEqualTo("""
    <android.support.constraint.ConstraintLayout>
      <TextView>
      <Button>
      <Switch>
      <android.support.constraint.helper.Flow>
        @id/a
        @id/b
        @id/c
        @id/include
        @id/linear
      <include>
      <LinearLayout>
        <CheckBox>
      <TextView>
    """.trimIndent())
  }

  @RunsInEdt
  @Test
  fun testComponentActivation() {
    projectRule.fixture.addFileToProject("res/layout/other.xml", "<TextView android:id=\"@id/d\"/>")
    projectRule.mockProjectService(LayoutNavigationManager::class.java)
    val content = createToolContent()
    val model = createFlowModel()
    val table = attach(content, model)
    val rowWithInclude = 10
    assertThat((table.getValueAt(rowWithInclude, 0) as? NlComponent)?.tagName).isEqualTo(SdkConstants.TAG_INCLUDE)
    val bounds = table.getCellRect(rowWithInclude, 0, true) // <include>
    val ui = FakeUi(table)
    ui.mouse.doubleClick(bounds.centerX.toInt(), bounds.centerY.toInt())
    UIUtil.dispatchAllInvocationEvents()

    // Activating an include component should open the associated layout file: other.xml
    val v1 = ArgumentCaptor.forClass(VirtualFile::class.java)
    val v2 = ArgumentCaptor.forClass(VirtualFile::class.java)
    verify(LayoutNavigationManager.getInstance(projectRule.project)).pushFile(v1.capture(), v2.capture())
    assertThat(v1.value.path).endsWith("some_layout.xml")
    assertThat(v2.value.path).endsWith("other.xml")
  }

  @RunsInEdt
  @Test
  fun testReferenceActivation() {
    val content = createToolContent()
    val model = createFlowModel()
    val table = attach(content, model)
    table.tableModel
    val bounds = table.getCellRect(6, 0, true) // "@id/b"
    val ui = FakeUi(table)
    ui.mouse.doubleClick(bounds.centerX.toInt(), bounds.centerY.toInt())
    UIUtil.dispatchAllInvocationEvents()

    // Activating a reference should select the component being referenced: the Button
    val selection = model.surface.selectionModel.selection
    assertThat(selection).hasSize(1)
    assertThat(selection.first().tagName).isEqualTo(SdkConstants.BUTTON)
  }

  @RunsInEdt
  @Test
  fun testComponentToComponentDnD() {
    val content = createToolContent()
    val model = createFlowModel()
    val table = attach(content, model)
    val tableModel = table.tableModel
    val textA = model.find("a")!!
    val button = model.find("b")!!
    val linear = model.find("linear")!!
    val data = tableModel.createTransferable(textA)!!
    assertThat(data.isDataFlavorSupported(ItemTransferable.DESIGNER_FLAVOR)).isTrue()
    assertThat(tableModel.canInsert(linear, data)).isTrue()
    assertThat(tableModel.canInsert(button, data)).isFalse()

    // Move TextView "a" from the constraint layout to the linear layout
    tableModel.insert(linear, data, before = null, isMove = true, listOf(textA))
    UIUtil.dispatchAllInvocationEvents()

    TreeUtil.expandAll(table.tree)
    assertThat(dumpTree(table.tree)).isEqualTo("""
    <android.support.constraint.ConstraintLayout>
      <Button>
      <Switch>
      <android.support.constraint.helper.Flow>
        @id/b
        @id/c
        @id/include
        @id/linear
      <include>
      <LinearLayout>
        <CheckBox>
        <TextView>
    """.trimIndent())
  }

  @RunsInEdt
  @Test
  fun testMoveComponentToConstraintHelperDnD() {
    val content = createToolContent()
    val model = createFlowModel()
    val table = attach(content, model)
    val tableModel = table.tableModel
    val checkBox = model.find("d")!!
    val flow = model.find("flow")!!
    val referenceB = NlComponentReference(flow, "b")
    val data = tableModel.createTransferable(checkBox)!!
    assertThat(data.isDataFlavorSupported(ItemTransferable.DESIGNER_FLAVOR)).isTrue()
    assertThat(tableModel.canInsert(flow, data)).isTrue()

    // Move the CheckBox from the linear layout to the Flow before the b reference (the CheckBox itself should move to the ConstraintLayout)
    tableModel.insert(flow, data, before = referenceB, isMove = true, draggedFromTree = listOf(checkBox))
    UIUtil.dispatchAllInvocationEvents()

    assertThat(dumpTree(table.tree)).isEqualTo("""
    <android.support.constraint.ConstraintLayout>
      <TextView>
      <CheckBox>
      <Button>
      <Switch>
      <android.support.constraint.helper.Flow>
        @id/a
        @id/d
        @id/b
        @id/c
        @id/include
        @id/linear
      <include>
      <LinearLayout>
    """.trimIndent())
  }

  @RunsInEdt
  @Test
  fun testMoveComponentWithinConstraintHelperDnD() {
    val content = createToolContent()
    val model = createFlowModel()
    val table = attach(content, model)
    val tableModel = table.tableModel
    val textView = model.find("a")!!
    val flow = model.find("flow")!!
    val referenceC = NlComponentReference(flow, "c")
    val data = tableModel.createTransferable(textView)!!
    assertThat(data.isDataFlavorSupported(ItemTransferable.DESIGNER_FLAVOR)).isTrue()
    assertThat(tableModel.canInsert(flow, data)).isTrue()

    // Move the TextView from the Flow before the c reference
    tableModel.insert(flow, data, before = referenceC, isMove = true, draggedFromTree = listOf(textView))
    UIUtil.dispatchAllInvocationEvents()

    assertThat(dumpTree(table.tree)).isEqualTo("""
    <android.support.constraint.ConstraintLayout>
      <Button>
      <TextView>
      <Switch>
      <android.support.constraint.helper.Flow>
        @id/b
        @id/a
        @id/c
        @id/include
        @id/linear
      <include>
      <LinearLayout>
        <CheckBox>
    """.trimIndent())
  }

  @RunsInEdt
  @Test
  fun testMoveReferenceDnD() {
    val content = createToolContent()
    val model = createFlowModel()
    val table = attach(content, model)
    val tableModel = table.tableModel
    val flow = model.find("flow")!!
    val referenceB = NlComponentReference(flow, "b")
    val referenceC = NlComponentReference(flow, "c")
    val data = tableModel.createTransferable(referenceC)!!
    assertThat(data.isDataFlavorSupported(ItemTransferable.DESIGNER_FLAVOR)).isTrue()
    assertThat(tableModel.canInsert(flow, data)).isTrue()

    // Move the "c" reference before the "b" reference
    tableModel.insert(flow, data, referenceB, isMove = true, draggedFromTree = listOf(referenceC))
    UIUtil.dispatchAllInvocationEvents()
    Thread.sleep(2000L)

    assertThat(dumpTree(table.tree)).isEqualTo("""
    <android.support.constraint.ConstraintLayout>
      <TextView>
      <Button>
      <Switch>
      <android.support.constraint.helper.Flow>
        @id/a
        @id/c
        @id/b
        @id/include
        @id/linear
      <include>
      <LinearLayout>
        <CheckBox>
    """.trimIndent())
  }

  private fun dumpTree(tree: JTree): String {
    val builder = StringBuilder()
    val rows = tree.rowCount
    for (row in 0 until rows) {
      val path = tree.getPathForRow(row)
      builder.append("  ".repeat(path.pathCount - 1))
      when (val component = path.lastPathComponent) {
        is NlComponent -> builder.appendLine("<${component.tagName}>")
        is NlComponentReference -> builder.appendLine("@id/${component.id}")
        else -> error("unexpected element")
      }
    }
    return builder.toString().trim()
  }

  private fun createFlowModel(): SyncNlModel {
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    return NlModelBuilderUtil.model(
      facet, projectRule.fixture, SdkConstants.FD_RES_LAYOUT, "some_layout.xml",
      component(AndroidXConstants.CONSTRAINT_LAYOUT.defaultName())
        .withBounds(0, 0, 1000, 1000)
        .matchParentWidth()
        .matchParentHeight()
        .children(
          component(SdkConstants.TEXT_VIEW)
            .withBounds(50, 100, 100, 100)
            .id("@+id/a")
            .width("100dp")
            .height("100dp"),
          component(SdkConstants.BUTTON)
            .withBounds(50, 200, 100, 100)
            .id("@+id/b")
            .width("100dp")
            .height("100dp"),
          component(SdkConstants.SWITCH)
            .withBounds(50, 300, 100, 100)
            .id("@+id/c")
            .width("100dp")
            .height("100dp"),
          component(AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_FLOW.defaultName())
            .withBounds(50, 100, 100, 500)
            .id("@+id/flow")
            .viewObjectClassName(AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_HELPER.defaultName())
            .withAttribute(SdkConstants.ATTR_ORIENTATION, "vertical")
            .withAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_LAYOUT_START_TO_START_OF, "parent")
            .withAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_LAYOUT_END_TO_END_OF, "parent")
            .withAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, "parent")
            .withAttribute(SdkConstants.AUTO_URI, SdkConstants.CONSTRAINT_REFERENCED_IDS, "a,b,c,include,linear"),
          component(SdkConstants.VIEW_INCLUDE)
            .id("@+id/include")
            .withAttribute("layout", "@layout/other"),
          component(SdkConstants.LINEAR_LAYOUT)
            .withBounds(0, 500, 1000, 500)
            .id("@+id/linear")
            .matchParentWidth()
            .height("500dp")
            .withAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_LAYOUT_START_TO_START_OF, "parent")
            .withAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_LAYOUT_END_TO_END_OF, "parent")
            .withAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, "parent")
            .children(
              component(SdkConstants.CHECK_BOX)
                .withBounds(0, 500, 100, 100)
                .id("@+id/d")
                .width("100dp")
                .height("100dp"),
            )
        ))
      .build().also {
        val manager = it.surface.sceneManager as? SyncLayoutlibSceneManager
        manager?.ignoreRenderRequests = true
        manager?.ignoreModelUpdateRequests = true
      }
  }

  private fun attach(content: ToolContent<DesignSurface<*>>, model: SyncNlModel): TreeTableImpl {
    val surface = model.surface
    content.setToolContext(surface)
    val panel = content.component
    val table = content.focusedComponent as TreeTableImpl
    assertThat(SwingUtilities.isDescendingFrom(table, panel))
    table.setUI(HeadlessTableUI())
    table.tree.setUI(HeadlessTreeUI())
    table.bounds = Rectangle(0, 0, 400, 800)
    UIUtil.dispatchAllInvocationEvents()
    return table
  }

  private fun createToolContent(): ToolContent<DesignSurface<*>> {
    val definition = NlComponentTreeDefinition(projectRule.project, Side.LEFT, Split.TOP, AutoHide.DOCKED, isPassThroughQueue = true)
    return definition.factory.apply(projectRule.testRootDisposable)
  }

  private fun component(tag: String): ComponentDescriptor = ComponentDescriptor(tag)
}