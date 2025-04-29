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

import com.android.adblib.AdbFeatures.TRACK_MDNS_SERVICE
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.adb.wireless.v2.ui.WifiAvailableDevicesDialog
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import icons.StudioIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The action to show the [WiFiPairingDialog] window. */
class PairDevicesUsingWiFiAction : AnAction(StudioIcons.Avd.PAIR_OVER_WIFI) {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  @UiThread
  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    if (!StudioFlags.WIFI_V2_ENABLED.get()) {
      PairDevicesUsingWiFiService.getInstance(project).createPairingDialogController().showDialog()
      return
    }
    project.coroutineScope.launch(Dispatchers.Default) {
      val hostFeatures = AdbLibService.getSession(project).hostServices.hostFeatures()
      withContext(Dispatchers.EDT) {
        if (hostFeatures.contains(TRACK_MDNS_SERVICE)) {
          WifiAvailableDevicesDialog(project).showDialog()
        } else {
          PairDevicesUsingWiFiService.getInstance(project)
            .createPairingDialogController()
            .showDialog()
        }
      }
    }
  }

  companion object {
    const val ID = "Android.AdbDevicePairing"
  }
}
