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
package com.android.tools.idea.avdmanager

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.wearpairing.WearPairingManager
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceManagerEvent
import com.intellij.openapi.ui.Messages
import icons.StudioIcons
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.awt.event.ActionEvent

internal class UnpairDeviceAction(
  avdInfoProvider: AvdInfoProvider,
  private val logDeviceManagerEvents: Boolean = false
) : AvdUiAction(
  avdInfoProvider,
  "Unpair device",
  "Forget existing connection",
  StudioIcons.LayoutEditor.Toolbar.INSERT_HORIZ_CHAIN
) {
  override fun actionPerformed(actionEvent: ActionEvent) {
    if (logDeviceManagerEvents) {
      val deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(DeviceManagerEvent.EventKind.VIRTUAL_UNPAIR_DEVICE_ACTION)
        .build()

      val builder = AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.DEVICE_MANAGER)
        .setDeviceManagerEvent(deviceManagerEvent)

      UsageTracker.log(builder)
    }

    val avdInfo = avdInfo ?: return

    if (WearPairingManager.getPairedDevices(avdInfo.name) == null) {
      Messages.showMessageDialog(project, "Not paired yet. Please pair device first.", "Unpair Device", Messages.getInformationIcon())
    }
    else {
      GlobalScope.launch(AndroidDispatchers.ioThread) {
        WearPairingManager.removePairedDevices(avdInfo.name)
      }
    }
  }

  // TODO: Menu refreshing is not properly implemented, so we use the same logic as PairDeviceAction for now.
  // TODO: Move PairDeviceAction.Companion::isEnabled to a third class in this package and make PairDeviceAction and UnpairDeviceAction call
  //  it
  override fun isEnabled() = PairDeviceAction.isEnabled(avdInfo)
}
