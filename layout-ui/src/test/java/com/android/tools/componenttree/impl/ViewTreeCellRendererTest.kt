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
import com.android.testutils.ImageDiffUtil
import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.adtui.common.ColoredIconGenerator.deEmphasize
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.componenttree.api.ContextPopupHandler
import com.android.tools.componenttree.api.DoubleClickHandler
import com.android.tools.componenttree.common.ViewTreeCellRenderer
import com.android.tools.componenttree.common.ViewTreeCellRenderer.ColoredViewRenderer
import com.android.tools.componenttree.util.Item
import com.android.tools.componenttree.util.ItemNodeType
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.ApplicationRule
import com.intellij.ui.AbstractExpandableItemsHandler
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.UIUtil
import icons.StudioIcons.LayoutEditor.Palette
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.Color
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.TreeSelectionModel

private const val TEST_ROW = 1

class ViewTreeCellRendererTest {

  @get:Rule
  val rules = RuleChain.outerRule(ApplicationRule()).around(IconLoaderRule())!!

  private val type = ItemNodeType()
  private val contextPopupHandler: ContextPopupHandler = { _, _, _, _ -> }
  private val doubleClickHandler: DoubleClickHandler = { }
  private val renderer = ViewTreeCellRenderer(type)

  private val model = ComponentTreeModelImpl(mapOf(Pair(Item::class.java, type)), SwingUtilities::invokeLater)
  private val selectionModel = ComponentTreeSelectionModelImpl(model, TreeSelectionModel.SINGLE_TREE_SELECTION)
  private var tree: TreeImpl? = null

  private val normal = SimpleTextAttributes.REGULAR_ATTRIBUTES
  private val strikeout = SimpleTextAttributes.REGULAR_ATTRIBUTES.derive(SimpleTextAttributes.STYLE_STRIKEOUT, null, null, null)
  private val grey = SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES
  private val greyStrikeout = SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER or SimpleTextAttributes.STYLE_STRIKEOUT, null)

  @Before
  fun setUp() {
    tree = TreeImpl(model, contextPopupHandler, doubleClickHandler, emptyList(), "testComponentTree", null, installKeyboardActions = {},
                    selectionModel, autoScroll = false, installTreeSearch = false)
    tree!!.expandableTreeItemsHandler = TestTreeExpansionHandler(tree!!)
  }

  @After
  fun tearDown() {
    tree = null
  }

  @Test
  fun testFragmentsWithIdAndTextValue() {
    val item = Item(FQCN_TEXT_VIEW, "@+id/text", "\"Hello\"", Palette.TEXT_VIEW)
    val component = renderAndCheckFragments(item, Fragment("text", normal), Fragment(" \"Hello\"", grey))
    assertThat(component.icon).isEqualTo(Palette.TEXT_VIEW)
    assertThat(component.toolTipText).isEqualTo("""
      <html>
        TextView<br/>
        text: "Hello"
      </html>""".trimIndent())
    assertThat(ViewTreeCellRenderer.computeSearchString(type, item)).isEqualTo("text \"Hello\"")
  }

  @Test
  fun testFragmentsWithoutIdAndTextValue() {
    val item = Item(FQCN_TEXT_VIEW, null, null, Palette.TEXT_VIEW)
    val component = renderAndCheckFragments(item, Fragment("TextView", normal))
    assertThat(component.icon).isEqualTo(Palette.TEXT_VIEW)
    assertThat(component.toolTipText).isNull()
    assertThat(ViewTreeCellRenderer.computeSearchString(type, item)).isEqualTo("TextView")
  }

  @Test
  fun testFragmentsWithIdButNoTextValue() {
    val item = Item(FQCN_TEXT_VIEW, "@id/textView", null, Palette.TEXT_VIEW)
    val component = renderAndCheckFragments(item, Fragment("textView", normal))
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
    val item = Item(FQCN_TEXT_VIEW, null, "\"Hello World\"", Palette.TEXT_VIEW)
    val component = renderAndCheckFragments(item, Fragment("TextView", normal), Fragment(" \"Hello World\"", grey))
    assertThat(component.icon).isEqualTo(Palette.TEXT_VIEW)
    assertThat(component.toolTipText).isNull()
    assertThat(ViewTreeCellRenderer.computeSearchString(type, item)).isEqualTo("TextView \"Hello World\"")
  }

  @Test
  fun testFragmentsWithLessThanOptimalSpace() {
    val item = Item(FQCN_TEXT_VIEW, "@+id/text", "\"Hello\"", Palette.TEXT_VIEW)
    val component = renderAndCheckFragments(item, Fragment("text", normal), Fragment(" \"Hello\"", grey))
    val size = component.preferredSize
    size.width -= 3
    tree!!.size = size
    component.adjustForPainting()
    val fragments = getFragments(component)
    checkFragment(fragments[0], Fragment("text", normal))
    assertThat(fragments[1].text).endsWith("...")
  }

  @Test
  fun testFragmentsWithLessThanOptimalSpaceAndRowExpanded() {
    (tree?.expandableItemsHandler as TestTreeExpansionHandler).expandedRow = TEST_ROW
    tree?.overrideHasApplicationFocus = { true }
    val item = Item(FQCN_TEXT_VIEW, "@+id/text", "\"Hello\"", Palette.TEXT_VIEW)
    val component = renderAndCheckFragments(item, Fragment("text", normal), Fragment(" \"Hello\"", grey))
    val size = component.preferredSize
    size.width -= 3
    tree!!.size = size
    component.adjustForPainting()
    checkFragments(component, Fragment("text", normal), Fragment(" \"Hello\"", grey))
  }

  @Test
  fun testBackgroundColor() {
    val item = Item(FQCN_TEXT_VIEW, "@+id/text", "Hello", Palette.TEXT_VIEW)
    // Check that background does not depend on enabled and deEmphasized:
    for ((enabled, deEmphasized) in listOf(Pair(false, false), Pair(true, false), Pair(false, true), Pair(true, true))) {
      assertThat(getBackgroundColor(item, selected = false, hasFocus = false, enabled, deEmphasized)).isEqualTo(UIUtil.getTreeBackground())
      assertThat(getBackgroundColor(item, selected = false, hasFocus = true, enabled, deEmphasized)).isEqualTo(UIUtil.getTreeBackground())
      assertThat(getBackgroundColor(item, selected = true, hasFocus = false, enabled, deEmphasized)).isEqualTo(
        UIUtil.getTreeBackground(true, false))
      assertThat(getBackgroundColor(item, selected = true, hasFocus = true, enabled, deEmphasized)).isEqualTo(
        UIUtil.getTreeBackground(true, true))
    }
  }

  @Test
  fun testForegroundColor() {
    val normal = UIUtil.getTreeForeground()
    val faint = normal.deEmphasize()
    val selected = UIUtil.getTreeForeground(true, false)
    val focused = UIUtil.getTreeForeground(true, true)
    val item = Item(FQCN_TEXT_VIEW, "@+id/text", "Hello", Palette.TEXT_VIEW)
    assertThat(getForegroundColor(item, selected = false, hasFocus = false, enabled = false, deEmphasized = false)).isEqualTo(faint)
    assertThat(getForegroundColor(item, selected = false, hasFocus = true, enabled = false, deEmphasized = false)).isEqualTo(faint)
    assertThat(getForegroundColor(item, selected = true, hasFocus = false, enabled = false, deEmphasized = false)).isEqualTo(selected)
    assertThat(getForegroundColor(item, selected = true, hasFocus = true, enabled = false, deEmphasized = false)).isEqualTo(focused)
    assertThat(getForegroundColor(item, selected = false, hasFocus = false, enabled = true, deEmphasized = false)).isEqualTo(normal)
    assertThat(getForegroundColor(item, selected = false, hasFocus = true, enabled = true, deEmphasized = false)).isEqualTo(normal)
    assertThat(getForegroundColor(item, selected = true, hasFocus = false, enabled = true, deEmphasized = false)).isEqualTo(selected)
    assertThat(getForegroundColor(item, selected = true, hasFocus = true, enabled = true, deEmphasized = false)).isEqualTo(focused)
    assertThat(getForegroundColor(item, selected = false, hasFocus = false, enabled = false, deEmphasized = true)).isEqualTo(faint)
    assertThat(getForegroundColor(item, selected = false, hasFocus = true, enabled = false, deEmphasized = true)).isEqualTo(faint)
    assertThat(getForegroundColor(item, selected = true, hasFocus = false, enabled = false, deEmphasized = true)).isEqualTo(selected)
    assertThat(getForegroundColor(item, selected = true, hasFocus = true, enabled = false, deEmphasized = true)).isEqualTo(focused)
    assertThat(getForegroundColor(item, selected = false, hasFocus = false, enabled = true, deEmphasized = true)).isEqualTo(faint)
    assertThat(getForegroundColor(item, selected = false, hasFocus = true, enabled = true, deEmphasized = true)).isEqualTo(faint)
    assertThat(getForegroundColor(item, selected = true, hasFocus = false, enabled = true, deEmphasized = true)).isEqualTo(selected)
    assertThat(getForegroundColor(item, selected = true, hasFocus = true, enabled = true, deEmphasized = true)).isEqualTo(focused)
  }

  @Test
  fun testDisabled() {
    val item = Item(FQCN_TEXT_VIEW, "@+id/text", "\"Hello\"", Palette.TEXT_VIEW)
    item.enabled = false
    val component = renderAndCheckFragments(item, Fragment("text", strikeout), Fragment(" \"Hello\"", greyStrikeout))
    assertThat(component.icon).isEqualTo(Palette.TEXT_VIEW)
    assertThat(component.toolTipText).isEqualTo("""
      <html>
        TextView<br/>
        text: "Hello"
      </html>""".trimIndent())
    assertThat(ViewTreeCellRenderer.computeSearchString(type, item)).isEqualTo("text \"Hello\"")
  }

  @Test
  fun testIcon() {
    val normal = Palette.TEXT_VIEW
    val white = ColoredIconGenerator.generateWhiteIcon(Palette.TEXT_VIEW)
    val faint = ColoredIconGenerator.generateDeEmphasizedIcon(Palette.TEXT_VIEW)
    assertThat(hasNonWhiteColors(white)).isFalse()
    IconLoader.activate()
    val item = Item(FQCN_TEXT_VIEW, "@+id/text", "Hello", Palette.TEXT_VIEW)
    assertThat(getIcon(item, selected = false, hasFocus = false, enabled = true, deEmphasized = false)).isSameAs(normal)
    assertThat(getIcon(item, selected = false, hasFocus = true, enabled = true, deEmphasized = false)).isSameAs(normal)
    assertThat(getIcon(item, selected = true, hasFocus = false, enabled = true, deEmphasized = false)).isSameAs(normal)
    assertIconsEqual(getIcon(item, selected = true, hasFocus = true, enabled = true, deEmphasized = false)!!, white)
    assertIconsEqual(getIcon(item, selected = false, hasFocus = false, enabled = false, deEmphasized = false)!!, faint)
    assertIconsEqual(getIcon(item, selected = false, hasFocus = true, enabled = false, deEmphasized = false)!!, faint)
    assertThat(getIcon(item, selected = true, hasFocus = false, enabled = false, deEmphasized = false)).isSameAs(normal)
    assertIconsEqual(getIcon(item, selected = true, hasFocus = true, enabled = false, deEmphasized = false)!!, white)
    assertIconsEqual(getIcon(item, selected = false, hasFocus = false, enabled = true, deEmphasized = true)!!, faint)
    assertIconsEqual(getIcon(item, selected = false, hasFocus = true, enabled = true, deEmphasized = true)!!, faint)
    assertThat(getIcon(item, selected = true, hasFocus = false, enabled = true, deEmphasized = true)).isSameAs(normal)
    assertIconsEqual(getIcon(item, selected = true, hasFocus = true, enabled = true, deEmphasized = true)!!, white)
    assertIconsEqual(getIcon(item, selected = false, hasFocus = false, enabled = false, deEmphasized = true)!!, faint)
    assertIconsEqual(getIcon(item, selected = false, hasFocus = true, enabled = false, deEmphasized = true)!!, faint)
    assertThat(getIcon(item, selected = true, hasFocus = false, enabled = false, deEmphasized = true)).isSameAs(normal)
    assertIconsEqual(getIcon(item, selected = true, hasFocus = true, enabled = false, deEmphasized = true)!!, white)
  }

  @Suppress("UndesirableClassUsage")
  private fun assertIconsEqual(actual: Icon, expected: Icon) {
    val expectedImage = BufferedImage(expected.iconWidth, expected.iconHeight, BufferedImage.TYPE_INT_ARGB)
    expected.paintIcon(null, expectedImage.createGraphics(), 0, 0)
    val actualImage = BufferedImage(actual.iconWidth, actual.iconHeight, BufferedImage.TYPE_INT_ARGB)
    actual.paintIcon(null, actualImage.createGraphics(), 0, 0)
    ImageDiffUtil.assertImageSimilar("icon", expectedImage, actualImage, 0.0)
  }

  private fun getBackgroundColor(item: Item, selected: Boolean, hasFocus: Boolean, enabled: Boolean, deEmphasized: Boolean): Color {
    item.enabled = enabled
    item.deEmphasized = deEmphasized
    val component = renderer.getTreeCellRendererComponent(tree!!, item, selected, false, true, TEST_ROW, hasFocus) as ColoredViewRenderer
    component.adjustForPainting()
    return component.background
  }

  private fun getForegroundColor(item: Item, selected: Boolean, hasFocus: Boolean, enabled: Boolean, deEmphasized: Boolean): Color {
    item.enabled = enabled
    item.deEmphasized = deEmphasized
    val component = renderer.getTreeCellRendererComponent(tree!!, item, selected, false, true, TEST_ROW, hasFocus) as ColoredViewRenderer
    component.adjustForPainting()
    return component.foreground
  }

  private fun getIcon(item: Item, selected: Boolean, hasFocus: Boolean, enabled: Boolean, deEmphasized: Boolean): Icon? {
    item.enabled = enabled
    item.deEmphasized = deEmphasized
    val component = renderer.getTreeCellRendererComponent(tree!!, item, selected, false, true, TEST_ROW, hasFocus) as ColoredViewRenderer
    component.adjustForPainting()
    return component.icon
  }

  private fun hasNonWhiteColors(icon: Icon): Boolean {
    var combined = 0xffffff
    val image = toImage(icon)
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

  private fun toImage(icon: Icon): BufferedImage {
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
}
