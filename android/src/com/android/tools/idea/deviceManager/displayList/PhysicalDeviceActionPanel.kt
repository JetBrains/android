/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.displayList

import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.adtui.common.ColoredIconGenerator.generateWhiteIcon
import com.android.tools.idea.avdmanager.EmulatorAdvFeatures
import com.android.tools.idea.deviceManager.avdmanager.actions.AvdSummaryAction
import com.android.tools.idea.deviceManager.avdmanager.actions.AvdUiAction
import com.android.tools.idea.deviceManager.avdmanager.actions.ColdBootNowAction
import com.android.tools.idea.deviceManager.avdmanager.actions.DeleteAvdAction
import com.android.tools.idea.deviceManager.avdmanager.actions.DuplicateAvdAction
import com.android.tools.idea.deviceManager.avdmanager.actions.EditAvdAction
import com.android.tools.idea.deviceManager.avdmanager.actions.ExploreAvdAction
import com.android.tools.idea.deviceManager.avdmanager.actions.ExplorePhysicalDeviceAction
import com.android.tools.idea.deviceManager.avdmanager.actions.InstallSystemImageAction
import com.android.tools.idea.deviceManager.avdmanager.actions.PhysicalDeviceUiAction
import com.android.tools.idea.deviceManager.avdmanager.actions.RenamePhysicalDeviceAction
import com.android.tools.idea.deviceManager.avdmanager.actions.RepairAvdAction
import com.android.tools.idea.deviceManager.avdmanager.actions.RunAvdAction
import com.android.tools.idea.deviceManager.avdmanager.actions.ShowAvdOnDiskAction
import com.android.tools.idea.deviceManager.avdmanager.actions.StopAvdAction
import com.android.tools.idea.deviceManager.avdmanager.actions.WipeAvdDataAction
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBMenuItem
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.border.Border

/**
 * An action panel that behaves similarly to an Android overflow menu. Actions which
 * do not fit in the space provided are relegated to an overflow menu that can be invoked by
 * clicking on the last icon of the menu.
 */
class PhysicalDeviceActionPanel(
  private val deviceProvider: PhysicalDeviceUiAction.PhysicalDeviceProvider, numVisibleActions: Int
) : JPanel() {
  private val overflowMenu = JBPopupMenu()
  private val overflowMenuButton = FocusableHyperlinkLabel("", AllIcons.Actions.MoveDown)
  private val margins: Border = JBUI.Borders.empty(5, 3, 5, 3)
  var visibleComponents: MutableList<FocusableHyperlinkLabel> = mutableListOf()
  private var focused = false
  private var focusedComponent = -1
  private var highlighted = false

  private val actions: List<PhysicalDeviceUiAction>
    get() = listOf(
      // TODO
      ExplorePhysicalDeviceAction(deviceProvider),
      RenamePhysicalDeviceAction(deviceProvider)
      // rename
      // other
    )

  /*
  // FIXME(qumeric) seems like we don't need it, but leaving it here for some time just in case
  override fun refreshAvds() {
    refreshProvider.refreshAvds()
  }

  override fun refreshAvdsAndSelect(avdToSelect: AvdInfo?) {
    refreshProvider.refreshAvdsAndSelect(avdToSelect)
  }

  override val project: Project?
    get() = refreshProvider.project

  override val avdProviderComponent: JComponent
    get() = refreshProvider.getComponent()
   */

  fun showPopup(c: Component, e: MouseEvent) {
    overflowMenu.show(c, e.x, e.y)
  }

  fun runFocusedAction() {
    visibleComponents[focusedComponent].doClick()
  }

  fun cycleFocus(backward: Boolean): Boolean = if (backward) {
    if (focusedComponent == -1) {
      focusedComponent = visibleComponents.size - 1
      true
    }
    else {
      focusedComponent--
      focusedComponent != -1
    }
  }
  else {
    if (focusedComponent == visibleComponents.size - 1) {
      focusedComponent = -1
      false
    }
    else {
      focusedComponent++
      true
    }
  }

  // TODO(qumeric): change this and setHighlighted to proper Kotlin setters
  fun setFocused(focused: Boolean) {
    this.focused = focused
    if (!focused) {
      focusedComponent = -1
    }
  }

  fun setHighlighted(highlighted: Boolean) {
    this.highlighted = highlighted
  }

  // TODO(qumeric):should be probably merged with AvdActionPanel.FocusableHyperlinkListener
  inner class FocusableHyperlinkLabel internal constructor(text: String?, icon: Icon?) : HyperlinkLabel(
    text, JBColor.foreground(), JBColor.background(), JBColor.foreground()
  ) {
    var highlightedIcon: Icon? = null

    init {
      setIcon(icon)
      isOpaque = false
      setUseIconAsLink(true)
      if (icon != null) {
        highlightedIcon = generateWhiteIcon(myIcon)
      }
    }

    internal constructor(text: String?, icon: Icon?, enabled: Boolean) : this(text, icon) {
      isEnabled = enabled
    }

    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      if (focused && focusedComponent != -1 && visibleComponents[focusedComponent] === this) {
        g.color = UIManager.getColor("Table.selectionForeground")
        UIUtil.drawDottedRectangle(g, 0, 0, width - 2, height - 2)
      }
      if (myIcon != null) {
        // Repaint the icon
        var theIcon = myIcon
        if (highlighted && highlightedIcon != null) {
          // Use white when the cell is highlighted
          theIcon = highlightedIcon
        }
        theIcon.paintIcon(this, g, 0, (height - theIcon.iconHeight) / 2)
      }
    }
  }

  init {
    fun addVisibleHyperlinkLabel(fhl: FocusableHyperlinkLabel) {
      add(fhl)
      visibleComponents.add(fhl)
    }

    isOpaque = true
    border = JBUI.Borders.empty(10)
    layout = FlowLayout(FlowLayout.RIGHT, 3, 0)

    var numVisibleActions = numVisibleActions
    var visibleActionCount = 0
    var errorState = false

    for (action in actions) {
      val actionLabel: JComponent = if (errorState || numVisibleActions != -1 && visibleActionCount >= numVisibleActions) {
        // Add extra items to the overflow menu
        JBMenuItem(action).apply {
          overflowMenu.add(this)
        }
      }
      else {
        // Add visible items to the panel
        FocusableHyperlinkLabel("", action.icon).apply {
          addHyperlinkListener(action)
          addVisibleHyperlinkLabel(this)
          visibleActionCount++
        }
      }
      actionLabel.apply {
        toolTipText = action.description
        border = margins
      }
    }

    overflowMenuButton.apply {
      border = margins
      addHyperlinkListener {
        overflowMenu.show(this, this.x - overflowMenu.preferredSize.width, this.y)
      }
      addVisibleHyperlinkLabel(this)
    }

    addKeyListener(object : KeyAdapter() {
      override fun keyTyped(e: KeyEvent) {
        if (e.keyChar.toInt() == KeyEvent.VK_ENTER || e.keyChar.toInt() == KeyEvent.VK_SPACE) {
          runFocusedAction()
        }
      }
    })
  }
}