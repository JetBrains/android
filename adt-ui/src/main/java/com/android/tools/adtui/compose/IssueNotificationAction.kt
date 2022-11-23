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
package com.android.tools.adtui.compose

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.AnActionLink
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JToolTip
import javax.swing.SwingConstants
import javax.swing.border.Border

private const val ACTION_BACKGROUND_ALPHA = 0x1A
private const val ACTION_BORDER_ALPHA = 0xBF
private const val ACTION_BORDER_ARC_SIZE = 5
private const val ACTION_BORDER_THICKNESS = 1

private fun chipBorder(color: Color): Border = RoundedLineBorder(UIUtil.toAlpha(color, ACTION_BORDER_ALPHA),
                                                                 ACTION_BORDER_ARC_SIZE,
                                                                 ACTION_BORDER_THICKNESS)

interface ComposeStatus {
  val icon: Icon?
  val title: String
  val description: String
  /** When true, the refresh icon will be displayed next to the notification chip. */
  val hasRefreshIcon: Boolean
  val presentation: Presentation?

  companion object {
    val PRESENTATION = Key<Presentation>("ComposeStatus.Presentation")

    /**
     * When not null, this will define the text alignment in the notification chip. One of [SwingConstants.LEADING] or
     * [SwingConstants.TRAILING].
     */
    val TEXT_ALIGNMENT = Key<Int>("ComposeStatus.TextAlignment")
  }

  /**
   * Enum representing the different UI color states that the action might have for the border and background.
   */
  enum class Presentation(baseColorLight: Int, baseColorDark: Int = baseColorLight) {
    Error(0xE53E4D),
    Warning(0xEDA200);

    val color = JBColor(UIUtil.toAlpha(Color(baseColorLight), ACTION_BACKGROUND_ALPHA),
                        UIUtil.toAlpha(Color(baseColorDark), ACTION_BACKGROUND_ALPHA))
    val border = chipBorder(color)
  }
}

/**
 * Creates a new [AnActionLink] with the given [text]. The returned [AnActionLink] will use the given [delegateDataContext] to obtain
 * the associated information when calling into the [action].
 */
fun actionLink(text: String, action: AnAction, delegateDataContext: DataContext): AnActionLink =
  AnActionLink(text, action).apply {
    dataProvider = DataProvider { dataId -> delegateDataContext.getData(dataId) }
  }

/**
 * Action that reports the current state of the Compose subsystem. Local issues for a given Compose subsystem are reported as part of the
 * subsystem itself and not in this action.
 *
 * Clicking on the action will open a pop-up with additional details and action buttons.
 * @param popupAlarm used to show and hide the popup as a hint.
 */
open class IssueNotificationAction(
  private val createStatusInfo: (Project, DataContext) -> ComposeStatus?,
  private val createInformationPopup: (Project, DataContext) -> InformationPopup?,
  private val popupAlarm: Alarm = Alarm()
) : AnAction(), RightAlignedToolbarAction, CustomComponentAction, Disposable {

  /**
   * The currently opened popup.
   */
  private var popup: InformationPopup? = null

  /**
   * Creates an [AnActionEvent] from a mouse event, it's a lambda because we can replace with our own fake [DataContext].
   */
  @VisibleForTesting
  var actionEventCreator: (MouseEvent, IssueNotificationAction) -> AnActionEvent = { me, action ->
    AnActionEvent.createFromInputEvent(
      me,
      ActionPlaces.EDITOR_POPUP,
      PresentationFactory().getPresentation(action),
      ActionToolbar.getDataContextFor(me.component),
      false, true
    )
  }

  /**
   * [MouseAdapter] that schedules the popup.
   */
  @VisibleForTesting
  val mouseListener = object : MouseAdapter() {
    override fun mouseEntered(me: MouseEvent) {
      popupAlarm.cancelAllRequests()
      popupAlarm.addRequest(
        {
          popup.takeUnless {
            it?.isVisible() == true // Do not show if already showing, take unless returns null if the popup is visible
          }.let {
            val anActionEvent = actionEventCreator(me, this@IssueNotificationAction)
            showPopup(anActionEvent)
          }
        },
        Registry.intValue("ide.tooltip.initialReshowDelay") // Delay time before showing the popup
      )
    }

    override fun mouseExited(me: MouseEvent) {
      popupAlarm.cancelAllRequests()

      // When the mouse leaves the button, we schedule an alarm to close the popup.
      scheduleClosePopup(popupAlarm)
    }
  }

  fun scheduleClosePopup(alarm: Alarm) {
    popup?.let { informationPopup ->
      alarm.addRequest(
        { informationPopup.hidePopup() },
        // Adding initial delay value as used in the IntelliJ TrafficLightPop
        Registry.intValue("ide.tooltip.initialDelay.highlighter")
      )
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
    object : ActionButtonWithText(this, presentation, place, Dimension(0, 0)) {
      private val insets = JBUI.insets(3)
      private val actionPresentation: ComposeStatus.Presentation?
        get() = myPresentation.getClientProperty(ComposeStatus.PRESENTATION)
      val textAlignment: Int
        get() = myPresentation.getClientProperty(ComposeStatus.TEXT_ALIGNMENT) ?: SwingConstants.LEADING

      private val font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)

      private val textColor = JBColor(Gray._110, Gray._187)

      override fun isBackgroundSet(): Boolean =
        actionPresentation != null || super.isBackgroundSet()

      override fun getBackground(): Color? =
        actionPresentation?.color ?: super.getBackground()

      override fun getFont() = font

      override fun getForeground() = textColor

      override fun getBorder(): Border =
        if (popState == POPPED)
          chipBorder(JBUI.CurrentTheme.ActionButton.hoverBorder())
        else
          actionPresentation?.border ?: JBUI.Borders.empty()

      override fun getMargins(): Insets = insets

      override fun addNotify() {
        super.addNotify()
        addMouseListener(mouseListener)
        setHorizontalTextPosition(textAlignment)
      }

      override fun removeNotify() {
        removeMouseListener(mouseListener)
        super.removeNotify()
      }

      override fun createToolTip(): JToolTip? = null

      // Do not display the regular tooltip
      override fun updateToolTipText() {}

    }.apply {
      setHorizontalTextPosition(textAlignment)
    }

  override fun displayTextInToolbar(): Boolean = true

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val presentation = e.presentation
    createStatusInfo(project, e.dataContext)?.let {
      presentation.icon = it.icon
      presentation.text = it.title
      presentation.description = it.description
      presentation.putClientProperty(ComposeStatus.PRESENTATION, it.presentation)
      val isErrorOrWarningIcon = it.icon == AllIcons.General.Error || it.icon == AllIcons.General.Warning
      presentation.putClientProperty(ComposeStatus.TEXT_ALIGNMENT,
                                     if (isErrorOrWarningIcon) SwingConstants.TRAILING else SwingConstants.LEADING)
    }
  }

  /**
   * Shows the actions popup.
   */
  private fun showPopup(e: AnActionEvent) {
    popupAlarm.cancelAllRequests()
    val project = e.project ?: return
    popup = createInformationPopup(project, e.dataContext)?.also { newPopup ->
      // Whenever the mouse is inside the popup we cancel the existing alarms via callback
      newPopup.onMouseEnteredCallback = { popupAlarm.cancelAllRequests() }
      newPopup.showPopup(this, e.inputEvent)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    showPopup(e)
  }

  override fun dispose() {
    popup = null
    popupAlarm.cancelAllRequests()
  }
}
