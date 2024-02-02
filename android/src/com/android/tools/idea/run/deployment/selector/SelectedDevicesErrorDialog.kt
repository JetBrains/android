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
package com.android.tools.idea.run.deployment.selector

import com.android.tools.idea.run.LaunchCompatibility.State
import com.intellij.CommonBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBEmptyBorder
import icons.StudioIcons
import javax.swing.Action
import javax.swing.JComponent
import org.jetbrains.android.util.AndroidBundle.message

/**
 * Displays the deployment issues for each device and a warning or error icon when the user deploys
 * their app.
 *
 * In case of a warning the user can proceed with the deployment and choose not to show this dialog
 * during the current session.
 */
internal class SelectedDevicesErrorDialog
internal constructor(
  private val project: Project,
  private val devices: Iterable<DeploymentTargetDevice>,
) : DialogWrapper(project) {
  companion object {
    @JvmField
    internal val DO_NOT_SHOW_WARNING_ON_DEPLOYMENT =
      com.intellij.openapi.util.Key.create<Boolean>("do.not.show.warning.on.deployment")
  }

  private val anyDeviceHasError = devices.any { it.launchCompatibility.state == State.ERROR }

  init {
    isResizable = false
    title = if (anyDeviceHasError) message("error.level.title") else message("warning.level.title")
    if (!anyDeviceHasError) {
      setDoNotAskOption(
        object : com.intellij.openapi.ui.DoNotAskOption.Adapter() {
          override fun rememberChoice(isSelected: Boolean, exitCode: Int) =
            project.putUserData(DO_NOT_SHOW_WARNING_ON_DEPLOYMENT, isSelected)

          override fun getDoNotShowMessage() = message("do.not.ask.for.this.session")

          override fun isSelectedByDefault() =
            project.getUserData(DO_NOT_SHOW_WARNING_ON_DEPLOYMENT) == true
        }
      )
    }
    init()
  }

  override fun createActions(): Array<Action> {
    return if (!anyDeviceHasError) {
      myOKAction.putValue(Action.NAME, CommonBundle.getContinueButtonText())
      arrayOf(cancelAction, okAction)
    } else {
      myCancelAction.putValue(Action.NAME, CommonBundle.getOkButtonText())
      myCancelAction.putValue(DEFAULT_ACTION, true)
      arrayOf(cancelAction)
    }
  }

  override fun createCenterPanel(): JComponent =
    panel {
        row {
          val icon = if (anyDeviceHasError) StudioIcons.Common.ERROR else StudioIcons.Common.WARNING
          icon(IconUtil.scale(icon, null, 2.5f))
          panel {
            devices.forEach {
              row { text("${it.launchCompatibility.reason} on device ${it.name}", 50) }
            }
          }
        }
      }
      .withBorder(JBEmptyBorder(16))
}
