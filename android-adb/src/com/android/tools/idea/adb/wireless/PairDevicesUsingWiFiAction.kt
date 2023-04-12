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
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import icons.StudioIcons

/** The action to show the [WiFiPairingDialog] window. */
class PairDevicesUsingWiFiAction : AnAction(StudioIcons.Avd.PAIR_OVER_WIFI) {
  @UiThread
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = false
    val project = e.project ?: return
    e.presentation.isEnabledAndVisible = PairDevicesUsingWiFiService.getInstance(project).isFeatureEnabled
  }

  @UiThread
  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    if (!PairDevicesUsingWiFiService.getInstance(project).isFeatureEnabled) {
      return;
    }

    val controller = PairDevicesUsingWiFiService.getInstance(project).createPairingDialogController()
    controller.showDialog()
  }

  companion object {
    const val ID = "Android.AdbDevicePairing"
  }
}
