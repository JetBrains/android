/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.stats

import com.android.tools.idea.actions.SendFeedbackAction
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.Consumer
import icons.StudioIcons
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent

class SendFeedbackPanel : StatusBarWidget.Multiframe, StatusBarWidget.IconPresentation {
  private var myStatusBar: StatusBar? = null

  private val project: Project?
    get() = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myStatusBar as JComponent?))

  override fun ID(): String = "SendFeedbackPanel"
  override fun getPresentation() = this
  override fun install(statusBar: StatusBar) {
    myStatusBar = statusBar
  }

  override fun copy(): StatusBarWidget = SendFeedbackPanel()
  override fun getIcon(): Icon = StudioIcons.Shell.Telemetry.SEND_FEEDBACK
  override fun getTooltipText(): String = "Send feedback to Google"

  override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { _ ->
    SendFeedbackAction.submit(project, "Source: send_feedback_icon")
  }
}
