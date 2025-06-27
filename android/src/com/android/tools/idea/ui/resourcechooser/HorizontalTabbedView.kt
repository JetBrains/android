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
package com.android.tools.idea.ui.resourcechooser

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.adtui.stdui.registerActionShortCutSet
import com.intellij.ide.ui.laf.darcula.ui.DarculaTabbedPaneUI
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.Insets
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.KeyStroke
import javax.swing.SwingConstants

class HorizontalTabbedPanelBuilder {

  private val componentsToBuild = mutableMapOf<String, JComponent>()
  private var defaultPage = 0
  private val actionMap = mutableMapOf<KeyStroke, Action>()

  /**
   * Used to add a tab component with the tab label name.
   * If the [component] is added before, then throws [IllegalArgumentException].
   */
  fun addTab(name: String, component: JComponent) = apply {
    if (component in componentsToBuild.values) {
      throw IllegalArgumentException("The component is added before: '$component'")
    }
    componentsToBuild[name] = component
  }

  /**
   * Set the default tab page. By default the first page is chose.
   */
  fun setDefaultPage(page: Int) = apply {
    defaultPage = page
  }

  fun addKeyAction(keyStroke: KeyStroke, action: Action) = apply { actionMap[keyStroke] = action }

  fun build(): JComponent {
    if (componentsToBuild.isEmpty()) {
      return JPanel()
    }

    if (defaultPage < 0 || defaultPage >= componentsToBuild.size) {
      Logger.getInstance(this::class.java).debug("The default page does not exist, set first page as default page instead.")
      defaultPage = 0
    }

    val tabbedPane = MyTabbedPane()
    tabbedPane.addChangeListener { tabbedPane.getComponentAt(tabbedPane.selectedIndex).requestFocusInWindow() }
    registerNavigationKeys(tabbedPane)
    componentsToBuild.entries.forEachIndexed { index, entry -> tabbedPane.addPage(index, entry.key, entry.value) }

    tabbedPane.selectedIndex = defaultPage
    tabbedPane.isFocusable = false
    // Make the tab size match the ui.
    tabbedPane.updateUI()
    tabbedPane.requestFocusInWindow()

    actionMap.forEach { (keyStroke, action) ->
      val key = keyStroke.toString()
      tabbedPane.actionMap.put(key, action)
      tabbedPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(keyStroke, key)
    }

    return tabbedPane
  }

  companion object {
    /**
     * Support using Home, End, PageUp, and PageDown to navigate between different tabs.
     */
    private fun registerNavigationKeys(tabbedPane: JTabbedPane) {
      tabbedPane.run {
        registerActionShortCutSet({ selectedIndex = (tabCount + 1) % tabCount },
                                  ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_TAB).shortcutSet)
        registerActionShortCutSet({ selectedIndex = (tabCount + selectedIndex - 1) % tabCount },
                                  ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_TAB).shortcutSet)
      }
    }
  }
}

@SwingCoordinate
private const val TAB_TEXT_PADDING_PX = 5

private class MyTabbedPane : JBTabbedPane() {

  init {
    tabLayoutPolicy = JTabbedPane.WRAP_TAB_LAYOUT
    tabComponentInsets = Insets(0, 0, 0, 0)
  }

  override fun requestFocusInWindow() = getComponentAt(selectedIndex)?.requestFocusInWindow() ?: false

  override fun updateUI() {
    val g = graphics
    setUI(TabbedUI(getTabWidth(), JBUI.scale(TAB_TEXT_PADDING_PX * 2)))
  }

  override fun getPreferredSize(): Dimension {
    if (tabCount == 0) {
      return Dimension()
    }

    val width = components.map { it.preferredSize.width }.maxOrNull()!!
    val height = components.map { it.preferredSize.height }.maxOrNull()!!
    return Dimension(width, height + getTabComponentAt(0).height)
  }

  private fun getTabWidth(): Int {
    val children = components
    if (children.isEmpty()) {
      return 0
    }
    return children.map { it.preferredSize.width }.maxOrNull()!! / tabCount
  }

  fun addPage(index: Int, title: String, component: JComponent) {
    val tab = JBLabel(title)
    tab.horizontalAlignment = SwingConstants.CENTER
    tab.border = JBUI.Borders.empty()
    tab.isFocusable = false
    add(title, component)
    setTabComponentAt(index, tab)
  }
}

private class TabbedUI(private val width: Int, private val textVerticalPadding: Int): DarculaTabbedPaneUI() {
  override fun calculateTabWidth(tabPlacement: Int, tabIndex: Int, metrics: FontMetrics?) = width

  override fun calculateTabHeight(tabPlacement: Int, tabIndex: Int, fontHeight: Int) = textVerticalPadding * 2 + fontHeight
}
