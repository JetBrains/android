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
package com.android.tools.idea.compose.preview.animation

import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.message
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.InplaceButton
import com.intellij.ui.JBColor
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.UiDecorator
import com.intellij.ui.tabs.impl.JBEditorTabsBorder
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Insets
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.border.MatteBorder

/** Tabs panel with enabled navigation. */
class AnimationTabs(surface: DesignSurface<*>) :
  JBTabsImpl(surface.project, IdeFocusManager.getInstance(surface.project), surface.project) {
  private val decoration = UiDecorator.UiDecoration(null, Insets(5, 10, 5, 2))

  init {
    border = MatteBorder(0, 0, 1, 0, JBColor.border())
    ActionToolbarUtil.makeToolbarNavigable(myMoreToolbar)
    setUiDecorator { decoration }
  }

  fun addTabWithCloseButton(info: TabInfo, closeAction: (tabInfo: TabInfo) -> Unit): TabInfo {
    return super.addTab(info).also { tabInfo ->
      getTabLabel(tabInfo)
        .add(CloseButton(CloseActionListener(tabInfo, closeAction)), BorderLayout.EAST)
    }
  }

  override fun addTab(info: TabInfo?): TabInfo {
    return super.addTab(info).also { tabInfo ->
      getTabLabel(tabInfo).add(JPanel(), BorderLayout.EAST)
    }
  }

  override fun createTabLabel(info: TabInfo): TabLabel =
    FocusableTabLabel(this, info).apply {
      isFocusable = true
      isCreated = true
    }

  private inner class CloseActionListener(
    private val tabInfo: TabInfo,
    private val closeAction: (tabInfo: TabInfo) -> Unit
  ) : ActionListener {
    override fun actionPerformed(e: ActionEvent?) {
      removeTab(tabInfo)
      closeAction(tabInfo)
    }
  }

  private class CloseButton(actionListener: ActionListener?) :
    InplaceButton(
      IconButton(
        message("animation.inspector.action.close.tab"),
        AllIcons.Actions.Close,
        AllIcons.Actions.CloseHovered
      ),
      actionListener
    ) {
    init {
      preferredSize = JBUI.size(16)
      minimumSize = preferredSize // Prevent layout phase from squishing this button
    }
  }

  override fun createTabBorder() = JBEditorTabsBorder(this)

  inner class FocusableTabLabel(tabs: JBTabsImpl, info: TabInfo) : TabLabel(tabs, info) {

    init {
      if (mouseListeners.size != 1) {
        logger<FocusableTabLabel>()
          .warn("FocusableTabLabel is expected to have a single MouseListener.")
      }
      mouseListeners.getOrNull(0)?.let {
        val ignoreRightClickListener =
          object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
              if (SwingUtilities.isRightMouseButton(e))
                return // Ignore right-click events, so we don't show the context menu popup
              it.mouseClicked(e)
            }

            override fun mousePressed(e: MouseEvent?) {
              if (SwingUtilities.isRightMouseButton(e))
                return // Ignore right-click events, so we don't show the context menu popup
              it.mousePressed(e)
            }

            override fun mouseReleased(e: MouseEvent?) {
              if (SwingUtilities.isRightMouseButton(e))
                return // Ignore right-click events, so we don't show the context menu popup
              it.mouseReleased(e)
            }

            override fun mouseEntered(e: MouseEvent?) {
              it.mouseEntered(e)
            }

            override fun mouseExited(e: MouseEvent?) {
              it.mouseExited(e)
            }
          }
        removeMouseListener(it)
        addMouseListener(ignoreRightClickListener)
      }
    }

    var isCreated = false
    override fun isFocusable(): Boolean {
      // Make sure label is focusable until it's marked as created.
      return !isCreated || super.isFocusable()
    }
  }
}
