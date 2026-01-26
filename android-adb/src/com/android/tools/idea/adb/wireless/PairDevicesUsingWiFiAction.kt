/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.adb.wireless

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.adb.wireless.v2.ui.WifiAvailableDevicesDialog
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import icons.StudioIcons

/** The action to show the [WiFiPairingDialog] window. */
class PairDevicesUsingWiFiAction : DumbAwareAction(StudioIcons.Avd.PAIR_OVER_WIFI) {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  @UiThread
  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    if (!StudioFlags.ADB_WIFI_V2_DIALOG.get()) {
      PairDevicesUsingWiFiService.getInstance(project).createPairingDialogController().showDialog()
      return
    }
    val wifiPairingService =
      WiFiPairingServiceImpl(RandomProvider(), AdbServiceWrapperAdbLibImpl(project))
    WifiAvailableDevicesDialog(project, wifiPairingService).showDialog()
  }

  companion object {
    const val ID = "Android.AdbDevicePairing"
  }
}
