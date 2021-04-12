/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.componenttree.impl

import com.android.SdkConstants.FQCN_TEXT_VIEW
import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.componenttree.api.ContextPopupHandler
import com.android.tools.componenttree.api.DoubleClickHandler
import com.android.tools.componenttree.impl.ViewTreeCellRenderer.ColoredViewRenderer
import com.android.tools.componenttree.util.Item
import com.android.tools.componenttree.util.ItemNodeType
import com.android.tools.property.testing.ApplicationRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.AbstractExpandableItemsHandler
import com.intellij.ui.ExpandableItemsHandlerFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.UIUtil
import icons.StudioIcons.LayoutEditor.Palette
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import java.awt.Color
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.SwingUtilities

private const val TEST_ROW = 1

class ViewTreeCellRendererTest {

  @JvmField
  @Rule
  val appRule = ApplicationRule()

  private val type = ItemNodeType()
  private val contextPopupHandler: ContextPopupHandler = { _, _, _ -> }
  private val doubleClickHandler: DoubleClickHandler = { }
  private val renderer = ViewTreeCellRenderer(type)

  private val model = ComponentTreeModelImpl(mapOf(Pair(Item::class.java, type)), SwingUtilities::invokeLater)

  private val normal = SimpleTextAttributes.REGULAR_ATTRIBUTES
  private val bold = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
  private val grey = SimpleTextAttributes.GRAYED_ATTRIBUTES

  private var focusManager: KeyboardFocusManager? = mock(KeyboardFocusManager::class.java)
  private var focusOwner: Any? = null
  private var tree: TreeImpl? = null

  @Before
  fun setUp() {
    appRule.testApplication.registerService(
      ExpandableItemsHandlerFactory::class.java, TestExpandableItemsHandlerFactory(), appRule.testRootDisposable)
    doAnswer { focusOwner }.`when`<KeyboardFocusManager>(focusManager!!).focusOwner
    KeyboardFocusManager.setCurrentKeyboardFocusManager(focusManager)
    tree = TreeImpl(model, contextPopupHandler, doubleClickHandler, emptyList(), "testComponentTree")
  }

  @After
  fun tearDown() {
    KeyboardFocusManager.setCurrentKeyboardFocusManager(null)
    focusManager = null
    tree = null
  }

  @Test
  fun testFragmentsWithIdAndTextValue() {
    val item = Item(FQCN_TEXT_VIEW, "@+id/text", "Hello", Palette.TEXT_VIEW)
    val component = renderAndCheckFragments(item, Fragment("text", bold), Fragment(" - \"Hello\"", grey))
    assertThat(component.icon).isEqualTo(Palette.TEXT_VIEW)
    assertThat(component.toolTipText).isEqualTo("""
      <html>
        TextView<br/>
        text: "Hello"
      </html>""".trimIndent())
    assertThat(ViewTreeCellRenderer.computeSearchString(type, item)).isEqualTo("text - \"Hello\"")
  }

  @Test
  fun testFragmentsWithoutIdAndTextValue() {
    val item = Item(FQCN_TEXT_VIEW, null, null, Palette.TEXT_VIEW)
    val component = renderAndCheckFragments(item, Fragment("TextView", normal))
    assertThat(component.icon).isEqualTo(Palette.TEXT_VIEW)
    assertThat(component.toolTipText).isEqualTo("TextView")
    assertThat(ViewTreeCellRenderer.computeSearchString(type, item)).isEqualTo("TextView")
  }

  @Test
  fun testFragmentsWithIdButNoTextValue() {
    val item = Item(FQCN_TEXT_VIEW, "@id/textView", null, Palette.TEXT_VIEW)
    val component = renderAndCheckFragments(item, Fragment("textView", bold), Fragment(" - TextView", normal))
    assertThat(component.icon).isEqualTo(Palette.TEXT_VIEW)
    assertThat(component.toolTipText).isEqualTo("""
      <html>
        TextView<br/>
        textView
      </html>""".trimIndent())
    assertThat(ViewTreeCellRenderer.computeSearchString(type, item)).isEqualTo("textView - TextView")
  }

  @Test
  fun testFragmentsWithTextValueButNoId() {
    val item = Item(FQCN_TEXT_VIEW, null, "Hello World", Palette.TEXT_VIEW)
    val component = renderAndCheckFragments(item, Fragment("TextView", normal), Fragment(" - \"Hello World\"", grey))
    assertThat(component.icon).isEqualTo(Palette.TEXT_VIEW)
    assertThat(component.toolTipText).isEqualTo("""
      <html>
        TextView<br/>
        Hello World
      </html>""".trimIndent())
    assertThat(ViewTreeCellRenderer.computeSearchString(type, item)).isEqualTo("TextView - \"Hello World\"")
  }

  @Test
  fun testFragmentsWithLessThanOptimalSpace() {
    val item = Item(FQCN_TEXT_VIEW, "@+id/text", "Hello", Palette.TEXT_VIEW)
    val component = renderAndCheckFragments(item, Fragment("text", bold), Fragment(" - \"Hello\"", grey))
    val size = component.preferredSize
    component.setBounds(0, 0, size.width - 3, size.height)
    component.adjustForPainting()
    val fragments = getFragments(component)
    checkFragment(fragments[0], Fragment("text", bold))
    assertThat(fragments[1].text).endsWith("...")
  }

  @Test
  fun testFragmentsWithLessThanOptimalSpaceAndRowExpanded() {
    (tree?.expandableItemsHandler as TestTreeExpansionHandler).expandedRow = TEST_ROW
    tree?.overrideHasApplicationFocus = { true }
    val item = Item(FQCN_TEXT_VIEW, "@+id/text", "Hello", Palette.TEXT_VIEW)
    val component = renderAndCheckFragments(item, Fragment("text", bold), Fragment(" - \"Hello\"", grey))
    val size = component.preferredSize
    component.setBounds(0, 0, size.width - 3, size.height)
    component.adjustForPainting()
    checkFragments(component, Fragment("text", bold), Fragment(" - \"Hello\"", grey))
  }

  @Test
  fun testBackgroundColorWhenTreeHasFocus() {
    focusOwner = tree
    val item = Item(FQCN_TEXT_VIEW, "@+id/text", "Hello", Palette.TEXT_VIEW)
    assertThat(getBackgroundColor(item, selected = false, hasFocus = false)).isEqualTo(UIUtil.getTreeBackground())
    assertThat(getBackgroundColor(item, selected = false, hasFocus = true)).isEqualTo(UIUtil.getTreeBackground())
    assertThat(getBackgroundColor(item, selected = true, hasFocus = false)).isEqualTo(UIUtil.getTreeBackground(true, true))
    assertThat(getBackgroundColor(item, selected = true, hasFocus = true)).isEqualTo(UIUtil.getTreeBackground(true, true))
  }

  @Test
  fun testBackgroundColorWhenTreeDoesNotHaveFocus() {
    val item = Item(FQCN_TEXT_VIEW, "@+id/text", "Hello", Palette.TEXT_VIEW)
    assertThat(getBackgroundColor(item, selected = false, hasFocus = false)).isEqualTo(UIUtil.getTreeBackground())
    assertThat(getBackgroundColor(item, selected = false, hasFocus = true)).isEqualTo(UIUtil.getTreeBackground())
    assertThat(getBackgroundColor(item, selected = true, hasFocus = false)).isEqualTo(UIUtil.getTreeBackground(true, false))
    assertThat(getBackgroundColor(item, selected = true, hasFocus = true)).isEqualTo(UIUtil.getTreeBackground(true, false))
  }

  @Test
  fun testForegroundColorWhenTreeHasFocus() {
    focusOwner = tree
    val item = Item(FQCN_TEXT_VIEW, "@+id/text", "Hello", Palette.TEXT_VIEW)
    assertThat(getForegroundColor(item, selected = false, hasFocus = false)).isEqualTo(UIUtil.getTreeForeground())
    assertThat(getForegroundColor(item, selected = false, hasFocus = true)).isEqualTo(UIUtil.getTreeForeground())
    assertThat(getForegroundColor(item, selected = true, hasFocus = false)).isEqualTo(UIUtil.getTreeForeground(true, true))
    assertThat(getForegroundColor(item, selected = true, hasFocus = true)).isEqualTo(UIUtil.getTreeForeground(true, true))
  }

  @Test
  fun testForegroundColorWhenTreeDoesNotHaveFocus() {
    val item = Item(FQCN_TEXT_VIEW, "@+id/text", "Hello", Palette.TEXT_VIEW)
    assertThat(getForegroundColor(item, selected = false, hasFocus = false)).isEqualTo(UIUtil.getTreeForeground())
    assertThat(getForegroundColor(item, selected = false, hasFocus = true)).isEqualTo(UIUtil.getTreeForeground())
    assertThat(getForegroundColor(item, selected = true, hasFocus = false)).isEqualTo(UIUtil.getTreeForeground(true, false))
    assertThat(getForegroundColor(item, selected = true, hasFocus = true)).isEqualTo(UIUtil.getTreeForeground(true, false))
  }

  @Test
  fun testIconWhenTreeHasFocus() {
    IconLoader.activate()
    focusOwner = tree
    val item = Item(FQCN_TEXT_VIEW, "@+id/text", "Hello", Palette.TEXT_VIEW)
    assertThat(getIcon(item, selected = false, hasFocus = false)).isEqualTo(Palette.TEXT_VIEW)
    assertThat(getIcon(item, selected = false, hasFocus = true)).isEqualTo(Palette.TEXT_VIEW)
    assertIconsEqual(getIcon(item, selected = true, hasFocus = false)!!, ColoredIconGenerator.generateWhiteIcon(Palette.TEXT_VIEW))
    assertIconsEqual(getIcon(item, selected = true, hasFocus = true)!!, ColoredIconGenerator.generateWhiteIcon(Palette.TEXT_VIEW))
    assertThat(hasNonWhiteColors(getIcon(item, selected = true, hasFocus = false)!!)).isFalse()
    assertThat(hasNonWhiteColors(getIcon(item, selected = true, hasFocus = true)!!)).isFalse()
  }

  @Suppress("UndesirableClassUsage")
  private fun assertIconsEqual(actual: Icon, expected: Icon) {
    val expectedImage = BufferedImage(expected.iconWidth, expected.iconHeight, BufferedImage.TYPE_INT_ARGB)
    expected.paintIcon(null, expectedImage.createGraphics(), 0, 0)
    val actualImage = BufferedImage(actual.iconWidth, actual.iconHeight, BufferedImage.TYPE_INT_ARGB)
    actual.paintIcon(null, actualImage.createGraphics(), 0, 0)
    ImageDiffUtil.assertImageSimilar("icon", expectedImage, actualImage, 0.0)
  }

  @Test
  fun testIconWhenTreeDoesNotHaveFocus() {
    IconLoader.activate()
    val item = Item(FQCN_TEXT_VIEW, "@+id/text", "Hello", Palette.TEXT_VIEW)
    assertThat(getIcon(item, selected = false, hasFocus = false)).isEqualTo(Palette.TEXT_VIEW)
    assertThat(getIcon(item, selected = false, hasFocus = true)).isEqualTo(Palette.TEXT_VIEW)
    assertThat(getIcon(item, selected = true, hasFocus = false)).isEqualTo(Palette.TEXT_VIEW)
    assertThat(getIcon(item, selected = true, hasFocus = true)).isEqualTo(Palette.TEXT_VIEW)
  }

  private fun getBackgroundColor(item: Item, selected: Boolean, hasFocus: Boolean): Color {
    val component = renderer.getTreeCellRendererComponent(tree!!, item, selected, false, true, TEST_ROW, hasFocus) as ColoredViewRenderer
    component.adjustForPainting()
    return component.background
  }

  private fun getForegroundColor(item: Item, selected: Boolean, hasFocus: Boolean): Color {
    val component = renderer.getTreeCellRendererComponent(tree!!, item, selected, false, true, TEST_ROW, hasFocus) as ColoredViewRenderer
    component.adjustForPainting()
    return component.foreground
  }

  private fun getIcon(item: Item, selected: Boolean, hasFocus: Boolean): Icon? {
    val component = renderer.getTreeCellRendererComponent(tree!!, item, selected, false, true, TEST_ROW, hasFocus) as ColoredViewRenderer
    component.adjustForPainting()
    return component.icon
  }

  private fun hasNonWhiteColors(icon: Icon): Boolean {
    var combined = 0xffffff
    val image = toImage(icon) ?: return false
    for (x in 0 until image.width) {
      for (y in 0 until image.height) {
        val rgb = Color(image.getRGB(x, y), true)
        if (rgb.alpha != 0) {
          combined = combined.and(rgb.rgb)
        }
      }
    }
    return combined.and(0xffffff) != 0xffffff
  }

  private fun toImage(icon: Icon): BufferedImage? {
    @Suppress("UndesirableClassUsage")
    val image = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    icon.paintIcon(null, g, 0, 0)
    return image
  }

  private fun renderAndCheckFragments(item: Item, vararg fragments: Fragment): ColoredViewRenderer {
    val component = renderer.getTreeCellRendererComponent(tree!!, item, false, false, true, TEST_ROW, false) as ColoredViewRenderer
    checkFragments(component, *fragments)
    return component
  }

  private fun checkFragments(component: ColoredViewRenderer, vararg expectedFragments: Fragment) {
    val fragments = getFragments(component)
    for (index in expectedFragments.indices) {
      if (index >= fragments.size) {
        break
      }
      checkFragment(fragments[index], expectedFragments[index], "Difference at fragment index: $index")
    }
    assertThat(fragments.size).named("Fragment count error").isEqualTo(expectedFragments.size)
  }

  private fun checkFragment(fragment: Fragment, expected: Fragment, name: String = "Fragment") {
    assertThat(fragment.text).named(name).isEqualTo(expected.text)
    assertThat(fragment.attr.fgColor).named("$name with: Font Color").isEqualTo(expected.attr.fgColor)
    assertThat(fragment.attr.fontStyle).named("$name with: Font Style").isEqualTo(expected.attr.fontStyle)
    assertThat(fragment.attr.style).named("$name with: Style").isEqualTo(expected.attr.style)
  }

  private fun getFragments(component: ColoredViewRenderer): List<Fragment> {
    val fragments = mutableListOf<Fragment>()
    val iterator = component.iterator()
    for (part in iterator) {
      fragments.add(Fragment(part, iterator.textAttributes))
    }
    return fragments
  }

  private class Fragment(
    val text: String,
    val attr: SimpleTextAttributes
  )

  private class TestTreeExpansionHandler(tree: JTree) : AbstractExpandableItemsHandler<Int, JTree>(tree) {
    var expandedRow = -1

    override fun getCellRendererAndBounds(key: Int?): com.intellij.openapi.util.Pair<Component, Rectangle>? {
      return null
    }

    override fun getCellKeyForPoint(point: Point?): Int {
      return -1
    }

    override fun getExpandedItems(): Collection<Int> {
      return listOf(expandedRow)
    }
  }

  private class TestExpandableItemsHandlerFactory : ExpandableItemsHandlerFactory() {
    override fun doInstall(component: JComponent) = when (component) {
      is JTree -> TestTreeExpansionHandler(component)
      else -> null
    }
  }
}
