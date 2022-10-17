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
package com.android.tools.componenttree.treetable

import com.android.SdkConstants
import com.android.flags.junit.SetFlagRule
import com.android.testutils.MockitoCleanerRule
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.laf.HeadlessTableUI
import com.android.tools.adtui.swing.laf.HeadlessTreeUI
import com.android.tools.componenttree.api.ComponentTreeBuildResult
import com.android.tools.componenttree.api.ComponentTreeBuilder
import com.android.tools.componenttree.api.IconColumn
import com.android.tools.componenttree.util.Item
import com.android.tools.componenttree.util.ItemNodeType
import com.android.tools.idea.flags.StudioFlags
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import icons.StudioIcons
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.eq
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetContext
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JScrollPane

@RunsInEdt
class TreeTableDropTargetHandlerTest {

  companion object {
    @JvmField
    @ClassRule
    val rule = ApplicationRule()
  }

  @get:Rule
  val chain = RuleChain
    .outerRule(SetFlagRule(StudioFlags.USE_COMPONENT_TREE_TABLE, true))
    .around(MockitoCleanerRule())
    .around(IconLoaderRule())
    .around(EdtRule())!!

  private val item1 = Item(SdkConstants.FQCN_LINEAR_LAYOUT)
  private val item2 = Item(SdkConstants.FQCN_GRID_LAYOUT)
  private val item3 = Item(SdkConstants.FQCN_BUTTON)
  private val item4 = Item(SdkConstants.FQCN_RELATIVE_LAYOUT)
  private val item5 = Item(SdkConstants.FQCN_CHECK_BOX)
  private val item6 = Item(SdkConstants.FQCN_TEXT_VIEW)

  private val badgeItem = object : IconColumn("Badge") {
    override fun getIcon(item: Any): Icon? = when (item) {
      item1 -> StudioIcons.Common.ERROR
      item2 -> StudioIcons.Common.FILTER
      else -> StudioIcons.Common.CLOSE
    }

    override fun getTooltipText(item: Any): String = ""
    override fun performAction(item: Any, component: JComponent, bounds: Rectangle) {}
    override fun showPopup(item: Any, component: JComponent, x: Int, y: Int) {}
  }

  // row 0: item1  (layout)
  // row 1:   item2  (layout)
  // row 2:     item3
  // row 3:     item4  (layout)
  // row 4:       item5
  // row 5:       item6

  @Before
  fun before() {
    item1.add(item2)
    item2.add(item3, item4)
    item4.add(item5, item6)
  }

  @Test
  fun testDragEnter() {
    val table = createTreeTable()
    val handler = TreeTableDropTargetHandler(table) { null }
    val event = createDropTargetDragEvent(table, Point(10, table.rowHeight - 2))
    handler.dragEnter(event)
    checkPaint(table, handler, 0, 1)
    verify(table).repaint()
    verify(event, never()).acceptDrag(anyInt())
  }

  @Test
  fun testDragExitResetsInsertionRow() {
    val table = createTreeTable()
    val handler = TreeTableDropTargetHandler(table) { null }
    val event = createDropTargetDragEvent(table, Point(10, table.rowHeight - 2))
    handler.dragEnter(event)
    checkPaint(table, handler, 0, 1)
    verify(table).repaint()
    verify(event, never()).acceptDrag(anyInt())

    handler.dragExit(event)
    checkNoPaint(handler)
    verify(table, times(2)).repaint()
    verify(event, never()).acceptDrag(anyInt())
  }

  @Test
  fun testDragOverBadgeResetsInsertionRow() {
    val table = createTreeTable()
    val handler = TreeTableDropTargetHandler(table) { null }
    val event = createDropTargetDragEvent(table, Point(10, table.rowHeight - 2))
    handler.dragEnter(event)
    checkPaint(table, handler, 0, 1)
    verify(table).repaint()
    verify(event, never()).acceptDrag(anyInt())

    val rect = table.getCellRect(0, 1, true)
    event.location.x = rect.x + rect.width / 2
    handler.dragOver(event)
    checkNoPaint(handler)
    verify(table, times(2)).repaint()
    verify(event).acceptDrag(eq(0))
  }

  @Test
  fun testDragOverBottomOfItem5() {
    val table = createTreeTable()
    val rowHeight = table.rowHeight
    val handler = TreeTableDropTargetHandler(table) { null }
    val event = createDropTargetDragEvent(table, Point(10, 5 * rowHeight - 3))
    handler.dragOver(event)
    checkPaint(table, handler, 3, 5)
    verify(table).repaint()
    verify(event, never()).acceptDrag(anyInt())
  }

  @Test
  fun testReceiverDependOnDepth() {
    val table = createTreeTable()
    val depth2 = table.computeLeftOffset(2)
    val depth3 = table.computeLeftOffset(3)
    val depth4 = table.computeLeftOffset(4)
    val rowHeight = table.rowHeight
    val handler = TreeTableDropTargetHandler(table) { null }
    val event = createDropTargetDragEvent(table, Point(depth4 + 3, 6 * rowHeight - 3))
    handler.dragOver(event)
    checkPaint(table, handler, 3, 6)
    verify(table).repaint()
    verify(event, never()).acceptDrag(anyInt())

    // Moving the mouse to the left will cause a receiver higher in the hierarchy to be selected as the receiver
    event.location.x = depth3 + 3
    handler.dragOver(event)
    checkPaint(table, handler, 1, 6)
    verify(table, times(2)).repaint()
    verify(event, never()).acceptDrag(anyInt())

    // Moving the mouse more to the left will cause a receiver even higher in the hierarchy to be selected as the receiver
    event.location.x = depth2 + 3
    handler.dragOver(event)
    checkPaint(table, handler, 0, 6)
    verify(table, times(3)).repaint()
    verify(event, never()).acceptDrag(anyInt())
  }

  @Test
  fun testNoPossibleReceivers() {
    item1.canInsert = false
    item2.canInsert = false
    item4.canInsert = false
    val table = createTreeTable()
    val depth4 = table.computeLeftOffset(4)
    val rowHeight = table.rowHeight
    val handler = TreeTableDropTargetHandler(table) { null }
    val event = createDropTargetDragEvent(table, Point(depth4 + 3, 6 * rowHeight - 3))
    handler.dragOver(event)
    checkNoPaint(handler)
    verify(table, never()).repaint()
    verify(event).acceptDrag(eq(0))
  }

  @Test
  fun testCannotDragItemIntoItself() {
    val table = createTreeTable()
    val depth4 = table.computeLeftOffset(4)
    val rowHeight = table.rowHeight
    val handler = TreeTableDropTargetHandler(table) { item2 }  // We are dragging item2 to the end
    val event = createDropTargetDragEvent(table, Point(depth4 + 3, 6 * rowHeight - 3))

    // Even though the drag location specify item3,
    // the dragged item2 should not be accepted in item3 or item2, but can be accepted in item1.
    handler.dragOver(event)
    checkPaint(table, handler, 0, 6)
    verify(table).repaint()
    verify(event, never()).acceptDrag(anyInt())
  }

  @Test
  fun testPaint() {
    val table = createTreeTable()
    table.setSize(400, 800)
    val depth4 = table.computeLeftOffset(4)
    val rowHeight = table.rowHeight
    val handler = TreeTableDropTargetHandler(table) { null }
    val event = createDropTargetDragEvent(table, Point(depth4 + 3, 6 * rowHeight - 3))
    handler.dragOver(event)
    checkPaint(table, handler, 3, 6)
  }

  @Test
  fun testDrop() {
    val table = createTreeTable()
    val depth4 = table.computeLeftOffset(4)
    val rowHeight = table.rowHeight
    val handler = TreeTableDropTargetHandler(table) { null }
    val event = createDropTargetDragEvent(table, Point(depth4 + 3, 6 * rowHeight - 3))
    handler.dragOver(event)
    checkPaint(table, handler, 3, 6)

    val dropEvent: DropTargetDropEvent = mock()
    whenever(dropEvent.dropAction).thenReturn(DnDConstants.ACTION_MOVE)
    whenever(dropEvent.transferable).thenReturn(mock())
    handler.drop(dropEvent)
    checkNoPaint(handler)
    verify(table, times(2)).repaint()
    verify(dropEvent).acceptDrop(eq(DnDConstants.ACTION_MOVE))
    verify(dropEvent).dropComplete(eq(true))
  }

  @Test
  fun testDropButNotAccepted() {
    item4.acceptInsert = false
    val table = createTreeTable()
    val depth4 = table.computeLeftOffset(4)
    val rowHeight = table.rowHeight
    val handler = TreeTableDropTargetHandler(table) { null }
    val event = createDropTargetDragEvent(table, Point(depth4 + 3, 6 * rowHeight - 3))
    handler.dragOver(event)
    checkPaint(table, handler, 3, 6)

    val dropEvent: DropTargetDropEvent = mock()
    whenever(dropEvent.dropAction).thenReturn(DnDConstants.ACTION_MOVE)
    whenever(dropEvent.transferable).thenReturn(mock())
    handler.drop(dropEvent)
    checkNoPaint(handler)
    verify(table, times(2)).repaint()
    verify(dropEvent, never()).acceptDrop(anyInt())
    verify(dropEvent, never()).dropComplete(any())
  }

  private fun checkNoPaint(handler: TreeTableDropTargetHandler) {
    val g: Graphics = mock()
    handler.paintDropTargetPosition(g)
    verifyNoInteractions(g)
  }

  private fun checkPaint(table: TreeTableImpl, handler: TreeTableDropTargetHandler, expectedReceiverRow: Int, expectedInsertionRow: Int) {
    val g: Graphics = mock()
    val g2: Graphics2D = mock()
    whenever(g.create()).thenReturn(g2)
    handler.paintDropTargetPosition(g)
    val rb = table.tree.getRowBounds(expectedReceiverRow)
    val ib = table.tree.getRowBounds(maxOf(0, expectedInsertionRow - 1))
    inOrder(g2).apply {
      verify(g2).color = eq(ColorUtil.brighter(UIUtil.getTreeSelectionBackground(true), 10))
      verify(g2).setRenderingHint(eq(RenderingHints.KEY_ANTIALIASING), eq(RenderingHints.VALUE_ANTIALIAS_ON))
      verify(g2).drawRect(maxOf(0, rb.x - 2), rb.y, rb.width + 2, rb.height)
      verify(g2).drawLine(rb.x + 6, ib.y + ib.height, ib.x + ib.width, ib.y + ib.height)
      verify(g2).drawPolygon(any())
      verify(g2).fillPolygon(any())
      verify(g2).stroke = any()
      verify(g2).drawLine(rb.x + 7, rb.y + rb.height, rb.x + 7, ib.y + ib.height)
      verify(g2).dispose()
      verifyNoMoreInteractions()
    }
  }

  private fun createTreeTable(): TreeTableImpl {
    val result = createTreeWithScrollPane()
    val table = (result.component as JScrollPane).viewport.view as TreeTableImpl
    result.model.treeRoot = item1

    table.setUI(HeadlessTableUI())
    table.tree.setUI(HeadlessTreeUI())
    TreeUtil.expandAll(table.tree)
    table.setSize(400, 800)
    table.doLayout()
    runInEdtAndWait { UIUtil.dispatchAllInvocationEvents() }
    return spy(table)
  }

  private fun createTreeWithScrollPane(): ComponentTreeBuildResult {
    return ComponentTreeBuilder()
      .withNodeType(ItemNodeType())
      .withBadgeSupport(badgeItem)
      .withoutTreeSearch()
      .withInvokeLaterOption { it.run() }
      .build()
  }

  private fun createDropTargetDragEvent(component: JComponent, location: Point): DropTargetDragEvent {
    val dropTarget: DropTarget = mock() // The constructor is throwing HeadlessException
    val context: DropTargetContext = mock()
    whenever(dropTarget.component).thenReturn(component)
    whenever(dropTarget.dropTargetContext).thenReturn(context)
    whenever(context.dropTarget).thenReturn(dropTarget)
    whenever(context.component).thenReturn(component)
    val event = spy(DropTargetDragEvent(dropTarget.dropTargetContext, location, DnDConstants.ACTION_MOVE, DnDConstants.ACTION_COPY_OR_MOVE))
    whenever(event.transferable).thenReturn(mock())
    return event
  }
}
