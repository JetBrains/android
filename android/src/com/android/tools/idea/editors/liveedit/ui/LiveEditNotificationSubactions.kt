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

import com.android.tools.adtui.compose.REFRESH_BUTTON
import com.android.tools.idea.editors.literals.LiveEditAnActionListener
import com.android.tools.idea.editors.literals.LiveEditService
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.logcat.AndroidLogcatToolWindowFactory
import com.android.tools.idea.run.deployment.liveedit.LiveEditBundle
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ui.JBUI
import org.jetbrains.android.util.AndroidBundle
import java.awt.Dimension
import java.awt.Insets

const val MANUAL_LIVE_EDIT_ACTION_ID = "Compose.Live.Edit.ManualLiveEdit"
const val SHOW_LOGCAT_ACTION_ID = "Compose.Live.Edit.ShowLogcat"

/**
 * [AnAction] that opens the Live Edit settings page for the user to enable/disable live edit.
 */
internal class ToggleLiveEditStatusAction: AnAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.text = if (LiveEditApplicationConfiguration.getInstance().isLiveEdit) {
      AndroidBundle.message("live.edit.action.disable.title")
    } else {
      AndroidBundle.message("live.edit.action.enable.title")
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    ShowSettingsUtil.getInstance().showSettingsDialog(e.project, LiveEditConfigurable::class.java)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}

/**
 * [AnAction] that triggers a compilation of the current module. The build will automatically
 * trigger a refresh of the surface. The action visibility is controlled by
 * [LiveEditStatus.hasRefreshIcon]
 */
internal class ForceCompileAndRefreshActionForNotification :
  AnAction(
    "",
    LiveEditBundle.message("le.build.redeploy.description"),
    REFRESH_BUTTON
  ), CustomComponentAction {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val status = getStatusInfo(project, e.dataContext)
    when {
      status == LiveEditStatus.OutOfDate -> {
        invokeActionNow(e, ManualLiveEditAction())
      }
      status.unrecoverable() -> ActionUtil.getAction("Run")?.let {
        ActionUtil.invokeAction(it, e.dataContext, e.place, e.inputEvent, null)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val status = getStatusInfo(project, e.dataContext).let {
      e.presentation.isEnabledAndVisible = it.hasRefreshIcon
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
    return ActionUpdateThread.EDT
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
