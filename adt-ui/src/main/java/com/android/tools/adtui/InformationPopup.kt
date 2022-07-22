/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.adtui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.AnActionLink
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

private const val POPUP_PADDING = 6
private const val POPUP_MIN_WIDTH = 296
private const val POPUP_CONTENT_PADDING = 10
private const val POPUP_LINK_SEPARATOR_WIDTH = 15
private const val POPUP_LINK_PADDING_TOP_AND_BOTTOM = 4
private const val POPUP_LINK_PADDING_LEFT_AND_RIGHT = 10
private const val POPUP_DESCRIPTION_TOP_AND_BOTTOM_INDENT = 10
private const val POPUP_DESCRIPTION_LEFT_INDENT = 10
private const val POPUP_DESCRIPTION_RIGHT_INDENT = 6

private fun createLinksBar(links: Collection<AnActionLink>, hidePopup: () -> Unit): JPanel {
  val panel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.LINE_AXIS)
  }

  links.forEachIndexed { index, linkLabel ->
    if (index != 0) panel.add(Box.createHorizontalStrut(JBUI.scale(POPUP_LINK_SEPARATOR_WIDTH)))
    linkLabel.addActionListener {
      // Invoke later to avoid closing the tab before the action has executed.
      invokeLater {
        hidePopup()
      }
    }
    panel.add(linkLabel)
  }

  panel.add(Box.createHorizontalGlue())

  panel.isOpaque = true
  panel.background = UIUtil.getToolTipActionBackground()
  panel.border = JBUI.Borders.empty(POPUP_LINK_PADDING_TOP_AND_BOTTOM, POPUP_LINK_PADDING_LEFT_AND_RIGHT)

  return panel
}

private fun createContentPanel(
  title: String?,
  details: String,
  additionalActions: List<AnAction>,
  links: Collection<AnActionLink>,
  hidePopup: () -> Unit
): JComponent {
  val content = JPanel(GridBagLayout()).also {
    it.isOpaque = false
    it.background = UIUtil.getToolTipBackground()
  }
  val gc = GridBag()

  if (title != null) {
    gc.nextLine().next()
      .anchor(GridBagConstraints.LINE_START)
      .weightx(1.0)
      .fillCellHorizontally()
      .insets(POPUP_CONTENT_PADDING, POPUP_CONTENT_PADDING, POPUP_CONTENT_PADDING, 0)
    content.add(JLabel(XmlStringUtil.wrapInHtml(title)), gc)
  }

  gc.nextLine().next()
    .anchor(GridBagConstraints.LINE_START)
    .fillCellHorizontally()
    .weightx(1.0)
    .insets(POPUP_DESCRIPTION_TOP_AND_BOTTOM_INDENT, POPUP_DESCRIPTION_LEFT_INDENT, POPUP_DESCRIPTION_TOP_AND_BOTTOM_INDENT,
            POPUP_DESCRIPTION_RIGHT_INDENT)
  content.add(JLabel(XmlStringUtil.wrapInHtml(details)), gc)

  if (additionalActions.isNotEmpty()) {
    val presentation = Presentation()
    presentation.icon = AllIcons.Actions.More
    presentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, java.lang.Boolean.TRUE)
    val menuButton = ActionButton(
      DefaultActionGroup(additionalActions).apply {
        isPopup = true
      },
      presentation,
      ActionPlaces.EDITOR_POPUP,
      ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)

    content.add(menuButton, gc.next().anchor(GridBagConstraints.LINE_END).weightx(0.0).insets(10, 6, 10, 6))
  }

  if (links.isNotEmpty()) {
    content.add(createLinksBar(links, hidePopup),
                gc.nextLine().next().anchor(GridBagConstraints.LINE_START)
                  .fillCellHorizontally()
                  .coverLine()
                  .weightx(1.0))
  }

  return content
}

/**
 * Creates a popup that displays a title, a description, a list of actions as an overflow menu
 * and a list of links at the bottom.
 * The list of actions or links can be empty.
 *
 * The popup contains an optional `title` and `description` that can contain HTML contents. The list of additional actions will be
 * displayed in the overflow menu and the list of links at the bottom of the popup.
 */
class InformationPopup(
  title: String?,
  description: String,
  additionalActions: List<AnAction>,
  links: Collection<AnActionLink>
) : Disposable {
  /**
   * This tracks if the mouse has been over the popup.
   */
  @VisibleForTesting
  internal var hasMouseHoveredOverPopup = false
  private val content: JComponent by lazy {
    createContentPanel(title, description, additionalActions, links, hidePopup = ::hidePopup).apply {
      addMouseListener(object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent?) {
          hasMouseHoveredOverPopup = true
        }

        override fun mouseExited(e: MouseEvent?) {
          // If the mouse exits the popup and the mouse has already been over it, close the popup.
          // If the mouse has not been over the popup, keep it open since the user might be moving the mouse towards it.
          if (hasMouseHoveredOverPopup) hidePopup()
        }
      })
    }
  }

  /**
   * [JBPopupListener] to reset [hasMouseHoveredOverPopup] when the popup is dismissed.
   */
  private val popupListener = object : JBPopupListener {
    override fun onClosed(event: LightweightWindowEvent) {
      // When the popup is closed, reset the hover tracking
      hasMouseHoveredOverPopup = false
    }
  }
  private var popup: JBPopup? = null

  /**
   * Hides the popup if open.
   */
  fun hidePopup() {
    popup?.let {
      it.cancel()
      Disposer.dispose(it)
    }
    popup = null
  }

  /**
   * Shows the popup, using the given [InputEvent] to position the it.
   */
  fun showPopup(event: InputEvent) {
    hidePopup()

    val newPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(content, null)
      .setCancelOnClickOutside(true)
      .setCancelOnWindowDeactivation(true)
      .addListener(popupListener)
      .createPopup()
    popup = newPopup
    Disposer.register(this, newPopup)

    val owner = event.component as JComponent
    val size: Dimension = content.preferredSize
    size.width = size.width.coerceAtLeast(JBUIScale.scale(POPUP_MIN_WIDTH))

    val point = RelativePoint(
      owner,
      Point(
        owner.width - owner.insets.right + JBUIScale.scale(POPUP_PADDING) - size.width,
        owner.height + JBUIScale.scale(POPUP_PADDING)
      )
    )

    newPopup.size = size
    newPopup.show(point)
  }

  fun isVisible() = popup?.isVisible ?: false

  override fun dispose() {}

  @TestOnly
  fun component(): JComponent = content
}