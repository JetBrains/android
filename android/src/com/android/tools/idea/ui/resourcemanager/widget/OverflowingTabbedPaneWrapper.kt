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
package com.android.tools.idea.ui.resourcemanager.widget

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.DarculaTabbedPaneUI
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.util.ui.JBUI
import java.awt.AWTEvent
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JTabbedPane

/**
 * A wrapper component over a [JTabbedPane] which displays tabs in
 * a single row, ensures that the selected tab is visible and
 * shows any hidden tab in a popup menu displayed when clicking
 * an overflow button on the right.
 *
 * Use this component in place of a [JTabbedPane] and use [tabbedPane]
 * to access to the underlying [JTabbedPane] and call its method
 * (e.g [JTabbedPane.addTab]...)
 *
 * See [tabbedPane] for restriction applying to the underlying JTabbedPane.
 *
 * ```
 * ....--------------------------
 * .TAB A   TAB B   TAB C   [:] |<-- Overflow Button
 * ....------------======--------
 *                      | TAB A |  (Popup)
 *                      | TAB D |
 *                      ---------
 * ```
 */
class OverflowingTabbedPaneWrapper : JComponent() {

  private val overflowingTabbedPaneUI = OverflowingTabbedPaneUI()
  private val overflowButton = overflowingTabbedPaneUI.overflowButton

  /**
   * The underlying tabbedPane.
   *
   * It can be used like a normal [JTabbedPane] with few restrictions:
   * - The layout policy is not respected because any tab that overflows the pane
   *   will be displayed in the overflow menu
   * - Only [JTabbedPane.TOP] tab placement is supported
   */
  val tabbedPane = object : JTabbedPane() {
    override fun updateUI() {
      setUI(overflowingTabbedPaneUI)
    }
  }

  init {
    background = tabbedPane.background
    tabbedPane.isOpaque = false
    isOpaque = true
    add(overflowButton)
    add(tabbedPane)
    enableEvents(AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK)
  }

  /**
   * Lays out the [tabbedPane] and the [overflowButton] to the right.
   * Any other component is ignored.
   */
  override fun doLayout() {
    val preferredSize = tabbedPane.preferredSize
    tabbedPane.setBounds(0, 0, width, preferredSize.height)
    if (overflowButton.isVisible) {
      overflowButton.doLayout()
      val tabPaneInsets = tabbedPane.insets
      val overflowButtonInset = overflowButton.insets
      val overflowButtonSize = overflowButton.preferredSize
      overflowButton.setBounds(width - overflowButtonSize.width - tabPaneInsets.right - overflowButtonInset.right,
                               tabPaneInsets.top + (height - overflowButtonSize.height) / 2,
                               overflowButtonSize.width,
                               overflowButtonSize.height)
    }
  }

  override fun getPreferredSize(): Dimension = tabbedPane.preferredSize

  override fun updateUI() {
    super.updateUI()
    background = tabbedPane.background
  }

  override fun paintComponent(g: Graphics?) {
    super.paintComponent(g)
    if (g == null) return
    g.color = background
    g.fillRect(0, 0, width, height)
  }
}

private class OverflowingTabbedPaneUI : DarculaTabbedPaneUI() {

  private var overflowPopup: JBPopupMenu? = null
  private val resizeListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      overflowPopup?.isVisible = false
    }
  }
  private val hiddenTab = mutableListOf<Int>()

  private val overFlowPopupAction = createOverflowPopupAction()

  val overflowButton = ActionButton(overFlowPopupAction,
                                    PresentationFactory().getPresentation(overFlowPopupAction),
                                    "",
                                    JBUI.size(20)).apply {
    border = JBUI.Borders.emptyRight(2)
  }

  private fun createOverflowPopupAction() = object : DumbAwareAction("Show Hidden Tabs", "Show hidden tab",
                                                                     AllIcons.Actions.More) {

    override fun actionPerformed(e: AnActionEvent) = createAndShowOverflowMenu()

    private fun createAndShowOverflowMenu() {
      val menu = JBPopupMenu()
      hiddenTab.forEach { tabIndex ->
        menu.add(tabPane.getTitleAt(tabIndex)).addActionListener { tabPane.selectedIndex = tabIndex }
      }

      // Display the popup below the overflow button with their right sides aligned
      val x = tabPane.x + tabPane.width - menu.preferredSize.width
      val y = tabPane.height - tabPane.insets.bottom - tabPane.insets.top
      menu.show(tabPane, x, y)
      overflowPopup = menu
    }
  }

  override fun installListeners() {
    super.installListeners()
    tabPane.addComponentListener(resizeListener)
  }

  override fun uninstallListeners() {
    super.uninstallListeners()
    tabPane.removeComponentListener(resizeListener)
  }

  override fun createLayoutManager(): TabbedPaneLayout = OverflowingTabPaneLayout()

  /**
   * A modified copy of the base [javax.swing.plaf.basic.BasicTabbedPaneUI.TabbedPaneLayout]
   * which always displays tabs in a single row no matter what
   * the [javax.swing.JTabbedPane.tabLayoutPolicy] is.
   */
  private inner class OverflowingTabPaneLayout : TabbedPaneLayout() {

    override fun preferredTabAreaHeight(tabPlacement: Int, width: Int): Int {
      val metrics = fontMetrics
      val tabCount = tabPane.tabCount
      var total = 0
      if (tabCount > 0) {
        val rows = 1
        var x = 0

        val maxTabHeight = calculateMaxTabHeight(tabPlacement)

        for (i in 0 until tabCount) {
          val tabWidth = calculateTabWidth(tabPlacement, i, metrics)
          x += tabWidth
        }
        total = calculateTabAreaHeight(tabPlacement, rows, maxTabHeight)
      }
      return total
    }

    /**
     * Modified version of the base method that always displays the tabs in a single
     * horizontal row and put any overflowing tab's index in the [hiddenTab] list.
     *
     * The tabs are first laid out from 0 to [tabCount] - 1, then they are all offset
     * to the left to ensure that the selected tab is visible. Finally any tab that
     * is not entirely visible is added to the overflow menu, respecting their original
     * order.
     */
    override fun calculateTabRects(tabPlacement: Int, tabCount: Int) {
      val metrics = fontMetrics
      val insets = tabPane.insets
      val tabAreaInsets = getTabAreaInsets(tabPlacement)
      val selectedIndex = tabPane.selectedIndex
      var x = insets.left + tabAreaInsets.left
      val y = insets.top + tabAreaInsets.top
      var i = 0

      maxTabHeight = calculateMaxTabHeight(tabPlacement)

      runCount = 0
      selectedRun = -1

      if (tabCount == 0) {
        return
      }

      // Run through tabs and partition them into runs
      var rect: Rectangle
      while (i < tabCount) {
        rect = rects[i]

        if (i > 0) {
          rect.x = rects[i - 1].x + rects[i - 1].width
        }
        else {
          tabRuns[0] = 0
          runCount = 1
          maxTabWidth = 0
          rect.x = x
        }
        rect.width = calculateTabWidth(tabPlacement, i, metrics)
        maxTabWidth = Math.max(maxTabWidth, rect.width)

        x = rect.x + rect.width

        // Initialize y position in case there's just one run
        rect.y = y
        rect.height = maxTabHeight

        if (i == selectedIndex) {
          selectedRun = runCount - 1
        }
        i++
      }

      hideOverflowingTabs(tabCount, selectedIndex)

      // Pad the selected tab so that it appears raised in front
      padSelectedTab(tabPlacement, selectedIndex)
    }
  }

  /**
   * Ensures that [selectedIndex] is visible by offsetting all the tabs
   * to the left until the selected tab right bound is within the visible
   * area of [tabPane].
   */
  private fun hideOverflowingTabs(tabCount: Int, selectedIndex: Int) {
    hiddenTab.clear()
    val lastTabRect = rects[tabCount - 1]

    // If the the tabs area is greater than the the tabPane width
    if (lastTabRect.x + lastTabRect.width > tabPane.width) {
      val selectedRect = rects[selectedIndex]
      val availableWidth = tabPane.width - overflowButton.preferredSize.width
      val selectedTabRight = selectedRect.x + selectedRect.width

      // Offset the selected tab to the right of the overflowButton
      val offset = if (selectedTabRight > availableWidth) selectedTabRight - overflowButton.x else 0

      for (k in 0 until tabCount) {
        // Apply the offset to all the tabs
        val tabRect = rects[k]
        tabRect.translate(-offset, 0)
        val tabRight = tabRect.x + tabRect.width

        // If the tab is partially or entirely hidden, add is to the list
        if (tabRect.x < 0 || tabRight > availableWidth) {
          hiddenTab.add(k)

          // If the tab is entirely hidden to the left
          // or partially hidden to the right, hide it
          // all together so it does not appear underneath
          // the overflow button
          if (tabRight < 0 || tabRight > availableWidth) {
            tabRect.x = 0
            tabRect.width = 0
          }
        }
      }
    }
    overflowButton.isVisible = hiddenTab.isNotEmpty()
  }
}