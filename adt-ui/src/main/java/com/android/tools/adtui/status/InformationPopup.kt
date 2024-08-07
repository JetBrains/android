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
package com.android.tools.adtui.status

import com.android.tools.adtui.actions.DropDownAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.NewUI
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.AnActionLink
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Component
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Creates a popup that displays a title, a description, a list of actions as an overflow menu and a list of links at the bottom.
 * The list of actions or links can be empty.
 */
interface InformationPopup : Disposable {

  val popupComponent: JComponent

  var onMouseEnteredCallback: () -> Unit

  /**
   * Hides the popup if open.
   */
  fun hidePopup()

  /**
   * Shows the popup from a parent view (like an action), anchored to the {@param owner}.
   *
   * @param disposableParent the [Disposable] parent that triggers the popup
   * @param owner the parent [JComponent]
   */
  fun showPopup(disposableParent: Disposable, owner: JComponent)

  fun isVisible(): Boolean

}

/**
 * If the underlying action creates a popup, its DataProvider should return true for
 * this data key to prevent closing the parent popup prematurely.
 */
val POPUP_ACTION = Key<Boolean>("information_popup.popup_action")

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
) : InformationPopup {

  /**
   * Keeps the popup open until the mouse reaches the popup area.
   */
  @VisibleForTesting
  internal var hasEnteredPopup = false

  private var popup: JBPopup? = null

  override var onMouseEnteredCallback = {}

  private val hidePopupTimer = Timer(POPUP_DISMISS_TIMEOUT_MS) {
    hidePopup()
  }

  override val popupComponent: JComponent by lazy {
    createContentPanel(title, description, additionalActions, links, hidePopup = ::hidePopup).apply {
      addMouseListener(object : MouseAdapter() {

        override fun mouseEntered(e: MouseEvent?) {
          onMouseEnteredCallback()
          // The popup should stay open when the mouse is moving on the popup area.
          hasEnteredPopup = true
          // Stops the timer count to prevent the popup to be closed when the mouse is on the popup area.
          hidePopupTimer.stop()
        }
      })
    }
  }

  override fun hidePopup() {
    if (popup?.isDisposed == false) {
      Disposer.dispose(this)
    }
    // The timer will continue to launch its callback on every time-out.
    // Stopping it avoids polling as the popup is hidden already.
    hidePopupTimer.stop()
  }

  override fun showPopup(disposableParent: Disposable, owner: JComponent) {
    val size: Dimension = getPopupPreferredSize()
    val newPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(popupComponent, null)
      .setCancelOnClickOutside(true)
      .setCancelOnWindowDeactivation(true)
      .setCancelOnMouseOutCallback { e ->
        if (!hasEnteredPopup) {
          val point = SwingUtilities.convertPoint(e.component, e.point, owner)
          if (!point.isIntoArea(component = owner)) {
            // Starts the timer count to close the popup when the mouse is not on the popup area.
            hidePopupTimer.start()
          }
          hasEnteredPopup = false

          return@setCancelOnMouseOutCallback false
        }

        popup?.let { openPopup ->
          val popupWindow = SwingUtilities.getWindowAncestor(openPopup.content)
          val currentWindow = SwingUtilities.getWindowAncestor(e.component)
          if (popupWindow != null && currentWindow != null) {
            if (currentWindow != popupWindow && !popupWindow.ownedWindows.contains(currentWindow)) {
              // Starts the timer count to close the popup if the mouse is currently not over the popup window
              // or any of the owned windows (sub-popups).
              hidePopupTimer.start()
            }
          }
        } ?: hidePopupTimer.start()

        // This callback always returns false as the popup timer is taking care to close the popup already.
        return@setCancelOnMouseOutCallback false
      }
      .createPopup()

    popup = newPopup
    Disposer.register(disposableParent, newPopup)

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

  private fun Point.isIntoArea(component: Component): Boolean {
    // We can't rely on AbstractPopup$Canceller because it doesn't take into account of the padding between the button and the popup.
    val padding = (JBUIScale.sysScale() * POPUP_PADDING).toInt()
    // We add padding around the parent, because we added a gap (POPUP_PADDING) between the parent and the popup.
    // We add it on all four sides because it's just a nicer experience.
    return Rectangle(
      -padding,
      -padding,
      component.width + 2 * padding,
      component.height + 2 * padding
    ).contains(this)
  }

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
        if (linkLabel.getUserData(POPUP_ACTION) != true) {
          // Invoke later to avoid closing the tab before the action has executed.
          invokeLater {
            hidePopup()
          }
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
      it.isOpaque = true
      // See b/267198091#comment4 for the motivation of this change.
      // TODO: Checking for NewUI.isEnabled() should be removed when the new UI becomes stable.
      it.background = if (NewUI.isEnabled()) JBUI.CurrentTheme.Editor.Tooltip.BACKGROUND else UIUtil.getToolTipBackground()
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
      val dropDownAction = DropDownAction(null, null , AllIcons.Actions.More).also {
        it.addAll(additionalActions)
      }
      val menuButton = ActionButton(
        dropDownAction,
        presentation,
        ActionPlaces.EDITOR_POPUP,
        ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)

      menuButton.presentation.putClientProperty(CustomComponentAction.COMPONENT_KEY, menuButton)

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
    private const val POPUP_DISMISS_TIMEOUT_MS = 400
  }
}
