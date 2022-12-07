@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE") // TODO: remove usage of sun.awt.AWTAccessor.
package com.android.tools.componenttree.impl

import com.android.SdkConstants
import com.android.flags.junit.SetFlagRule
import com.android.testutils.MockitoCleanerRule
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.componenttree.api.ComponentTreeBuilder
import com.android.tools.componenttree.api.ContextPopupHandler
import com.android.tools.componenttree.api.DoubleClickHandler
import com.android.tools.componenttree.api.IconColumn
import com.android.tools.componenttree.util.Item
import com.android.tools.componenttree.util.ItemNodeType
import com.android.tools.componenttree.util.Style
import com.android.tools.componenttree.util.StyleNodeType
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.ui.AbstractExpandableItemsHandler
import com.intellij.ui.ScreenUtil
import com.intellij.ui.popup.MovablePopup
import com.intellij.util.Alarm
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.mockStatic
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.plaf.basic.BasicTreeUI

class TreeImplTest {
  private val disposableRule = DisposableRule()

  companion object {
    @JvmField
    @ClassRule
    val rule = ApplicationRule()
  }

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(EdtRule())
    .around(PortableUiFontRule())
    .around(MockitoCleanerRule())
    .around(SetFlagRule(StudioFlags.USE_COMPONENT_TREE_TABLE, false))
    .around(disposableRule)

  private val style1 = Style("style1")
  private val style2 = Style("style2")
  private val item1 = Item(SdkConstants.FQCN_LINEAR_LAYOUT)
  private val item2 = Item(SdkConstants.FQCN_TEXT_VIEW)
  private val item3 = Item(SdkConstants.FQCN_BUTTON)
  private val contextPopup = object : ContextPopupHandler {
    var popupInvokeCount = 0
      private set

    override fun invoke(item: Any, component: JComponent, x: Int, y: Int) {
      popupInvokeCount++
    }
  }
  private val doubleClickHandler = object : DoubleClickHandler {
    var clickCount = 0
      private set
    var lastItem: Any? = null

    override fun invoke(item: Any) {
      clickCount++
      lastItem = item
    }
  }
  private val badgeItem = object : IconColumn("Badge") {
    var lastActionItem: Any? = null
    var lastActionComponent: JComponent? = null
    var lastActionBounds: Rectangle? = null
    var lastPopupItem: Any? = null

    override fun getIcon(item: Any): Icon? = when (item) {
      item1 -> StudioIcons.Common.ERROR
      item2 -> StudioIcons.Common.FILTER
      else -> null
    }

    override fun getTooltipText(item: Any): String = when (item) {
      item1 -> "LinearLayout tip"
      item2 -> "TextView tip"
      style1 -> "style1 tip"
      style2 -> "style2 tip"
      else -> ""
    }

    override fun performAction(item: Any, component: JComponent, bounds: Rectangle) {
      lastActionItem = item
      lastActionComponent = component
      lastActionBounds = bounds
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
    val scrollPane = setScrollPaneSize(tree, 200, 20)
    val ui = FakeUi(tree)
    val bounds = tree.getRowBounds(tree.rowCount - 1)
    val bottom = bounds.maxY.toInt()
    scrollPane.viewport.viewPosition = Point(0, bottom - 20)
    UIUtil.dispatchAllInvocationEvents()
    ui.mouse.rightClick(190, bottom - 10)
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
    val bounds = tree.getRowBounds(1)
    ui.mouse.click(390, 30)
    assertThat(badgeItem.lastActionItem).isEqualTo(item2)
    assertThat(badgeItem.lastActionComponent).isSameAs(tree)
    assertThat(badgeItem.lastActionBounds).isEqualTo(Rectangle(384, bounds.y, 16, bounds.height))
  }

  @RunsInEdt
  @Test
  fun testClickOnBadgeWhenScrolled() {
    val tree = createTree()
    val scrollPane = setScrollPaneSize(tree, 300, 20)
    val ui = FakeUi(tree)
    val bounds = tree.getRowBounds(tree.rowCount - 1)
    val bottom = bounds.maxY.toInt()
    scrollPane.viewport.viewPosition = Point(0, bottom - 20)
    UIUtil.dispatchAllInvocationEvents()
    ui.mouse.click(290, bottom - 10)
    assertThat(badgeItem.lastActionItem).isEqualTo(item3)
    assertThat(badgeItem.lastActionComponent).isSameAs(tree)
    assertThat(badgeItem.lastActionBounds).isEqualTo(Rectangle(284, bounds.y, 16, bounds.height))
  }

  @RunsInEdt
  @Test
  fun testDoubleClick() {
    val tree = createTree()
    setScrollPaneSize(tree, 400, 700)
    val ui = FakeUi(tree)
    ui.mouse.doubleClick(10, 10)
    assertThat(doubleClickHandler.clickCount).isEqualTo(1)
    assertThat(doubleClickHandler.lastItem).isSameAs(item1)
  }

  @RunsInEdt
  @Test
  fun testExpandFirstRowOnHover() {
    val application = ApplicationManager.getApplication()
    application.replaceService(WindowManager::class.java, mock<WindowManagerEx>(), disposableRule.disposable)

    val tree = createTree()
    val scrollPane = getScrollPane(tree)
    setPopupView(tree)
    tree.overrideHasApplicationFocus = { true }

    setScrollPaneSize(tree, 30, 700)
    val ui = FakeUi(scrollPane, createFakeWindow = true)

    mockStatic(ScreenUtil::class.java).use { utilities ->
      utilities.whenever<Rectangle> {
        ScreenUtil.getScreenRectangle(any(Point::class.java))
      }.thenReturn(Rectangle(0, 0, 1000, 1000))

      ui.mouse.moveTo(10, 10)
      getAlarm(tree).drainRequestsInTest()
    }
    assertThat(tree.expandableTreeItemsHandler.expandedItems).containsExactly(0)
    assertThat(tree.expandableTreeItemsHandler.isShowing).isTrue()
  }

  @RunsInEdt
  @Test
  fun testHoverOnEmptyPartOfTree() {
    val tree = createTree()
    setScrollPaneSize(tree, 90, 700)
    tree.overrideHasApplicationFocus = { true }
    val ui = FakeUi(tree, createFakeWindow = true)
    ui.mouse.moveTo(10, 690)
    getAlarm(tree).drainRequestsInTest()
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
    val scrollPane = getScrollPane(tree)
    scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
    scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER

    // This disables the "Show scroll bars when scrolling" option on Mac (for this test).
    scrollPane.verticalScrollBar.isOpaque = true

    scrollPane.setBounds(0, 0,
                         width + scrollPane.verticalScrollBar.preferredSize.width,
                         height + scrollPane.horizontalScrollBar.preferredSize.height)
    scrollPane.doLayout()
    tree.parent.doLayout()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    return scrollPane
  }

  private fun getScrollPane(tree: TreeImpl): JScrollPane =
    tree.parent.parent as JScrollPane

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
      .build().component as JScrollPane
  }

  private fun getAlarm(tree: TreeImpl): Alarm =
    getField(tree.expandableTreeItemsHandler, AbstractExpandableItemsHandler::class.java, "myUpdateAlarm")

  private fun setPopupView(tree: TreeImpl) {
    val popup: MovablePopup = getField(tree.expandableTreeItemsHandler, AbstractExpandableItemsHandler::class.java, "myPopup")
    val owner: Component = getField(popup, popup.javaClass, "myOwner")
    val content: Component = getField(popup, popup.javaClass, "myContent")
    setField(tree.expandableTreeItemsHandler, AbstractExpandableItemsHandler::class.java, "myPopup", FakePopup(owner, content))
  }

  @Suppress("SameParameterValue")
  private fun <T> getField(instance: Any, klass: Class<*>, fieldName: String): T {
    val field = klass.getDeclaredField(fieldName)
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return field.get(instance) as T
  }

  @Suppress("SameParameterValue")
  private fun setField(instance: Any, klass: Class<*>, fieldName: String, newValue: Any) {
    val field = klass.getDeclaredField(fieldName)
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return field.set(instance, newValue)
  }

  private class FakePopup(owner: Component, content: Component): MovablePopup(owner, content) {
    private var popupVisible: Boolean = false

    override fun setVisible(visible: Boolean) { popupVisible = visible }
    override fun isVisible(): Boolean = popupVisible
  }
}
