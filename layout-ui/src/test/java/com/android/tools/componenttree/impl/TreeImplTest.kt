@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE") // TODO: remove usage of sun.awt.AWTAccessor.
package com.android.tools.componenttree.impl

import com.android.SdkConstants
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.setPortableUiFont
import com.android.tools.componenttree.api.BadgeItem
import com.android.tools.componenttree.api.ComponentTreeBuilder
import com.android.tools.componenttree.api.ContextPopupHandler
import com.android.tools.componenttree.api.DoubleClickHandler
import com.android.tools.componenttree.util.Item
import com.android.tools.componenttree.util.ItemNodeType
import com.android.tools.componenttree.util.Style
import com.android.tools.componenttree.util.StyleNodeType
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.AbstractExpandableItemsHandler
import com.intellij.util.Alarm
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import sun.awt.AWTAccessor
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.peer.ComponentPeer
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.plaf.basic.BasicTreeUI

class TreeImplTest {
  @get:Rule
  val edtRule = EdtRule()

  private val style1 = Style("style1")
  private val style2 = Style("style2")
  private val item1 = Item(SdkConstants.FQCN_LINEAR_LAYOUT)
  private val item2 = Item(SdkConstants.FQCN_TEXT_VIEW)
  private val item3 = Item(SdkConstants.FQCN_BUTTON)
  private val contextPopup = object : ContextPopupHandler {
    var popupInvokeCount = 0
      private set

    override fun invoke(component: JComponent, x: Int, y: Int) {
      popupInvokeCount++
    }
  }
  private val doubleClickHandler = object : DoubleClickHandler {
    var clickCount = 0
      private set

    override fun invoke() {
      clickCount++
    }
  }
  private val badgeItem = object : BadgeItem {
    var lastActionItem: Any? = null
    var lastPopupItem: Any? = null

    override fun getIcon(item: Any): Icon? = when (item) {
      item1 -> StudioIcons.Common.ADD
      item2 -> StudioIcons.Common.DELETE
      else -> null
    }

    override fun getTooltipText(item: Any?): String = when (item) {
      item1 -> "LinearLayout tip"
      item2 -> "TextView tip"
      style1 -> "style1 tip"
      style2 -> "style2 tip"
      else -> ""
    }

    override fun performAction(item: Any) {
      lastActionItem = item
    }

    override fun showPopup(item: Any, component: JComponent, x: Int, y: Int) {
      lastPopupItem = item
    }
  }

  @Before
  fun setUp() {
    item1.children.addAll(listOf(item2, item3))
    item2.parent = item1
    item3.parent = item1
    item2.children.add(style1)
    style1.parent = item2
    style1.children.add(style2)
    style2.parent = style1

    // Make the icons have their intended size
    IconLoader.activate()

    // Use a similar font for all platforms
    setPortableUiFont()
  }

  @RunsInEdt
  @Test
  fun testContextPopup() {
    val tree = createTree()
    setScrollPaneSize(tree, 300, 800)
    val ui = FakeUi(tree)
    ui.mouse.rightClick(10, 10)
    assertThat(contextPopup.popupInvokeCount).isEqualTo(1)
    assertThat(badgeItem.lastPopupItem).isNull()
  }

  @RunsInEdt
  @Test
  fun testBadgePopup() {
    val tree = createTree()
    setScrollPaneSize(tree, 400, 700)
    val ui = FakeUi(tree)
    ui.mouse.rightClick(390, 10)
    assertThat(contextPopup.popupInvokeCount).isEqualTo(0)
    assertThat(badgeItem.lastPopupItem).isEqualTo(item1)
  }

  @RunsInEdt
  @Test
  fun testBadgePopupWhenScrolled() {
    val tree = createTree()
    val scrollPane = setScrollPaneSize(tree, 20, 20)
    val ui = FakeUi(tree)
    val bounds = tree.getRowBounds(tree.rowCount - 1)
    val right = bounds.maxX.toInt()
    val bottom = bounds.maxY.toInt()
    scrollPane.viewport.viewPosition = Point(right - 20, bottom - 20)
    UIUtil.dispatchAllInvocationEvents()
    ui.mouse.rightClick(right - 10, bottom - 10)
    assertThat(contextPopup.popupInvokeCount).isEqualTo(0)
    assertThat(badgeItem.lastPopupItem).isEqualTo(item3)
  }

  @RunsInEdt
  @Test
  fun testClickOnBadge() {
    val tree = createTree()
    setScrollPaneSize(tree, 400, 700)
    val ui = FakeUi(tree)
    tree.expandRow(0)
    tree.expandRow(1)
    ui.mouse.click(390, 30)
    assertThat(badgeItem.lastActionItem).isEqualTo(item2)
  }

  @RunsInEdt
  @Test
  fun testClickOnBadgeWhenScrolled() {
    val tree = createTree()
    val scrollPane = setScrollPaneSize(tree, 20, 20)
    val ui = FakeUi(tree)
    val bounds = tree.getRowBounds(tree.rowCount - 1)
    val right = bounds.maxX.toInt()
    val bottom = bounds.maxY.toInt()
    scrollPane.viewport.viewPosition = Point(right - 20, bottom - 20)
    UIUtil.dispatchAllInvocationEvents()
    ui.mouse.click(right - 10, bottom - 10)
    assertThat(badgeItem.lastActionItem).isEqualTo(item3)
  }

  @RunsInEdt
  @Test
  fun testDoubleClick() {
    val tree = createTree()
    setScrollPaneSize(tree, 400, 700)
    val ui = FakeUi(tree)
    ui.mouse.doubleClick(10, 10)
    assertThat(doubleClickHandler.clickCount).isEqualTo(1)
  }

  // TODO(b/171255033): With mockito 3.4 is may be possible to override ScreenUtil.getScreenRectangle to avoid HeadlessException
  @Ignore("HeadlessException")
  @RunsInEdt
  @Test
  fun testExpandFirstRowOnHover() {
    val tree = createTree()
    setScrollPaneSize(tree, 90, 700)
    setPeer(tree)
    val alarm = getExpandableItemsHandlerAlarm(tree)
    tree.overrideHasApplicationFocus = { true }
    val ui = FakeUi(tree)
    ui.mouse.moveTo(10, 10)
    alarm.drainRequestsInTest()
    assertThat(tree.expandableTreeItemsHandler.expandedItems).containsExactly(0)
  }

  @RunsInEdt
  @Test
  fun testHoverOnEmptyPartOfTree() {
    val tree = createTree()
    setScrollPaneSize(tree, 90, 700)
    setPeer(tree)
    val alarm = getExpandableItemsHandlerAlarm(tree)
    tree.overrideHasApplicationFocus = { true }
    val ui = FakeUi(tree)
    ui.mouse.moveTo(10, 690)
    alarm.drainRequestsInTest()
    assertThat(tree.expandableTreeItemsHandler.expandedItems).isEmpty()
  }

  @RunsInEdt
  @Test
  fun testHiddenRootIsExpanded() {
    val tree = createTree()
    tree.isRootVisible = false
    tree.showsRootHandles = true
    val hiddenRoot = Item("hidden")
    hiddenRoot.children.add(item1)
    item1.parent = hiddenRoot
    tree.model!!.treeRoot = hiddenRoot
    assertThat(tree.rowCount).isEqualTo(1)
    tree.updateUI()
    assertThat(tree.rowCount).isEqualTo(1)
  }

  private fun setScrollPaneSize(tree: TreeImpl, width: Int, height: Int): JScrollPane {
    val scrollPane = tree.parent.parent as JScrollPane
    scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
    scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
    scrollPane.setBounds(0, 0,
                         width + scrollPane.verticalScrollBar.preferredSize.width,
                         height + scrollPane.horizontalScrollBar.preferredSize.height)
    scrollPane.doLayout()
    tree.parent.doLayout()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    return scrollPane
  }

  private fun createTree(): TreeImpl {
    val scrollPane = createTreeWithScrollPane()
    val tree = scrollPane.viewport.view as TreeImpl
    tree.model!!.treeRoot = item1

    // This avoids a HeadlessException in testDoubleClick
    @Suppress("UsePropertyAccessSyntax") // Prevents compile error 
    tree.setUI(object : BasicTreeUI() {
      override fun createMouseListener() = object : MouseAdapter() {}
    })
    return tree
  }

  private fun createTreeWithScrollPane(): JScrollPane {
    return ComponentTreeBuilder()
      .withNodeType(ItemNodeType())
      .withNodeType(StyleNodeType())
      .withBadgeSupport(badgeItem)
      .withContextMenu(contextPopup)
      .withDoubleClick(doubleClickHandler)
      .withoutTreeSearch()
      .withInvokeLaterOption { it.run() }
      .build().first as JScrollPane
  }

  private fun setPeer(component: Component) {
    val peer = mock(ComponentPeer::class.java)
    `when`(peer.locationOnScreen).thenReturn(Point(0, 0))
    AWTAccessor.getComponentAccessor().setPeer(component, peer)
    component.parent?.let { setPeer(it) }
  }

  private fun getExpandableItemsHandlerAlarm(tree: TreeImpl): Alarm {
    val handler = tree.expandableTreeItemsHandler
    val handlerClass = AbstractExpandableItemsHandler::class.java
    val field = handlerClass.getDeclaredField("myUpdateAlarm")
    field.isAccessible = true
    return field.get(handler) as Alarm
  }
}
