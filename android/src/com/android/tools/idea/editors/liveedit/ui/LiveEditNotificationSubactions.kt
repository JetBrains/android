/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.editors.liveedit.ui

import com.android.tools.adtui.compose.POPUP_ACTION
import com.android.tools.adtui.compose.REFRESH_BUTTON
import com.android.tools.idea.editors.literals.LiveEditAnActionListener
import com.android.tools.idea.editors.literals.LiveEditService
import com.android.tools.idea.editors.literals.LiveEditService.Companion.LiveEditTriggerMode.LE_TRIGGER_AUTOMATIC
import com.android.tools.idea.editors.literals.LiveEditService.Companion.LiveEditTriggerMode.LE_TRIGGER_MANUAL
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration.LiveEditMode.DISABLED
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration.LiveEditMode.LIVE_EDIT
import com.android.tools.idea.logcat.AndroidLogcatToolWindowFactory
import com.android.tools.idea.run.deployment.liveedit.LiveEditBundle
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus
import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.Dimension
import java.awt.Insets
import java.awt.Point
import javax.swing.JComponent

const val MANUAL_LIVE_EDIT_ACTION_ID = "Compose.Live.Edit.ManualLiveEdit"
const val SHOW_LOGCAT_ACTION_ID = "Compose.Live.Edit.ShowLogcat"

/**
 * [AnAction] that triggers a compilation of the current module. The build will automatically
 * trigger a refresh of the surface. The action visibility is controlled by
 * [LiveEditStatus.hasRefreshIcon]
 */
internal class RedeployAction :
  AnAction(
    null,
    null,
    null
  ), CustomComponentAction {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val status = getStatusInfo(project, e.dataContext)
    when (status.redeployMode) {
      LiveEditStatus.Companion.RedeployMode.REFRESH -> {
        invokeActionNow(e, ManualLiveEditAction())
      }
      LiveEditStatus.Companion.RedeployMode.RERUN -> ActionUtil.getAction("Run")?.let {
        ActionUtil.invokeAction(it, e.dataContext, e.place, e.inputEvent, null)
      }
      LiveEditStatus.Companion.RedeployMode.NONE -> {
        // do nothing
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val status = getStatusInfo(project, e.dataContext)
    when (status.redeployMode) {
      LiveEditStatus.Companion.RedeployMode.REFRESH -> {
        e.presentation.icon = REFRESH_BUTTON
        e.presentation.description = LiveEditBundle.message("le.status.out_of_date.description")
        e.presentation.isEnabledAndVisible = true
      }
      LiveEditStatus.Companion.RedeployMode.RERUN -> {
        e.presentation.icon = AllIcons.Actions.Restart
        e.presentation.description = LiveEditBundle.message("le.build.redeploy.description")
        e.presentation.isEnabledAndVisible = true
      }
      LiveEditStatus.Companion.RedeployMode.NONE -> {
        e.presentation.isEnabledAndVisible = false
      }
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String) =
    object : ActionButtonWithText(this, presentation, place, Dimension(20, 20)) {
      override fun updateToolTipText() {
        HelpTooltip.dispose(this)
        HelpTooltip()
          .setTitle(presentation.text)
          .setDescription(presentation.description)
          .installOn(this)
      }

      override fun getInsets(): Insets = JBUI.emptyInsets()
      override fun getInsets(insets: Insets?): Insets {
        val i = getInsets()
        if (insets != null) {
          insets.set(i.top, i.left, i.bottom, i.right)
          return insets
        }
        return i
      }

      override fun iconTextSpace() = 0
      override fun getMargins(): Insets = JBUI.insets(2, 0, 2, 2)
    }.apply {
      border = JBUI.Borders.empty()
    }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

/**
 * [AnAction] that performs a Save All and manually triggers Live Edit.
 */
internal class ManualLiveEditAction : AnAction("Refresh") {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    // Since there's no easy way to trigger a keyboard shortcut short of simulating AWT events,
    // we instead perform Save All followed by a manual Live Edit trigger.
    invokeActionNow(e, ActionManager.getInstance().getAction(LiveEditService.PIGGYBACK_ACTION_ID))
    LiveEditAnActionListener.triggerLiveEdit(project)
  }
}

internal class ShowLogcatAction : AnAction("Show Logcat") {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ToolWindowManager.getInstance(project).getToolWindow(AndroidLogcatToolWindowFactory.getToolWindowId())?.activate {}
  }
}

private fun invokeActionNow(parentEvent: AnActionEvent, action: AnAction) {
  ActionManager.getInstance().tryToExecute(action, parentEvent.inputEvent, parentEvent.inputEvent?.component, parentEvent.place, true)
}

private class ConfigureLiveEditActionOption(text: String, val setSelected: () -> Unit, val getSelected: () -> Boolean) : AnAction(text) {
  override fun actionPerformed(e: AnActionEvent) {
    setSelected.invoke()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.icon = if (getSelected()) StudioIcons.Common.CHECKED else EmptyIcon.ICON_13
  }

  fun getSelected() : Boolean {
    return getSelected.invoke()
  }
}

internal class ConfigureLiveEditAction : DefaultActionGroup(), DataProvider {
  lateinit var parentDisposable: Disposable
  lateinit var parentComponent: JComponent

  init {
    val config = LiveEditApplicationConfiguration.getInstance()
    templatePresentation.isPopupGroup = true
    templatePresentation.isPerformGroup = true
    add(ConfigureLiveEditActionOption(
      "Push Edits Automatically",
      {
        config.leTriggerMode = LE_TRIGGER_AUTOMATIC
        config.mode = LIVE_EDIT
      },
      {
        config.mode == LIVE_EDIT && config.leTriggerMode == LE_TRIGGER_AUTOMATIC
      })
    )
    add(ConfigureLiveEditActionOption(
      "Push Edits Manually",
      {
        config.leTriggerMode = LE_TRIGGER_MANUAL
        config.mode = LIVE_EDIT
      },
      {
        config.mode == LIVE_EDIT && config.leTriggerMode == LE_TRIGGER_MANUAL
      })
    )
    addSeparator()
    add(ConfigureLiveEditActionOption(
      "Disable Live Edit",
      { config.mode = DISABLED },
      { config.mode == DISABLED }))
  }

  override fun actionPerformed(e: AnActionEvent) {
    JBPopupFactory.getInstance()
      .createActionGroupPopup(
        null,
        this,
        e.dataContext,
        false,
        true,
        true,
        // Do a delayed dispose because disposing the parent popup directly here would result in the invoked action being hidden,
        // and the ActionUtil won't be able to invoke it. There is a flag available to avoid this, but it is internal.
        { parentDisposable.let { ApplicationManager.getApplication().invokeLater { Disposer.dispose (it) } } },
        4,
        { action -> (action as ConfigureLiveEditActionOption).getSelected() }
      )
      .show(RelativePoint(parentComponent, Point(0, parentComponent.height + JBUIScale.scale(4))))
  }

  override fun getData(dataId: String): Any? {
    return if (POPUP_ACTION.`is`(dataId)) true else null
  }
}
