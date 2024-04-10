/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.common.surface.sceneview

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.surface.DesignSurfaceScrollPane
import com.android.tools.idea.common.surface.LabelPanel
import com.android.tools.idea.common.surface.SceneViewPeerPanel
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.ScreenReader
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/** Distance between the bottom bound of model name and top bound of SceneView. */
@SwingCoordinate private const val TOP_BAR_BOTTOM_MARGIN = 3

/**
 * This panel wraps both the label and the toolbar and puts them left aligned (label) and right
 * aligned (the toolbar).
 */
class SceneViewTopPanel(
  private val toolbarTargetComponent: JComponent,
  private val statusIconAction: AnAction?,
  toolbarActions: List<AnAction>,
  private val labelPanel: LabelPanel,
) : JPanel(BorderLayout()) {

  init {
    border = JBUI.Borders.emptyBottom(TOP_BAR_BOTTOM_MARGIN)
    isOpaque = false
    // Make the status icon be part of the top panel
    val statusIcon =
      statusIconAction?.let {
        createToolbar(listOf(statusIconAction)) {
          (it as? ActionToolbarImpl)?.setForceMinimumSize(true)
          it.layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
        }
      }
    val statusIconSize = statusIcon.minimumWidth()
    if (statusIcon != null && statusIconSize > 0) {
      add(statusIcon, BorderLayout.LINE_START)
      statusIcon.isVisible = true
    }
    add(labelPanel, BorderLayout.CENTER)
    val toolbar =
      createToolbar(toolbarActions) {
          // Do not allocate space for the "see more" chevron if not needed
          it.isReservePlaceAutoPopupIcon = false
          it.setShowSeparatorTitles(true)
        }
        ?.also {
          add(it, BorderLayout.LINE_END)
          // Initialize the toolbar as invisible if ScreenReader is not active. In this case, its
          // visibility will be controlled by hovering the sceneViewTopPanel. When the screen reader
          // is active, the toolbar is always visible, so it can be focusable.
          it.isVisible = SceneViewPeerPanel.defaultToolbarVisibility || ScreenReader.isActive()
        }
    // The space of name label is sacrificed when there is no enough width to display the toolbar.
    // When it happens, the label will be trimmed and show the ellipsis at its tail.
    // User can still hover it to see the full label in the tooltips.
    val minWidth = statusIconSize + LabelPanel.MIN_WIDTH + toolbar.minimumWidth()
    // Since sceneViewToolbar visibility can change, sceneViewTopPanel (its container) might want
    // to reduce its size when sceneViewToolbar
    // gets invisible, resulting in a visual misbehavior where the toolbar moves a little when the
    // actions appear/disappear. To fix this,
    // we should set sceneViewTopPanel preferred size to always occupy the height taken by
    // sceneViewToolbar when it exists.
    val minHeight =
      maxOf(
        minimumSize.height,
        toolbar?.preferredSize?.height ?: 0,
        toolbar?.minimumSize?.height ?: 0,
      )
    minimumSize = Dimension(minWidth, minHeight)
    preferredSize = toolbar?.let { Dimension(minWidth, minHeight) }

    setUpTopPanelMouseListeners(toolbar)
  }

  /**
   * Creates and adds the [MouseAdapter]s required to show the [sceneViewToolbar] when the mouse is
   * hovering the [SceneViewTopPanel], and hide it otherwise.
   */
  private fun JPanel.setUpTopPanelMouseListeners(sceneViewToolbar: JComponent?) {
    // MouseListener to show the sceneViewToolbar when the mouse enters the target component, and to
    // hide it when the mouse exits the bounds
    // of sceneViewTopPanel.
    val hoverTopPanelMouseListener =
      object : MouseAdapter() {

        override fun mouseEntered(e: MouseEvent?) {
          // Show the toolbar actions when mouse is hovering the top panel.
          sceneViewToolbar?.let { it.isVisible = true }
        }

        override fun mouseExited(e: MouseEvent?) {
          SwingUtilities.getWindowAncestor(this@setUpTopPanelMouseListeners)?.let {
            if (!it.isFocused) {
              // Dismiss the toolbar if the current window loses focus, e.g. when alt tabbing.
              hideToolbar()
              return@mouseExited
            }
          }

          e?.locationOnScreen?.let {
            SwingUtilities.convertPointFromScreen(it, this@setUpTopPanelMouseListeners)
            // Hide the toolbar when the mouse exits the bounds of sceneViewTopPanel or the
            // containing design surface.
            if (!containsExcludingBorder(it) || !designSurfaceContains(e.locationOnScreen)) {
              hideToolbar()
            } else {
              // We've exited to one of the toolbar actions, so we need to make sure this listener
              // is algo registered on them.
              sceneViewToolbar?.let { toolbar ->
                for (i in 0 until toolbar.componentCount) {
                  toolbar
                    .getComponent(i)
                    .removeMouseListener(this) // Prevent duplicate listeners being added.
                  toolbar.getComponent(i).addMouseListener(this)
                }
              }
            }
          } ?: hideToolbar()
        }

        private fun JPanel.designSurfaceContains(p: Point): Boolean {
          var component = parent
          var designSurface: DesignSurfaceScrollPane? = null
          while (component != null) {
            if (component is DesignSurfaceScrollPane) {
              designSurface = component
              break
            }
            component = component.parent
          }
          if (designSurface == null) return false
          SwingUtilities.convertPointFromScreen(p, designSurface)
          // Consider the scrollbar width exiting from the right
          return p.x in 0 until (designSurface.width - UIUtil.getScrollBarWidth()) &&
            p.y in 0 until designSurface.height
        }

        private fun JPanel.containsExcludingBorder(p: Point): Boolean {
          val borderInsets = border.getBorderInsets(this@setUpTopPanelMouseListeners)
          return p.x in borderInsets.left until (width - borderInsets.right) &&
            p.y in borderInsets.top until (height - borderInsets.bottom)
        }

        private fun hideToolbar() {
          sceneViewToolbar?.let { toolbar ->
            // Only hide the toolbar if the screen reader is not active
            toolbar.isVisible = ScreenReader.isActive()
          }
        }
      }

    addMouseListener(hoverTopPanelMouseListener)
    labelPanel.addMouseListener(hoverTopPanelMouseListener)
  }

  private fun createToolbar(
    actions: List<AnAction>,
    toolbarCustomization: (ActionToolbar) -> Unit,
  ): JComponent? {
    if (actions.isEmpty()) {
      return null
    }
    return ActionManager.getInstance()
      .createActionToolbar("sceneView", DefaultActionGroup(actions), true)
      .apply {
        toolbarCustomization(this)
        this.targetComponent = toolbarTargetComponent
      }
      .component
      .apply {
        isOpaque = false
        border = JBUI.Borders.empty()
      }
  }

  private fun JComponent?.minimumWidth(): Int = this?.minimumSize?.width ?: 0
}
