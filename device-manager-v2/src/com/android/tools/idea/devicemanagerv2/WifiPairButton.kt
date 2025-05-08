/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.devicemanagerv2

import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.PairDeviceAction
import com.android.tools.adtui.categorytable.IconButton
import com.intellij.openapi.application.EDT
import icons.StudioIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WifiPairButton(handle: DeviceHandle, wifiPairAction: PairDeviceAction) :
  IconButton(StudioIcons.Avd.PAIR_OVER_WIFI) {

  init {
    addActionListener { handle.scope.launch(Dispatchers.EDT) { wifiPairAction.pair() } }
  }
}
