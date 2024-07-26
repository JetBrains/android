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

import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.common.ColoredIconGenerator
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.AnActionLink
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.SwingConstants
import javax.swing.border.Border

private const val ACTION_BACKGROUND_ALPHA = 0x1A
private const val ACTION_BORDER_ALPHA = 0xBF
private const val ACTION_BORDER_ARC_SIZE = 5
private const val ACTION_BORDER_THICKNESS = 1

val REFRESH_BUTTON =
  ColoredIconGenerator.generateColoredIcon(
    AllIcons.Actions.ForceRefresh,
    JBColor(0x59A869, 0x499C54)
  )

internal fun chipBorder(color: Color): Border = object: RoundedLineBorder(
  UIUtil.toAlpha(color, ACTION_BORDER_ALPHA), ACTION_BORDER_ARC_SIZE, ACTION_BORDER_THICKNESS) {
  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    // Slightly hack RoundedLineBorder since it has a rendering bug in HiDPI modes.
    // Will need to properly fix this by overriding the underlying component's paint method to
    // use a filled Path instead of line rendering for border drawing.
    super.paintBorder(c, g, x+1, y+1, width-1, height-1)
  }
}

/**
 * Represents the status of the IDE regarding states that are relevant to UI Previews and Live Edit,
 * e.g. code out-of-date or up-to-date, syntax errors, build needed, compiling, etc.
 */
interface IdeStatus {
  val icon: Icon?
  val title: String
  val description: String
  /** When true, the refresh icon will be displayed next to the notification chip. */
  val presentation: Presentation?
  val shouldSimplify: Boolean
    get() = false

  companion object {
    val PRESENTATION = Key<Presentation>("IdeStatus.Presentation")

    /**
     * When not null, this will define the text position in the notification chip. One of [SwingConstants.LEADING] or
     * [SwingConstants.TRAILING].
     */
    val TEXT_POSITION = Key<Int>("IdeStatus.TextPosition")
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
 * Action that reports the current [IdeStatus].
 *
 * Clicking on the action will open a pop-up with additional details and action buttons.
 * @param popupAlarm used to show and hide the popup as a hint.
 */
open class IssueNotificationAction(
  private val createStatusInfo: (Project, DataContext) -> IdeStatus?,
  private val createInformationPopup: (Project, DataContext) -> InformationPopup?,
  private val popupAlarm: Alarm = Alarm()
) : IconWithTextAction(), Disposable {

  /**
   * The currently opened popup.
   */
  private var popup: InformationPopup? = null

  // shouldHide and shouldSimplify require running in the UI thread since they access UI state.
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

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
    }
  }

  /**
   * Override this method to change the behavior of spacing between buttons (which includes the area around a button when hovered over).
   */
  open fun margins(): Insets {
    return JBUI.emptyInsets()
  }

  /**
   * Defines the padding between the button and its borders.
   */
  fun insets(): Insets {
    return JBUI.insets(1)
  }

  @UiThread
  open fun shouldHide(status: IdeStatus, dataContext: DataContext) : Boolean {
    return status.icon == null && StringUtil.isEmpty(status.title)
  }

  /**
   * Returns true if a minified version of the status should be displayed for places
   * where screen real estate is limited.
   */
  @UiThread
  open fun shouldSimplify(status: IdeStatus, dataContext: DataContext) : Boolean = false

  override fun displayTextInToolbar(): Boolean = true

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val presentation = e.presentation
    createStatusInfo(project, e.dataContext)?.let {
      presentation.apply {
        if (shouldHide(it, e.dataContext)) {
          isEnabledAndVisible = false
          return@let
        }
        isEnabledAndVisible = true
        icon = it.icon
        text = if (shouldSimplify(it, e.dataContext)) "" else it.title
        description = it.description
        putClientProperty(IdeStatus.PRESENTATION, it.presentation)
        val isErrorOrWarningIcon = it.icon == AllIcons.General.Error || it.icon == AllIcons.General.Warning
        putClientProperty(IdeStatus.TEXT_POSITION, if (isErrorOrWarningIcon) SwingConstants.TRAILING else SwingConstants.LEADING)
      }
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
      getDisposableParentForPopup(e)?.let { newPopup.showPopup(it, e.inputEvent!!) } ?: newPopup.showPopup(this, e.inputEvent!!)
    }
  }

  protected open fun getDisposableParentForPopup(e: AnActionEvent): Disposable? = null

  override fun actionPerformed(e: AnActionEvent) {
    showPopup(e)
  }

  override fun dispose() {
    popup = null
    popupAlarm.cancelAllRequests()
  }
}
