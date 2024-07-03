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
package com.android.tools.idea.logcat.actions

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.devices.DeviceComboBox.DeviceComboItem.DeviceItem
import com.android.tools.idea.logcat.devices.DeviceComboBox.DeviceComboItem.FileItem
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import java.nio.file.Path
import kotlin.io.path.exists

/** An action that restarts or reloads Logcat on the connected device or file. */
internal class RestartOrReloadLogcatAction :
  DumbAwareAction(
    LogcatBundle.message("logcat.restart.action.text"),
    null,
    AllIcons.Actions.Restart,
  ) {

  override fun update(e: AnActionEvent) {
    val logcatPresenter = e.getLogcatPresenter() ?: return
    val item = logcatPresenter.getSelectedItem()
    when {
      item is DeviceItem -> e.presentForDevice(logcatPresenter.getConnectedDevice() != null)
      item is FileItem && !isAutoLoadFileEnabled() -> e.presentForFile(item.path)
      else -> e.presentation.isEnabled = false
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val logcatPresenter = e.getLogcatPresenter() ?: return
    val item = logcatPresenter.getSelectedItem() ?: return
    when (item) {
      is DeviceItem -> logcatPresenter.restartLogcat()
      is FileItem -> logcatPresenter.reloadFile()
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

private fun AnActionEvent.presentForDevice(isConnected: Boolean) {
  presentation.isEnabled = isConnected
  presentation.text = LogcatBundle.message("logcat.restart.action.text")
  presentation.icon = AllIcons.Actions.Restart
}

private fun AnActionEvent.presentForFile(path: Path) {
  presentation.isEnabled = path.exists()
  presentation.text = LogcatBundle.message("logcat.reload.action.text")
  presentation.icon = AllIcons.Actions.Refresh
}

private fun isAutoLoadFileEnabled() = StudioFlags.LOGCAT_FILE_RELOAD_DELAY_MS.get() > 0
