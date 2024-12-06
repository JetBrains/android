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
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import org.jetbrains.annotations.VisibleForTesting

/** Distance between the bottom bound of model name and top bound of SceneView. */
@SwingCoordinate private const val TOP_BAR_BOTTOM_MARGIN = 3

/**
 * This panel wraps both the label and the toolbar and puts them left aligned (label) and right
 * aligned (the toolbar).
 */
class SceneViewTopPanel(
  private val toolbarTargetComponent: JComponent,
  private val statusIconAction: AnAction?,
  private val toolbarActions: List<AnAction>,
  private val labelPanel: JComponent,
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
    val toolbarActionGroup = DefaultActionGroup(toolbarActions)
    val toolbar =
      createToolbar(listOf(ShowActionGroupInPopupAction(toolbarActionGroup)))?.also {
        add(it, BorderLayout.LINE_END)
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
  }

  private fun createToolbar(
    actions: List<AnAction>,
    toolbarCustomization: (ActionToolbar) -> Unit = {},
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

  /**
   * Returns the visibility of the [SceneViewTopPanel]. The panel is visible if there are any
   * actions available or if the [labelPanel] is visible.
   */
  override fun isVisible(): Boolean {
    return labelPanel.isVisible || toolbarActions.isNotEmpty()
  }

  /** [AnAction] that displays the actions of the given [ActionGroup] in a popup. */
  @VisibleForTesting
  class ShowActionGroupInPopupAction(val actionGroup: ActionGroup) :
    AnAction("Show Toolbar Actions", null, StudioIcons.Common.OVERFLOW) {
    override fun actionPerformed(e: AnActionEvent) {
      val popup =
        JBPopupFactory.getInstance()
          .createActionGroupPopup(
            null,
            actionGroup,
            e.dataContext,
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            false,
          )
      e.inputEvent?.component?.let { component ->
        val location = component.locationOnScreen
        location.translate(0, component.height)
        popup.showInScreenCoordinates(component, location)
        return@actionPerformed
      }
      popup.showInBestPositionFor(e.dataContext)
    }
  }
}
