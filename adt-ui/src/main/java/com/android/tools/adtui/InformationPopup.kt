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
import com.intellij.openapi.util.Disposer
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.AnActionLink
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlStringUtil
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

/**
 * Creates a popup that displays a title, a description, a list of actions as an overflow menu and a list of links at the bottom.
 * The list of actions or links can be empty.
 */
interface InformationPopup {

  val popupComponent: JComponent

  var onMouseEnteredCallback: () -> Unit

  /**
   * Hides the popup if open.
   */
  fun hidePopup()

  /**
   * Shows the popup from a parent view (like an action) from a given input event
   *
   * @param disposableParent the [Disposable] parent that triggers the popup
   * @param event the given [InputEvent] to position the it
   */
  fun showPopup(disposableParent: Disposable, event: InputEvent)

  fun isVisible(): Boolean

}

/**
 * The popup contains an optional `title` and `description` that can contain HTML contents.
 * The list of additional actions will be displayed in the overflow menu and the list of links at the bottom of the popup.
 *
 * @param title              The title of the pop-up, it can contain HTML contents
 * @param description        The description text of the pop-up, it can contain HTML contents
 * @param additionalActions  A list of [AnAction] to be performed from the top right menu of the popup
 * @param links              The links of the pop-up eg: "build and re-run"
 */
class InformationPopupImpl(
  title: String?,
  description: String,
  additionalActions: List<AnAction>,
  links: Collection<AnActionLink>
) : InformationPopup, Disposable {

  /**
   * Keeps the popup open until the mouse reaches the popup area.
   */
  @VisibleForTesting
  internal var shouldPopupStayOpen = false

  private var popup: JBPopup? = null

  override var onMouseEnteredCallback = {}

  override val popupComponent: JComponent by lazy {
    createContentPanel(title, description, additionalActions, links, hidePopup = ::hidePopup).apply {
      addMouseListener(object : MouseAdapter() {

        override fun mouseEntered(e: MouseEvent?) {
          onMouseEnteredCallback()
          // The popup should stay open meanwhile the mouse is navigating into the popup area
          shouldPopupStayOpen = true
        }

        override fun mouseExited(e: MouseEvent?) {
          // The popup can be closed once it leaves the popup area
          shouldPopupStayOpen = false
        }
      })
    }
  }

  override fun hidePopup() {
    if (popup?.isDisposed == false) {
      Disposer.dispose(this)
    }
  }

  override fun showPopup(disposableParent: Disposable, event: InputEvent) {
    // The popup is triggered by clicking the disposableParent.
    // The popup should stay open until the mouse reaches the area of the popup.
    shouldPopupStayOpen = true

    val newPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(popupComponent, null)
      .setCancelOnClickOutside(true)
      .setCancelOnWindowDeactivation(true)
      .setCancelOnMouseOutCallback { !shouldPopupStayOpen }
      .createPopup()

    popup = newPopup
    Disposer.register(disposableParent, newPopup)

    val owner = event.component as JComponent
    val size: Dimension = getPopupPreferredSize()
    val point = getPointToShowPopupInWindow(owner, size)

    newPopup.size = size
    newPopup.show(point)
  }

  override fun isVisible(): Boolean {
    return popup?.isVisible ?: false
  }

  override fun dispose() {
    popup?.cancel()
  }

  /**
   * Calculates the [RelativePoint] in the screen where the popup is showing up in the window.
   * The point refers of the top center of the popup that is going to show.
   */
  private fun getPointToShowPopupInWindow(owner: JComponent, size: Dimension) = RelativePoint(
    owner,
    Point(
      owner.width - owner.insets.right + JBUIScale.scale(POPUP_PADDING) - size.width,
      owner.height + JBUIScale.scale(POPUP_PADDING)
    )
  )

  private fun getPopupPreferredSize(): Dimension {
    val size: Dimension = popupComponent.preferredSize
    size.width = size.width.coerceAtLeast(JBUIScale.scale(POPUP_MIN_WIDTH))
    return size
  }

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

  companion object {
    private const val POPUP_PADDING = 6
    private const val POPUP_MIN_WIDTH = 296
    private const val POPUP_CONTENT_PADDING = 10
    private const val POPUP_LINK_SEPARATOR_WIDTH = 15
    private const val POPUP_LINK_PADDING_TOP_AND_BOTTOM = 4
    private const val POPUP_LINK_PADDING_LEFT_AND_RIGHT = 10
    private const val POPUP_DESCRIPTION_TOP_AND_BOTTOM_INDENT = 10
    private const val POPUP_DESCRIPTION_LEFT_INDENT = 10
    private const val POPUP_DESCRIPTION_RIGHT_INDENT = 6
  }
}
