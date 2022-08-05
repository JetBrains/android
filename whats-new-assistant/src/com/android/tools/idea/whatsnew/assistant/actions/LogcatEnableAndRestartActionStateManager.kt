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
package com.android.tools.idea.whatsnew.assistant.actions

import com.android.tools.idea.assistant.AssistActionState
import com.android.tools.idea.assistant.AssistActionStateManager
import com.android.tools.idea.assistant.datamodel.ActionData
import com.android.tools.idea.assistant.view.StatefulButtonMessage
import com.android.tools.idea.assistant.view.UIUtils
import com.android.tools.idea.logcat.LogcatExperimentalSettings
import com.android.tools.idea.whatsnew.assistant.actions.LogcatEnableAndRestartActionStateManager.ActionState.ALREADY_ENABLED
import com.android.tools.idea.whatsnew.assistant.actions.LogcatEnableAndRestartActionStateManager.ActionState.DISABLED
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import java.awt.Color
import javax.swing.Icon

/**
 * Manages the state of [LogcatEnableAndRestartAction]
 *
 * If the new Logcat is already enabled, the action is disabled with a message.
 */
internal class LogcatEnableAndRestartActionStateManager : AssistActionStateManager() {
  override fun getId() = LogcatEnableAndRestartAction.ACTION_KEY

  override fun init(project: Project, actionData: ActionData) {}

  override fun getState(project: Project, actionData: ActionData): AssistActionState =
    if (LogcatExperimentalSettings.getInstance().logcatV2Enabled) ALREADY_ENABLED else DISABLED

  override fun getStateDisplay(project: Project, actionData: ActionData, message: String?): StatefulButtonMessage {
    val enabled = LogcatExperimentalSettings.getInstance().logcatV2Enabled
    return if (enabled) StatefulButtonMessage("The new Logcat is already enabled.", ALREADY_ENABLED)
    else StatefulButtonMessage("The new Logcat is already enabled.", DISABLED)
  }

  private enum class ActionState(
    private val buttonActive: Boolean,
    private val messageVisible: Boolean,
    private val messageIcon: Icon?
  ) : AssistActionState {
    ALREADY_ENABLED(false, true, AllIcons.RunConfigurations.TestPassed),
    DISABLED(true, false, null),
    ;

    override fun isButtonVisible(): Boolean = true

    override fun isButtonEnabled(): Boolean = buttonActive

    override fun isMessageVisible(): Boolean = messageVisible

    override fun getIcon(): Icon? = messageIcon

    override fun getForeground(): Color = UIUtils.getSuccessColor()
  }

}