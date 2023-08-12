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
package com.android.tools.idea.uibuilder.structure

import com.android.SdkConstants
import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.uibuilder.structure.NlVisibilityModel.Visibility
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.colorpicker.LightCalloutPopup
import com.intellij.ui.components.JBLabel
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JPopupMenu

/**
 * Menu that displays all available visibility options to choose from.
 * [SdkConstants.ANDROID_URI] and [SdkConstants.TOOLS_URI].
 */
class NlVisibilityPopupMenu(
  onClick: (visibility: Visibility, uri: String) -> Unit,
  onClose: (() -> Unit)) {
  val content = VisibilityPopupContent(onClick)
  private val popupMenu = LightCalloutPopup(content, onClose)

  fun show(model: NlVisibilityModel, invoker: JComponent, p: Point) {
    val application = ApplicationManager.getApplication()
    if (!application.isReadAccessAllowed) {
      return runReadAction { show(model, invoker, p) }
    }
    content.update(model)
    popupMenu.show(invoker, p, Balloon.Position.atRight)
  }

  val balloon: Balloon? get() = popupMenu.getBalloon()

  fun cancel() {
    popupMenu.cancel()
  }
}

/**
 * UI contents inside the popup menu.
 */
class VisibilityPopupContent(
  private val onClick: (visibility: Visibility, uri: String) -> Unit) : JPanel() {
  companion object {
    private const val MENU_OUTER_PADDING = 10
    private const val MENU_INNER_PADDING = 5
    private const val ICON_PADDING_X = 6
    private const val ICON_PADDING_Y = 6
  }

  var androidButtons: VisibilityPopupButtons? = null
  var toolsButtons: VisibilityPopupButtons? = null

  init {
    layout = GridBagLayout()
    background = secondaryPanelBackground
    border = BorderFactory.createEmptyBorder(MENU_OUTER_PADDING, MENU_OUTER_PADDING, MENU_OUTER_PADDING, MENU_OUTER_PADDING)

    val c = GridBagConstraints()
    c.fill = GridBagConstraints.NONE
    c.anchor = GridBagConstraints.WEST
    c.gridx = 0
    c.gridy = 0
    c.ipady = MENU_INNER_PADDING

    c.gridwidth = 4
    val androidText = JBLabel("android:visibility")
    add(androidText, c)

    c.gridwidth = 1
    c.gridy++
    c.ipady = ICON_PADDING_Y
    c.ipadx = ICON_PADDING_X
    androidButtons = addButtons(this, c, SdkConstants.ANDROID_URI)

    // Having trouble getting this separator to show.
    c.gridy++
    c.ipadx = 0
    c.gridx = 0
    c.gridwidth = 4
    val separator = JPopupMenu.Separator()
    separator.preferredSize = Dimension(1, 1)
    add(separator, c)

    c.fill = GridBagConstraints.NONE
    c.gridy++
    c.gridwidth = 4
    val toolsText = JBLabel("tools:visibility")
    add(toolsText, c)

    c.gridwidth = 1
    c.gridy++
    c.ipady = ICON_PADDING_Y
    c.ipadx = ICON_PADDING_X
    toolsButtons = addButtons(this, c, SdkConstants.TOOLS_URI)
  }

  fun update(model: NlVisibilityModel) {
    androidButtons?.update(model)
    toolsButtons?.update(model)
  }

  private fun addButtons(panel: JPanel,
                         c: GridBagConstraints,
                         uri: String): VisibilityPopupButtons {
    val buttons = VisibilityPopupButtons(uri, onClick)
    buttons.buttons.forEach {
      panel.add(it, c)
      c.gridx++
    }
    return buttons
  }
}

class VisibilityPopupButtons(
  private val uri: String,
  private val onClickListener: (visibility: Visibility, uri: String) -> Unit) {

  private val isToolsAttr = uri == SdkConstants.TOOLS_URI
  val buttons = ArrayList<NlVisibilityButton>()

  init {
    buttons.add(createButton(Visibility.NONE))
    buttons.add(createButton(Visibility.VISIBLE))
    buttons.add(createButton(Visibility.INVISIBLE))
    buttons.add(createButton(Visibility.GONE))
  }

  fun update(model: NlVisibilityModel) {
    buttons.forEach {
      if (isToolsAttr) {
        it.isClicked = it.visibility == model.toolsVisibility
      } else {
        it.isClicked = it.visibility == model.androidVisibility
      }
    }
  }

  private fun onClick(button: NlVisibilityButton) {
    if (button.isClicked) {
      return
    }

    buttons.forEach {
      it.isClicked = false
    }
    button.isClicked = true
    button.parent?.repaint()
    onClickListener.invoke(button.visibility!!, uri)
  }

  private fun createButton(visibility: Visibility): NlVisibilityButton {
    val isToolsAttr = uri == SdkConstants.TOOLS_URI
    val button = NlVisibilityButton()
    val item = ButtonPresentation(visibility, isToolsAttr)
    item.updateBgWhenHovered = true
    button.update(item)

    button.addMouseListener(object: MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        onClick(button)
      }

      override fun mouseEntered(e: MouseEvent?) {
        button.isHovered = true
        button.parent.repaint()
      }

      override fun mouseExited(e: MouseEvent?) {
        button.isHovered = false
        button.parent.repaint()
      }
    })

    return button
  }
}
