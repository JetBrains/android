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

import com.android.tools.idea.wearpairing.WearDevicePairingWizard
import com.android.tools.idea.wearpairing.isWearOrPhone
import icons.StudioIcons
import java.awt.event.ActionEvent

internal class PairDeviceAction(avdInfoProvider: AvdInfoProvider) :
  AvdUiAction(avdInfoProvider, "Pair device", "Wear OS virtual device pairing assistant",
              StudioIcons.LayoutEditor.Toolbar.INSERT_HORIZ_CHAIN) {

  override fun actionPerformed(actionEvent: ActionEvent) {
    val project = myAvdInfoProvider.project ?: return
    // TODO: Propagate deviceID and implement single panel changes
    WearDevicePairingWizard().show(project)
  }

  override fun isEnabled(): Boolean {
    return avdInfo?.isWearOrPhone() ?: false
  }
}
