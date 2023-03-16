/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import icons.StudioIcons
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class StartStopButton(private val handle: DeviceHandle) : IconButton(StudioIcons.Avd.RUN) {
  init {
    addActionListener {
      when (baseIcon) {
        StudioIcons.Avd.RUN -> handle.scope.launch { handle.activationAction?.activate() }
        StudioIcons.Avd.STOP -> handle.scope.launch { handle.deactivationAction?.deactivate() }
        else -> {}
      }
    }

    handle.scope.launch(uiThread) {
      handle.stateFlow.collectLatest { state ->
        when (state) {
          is DeviceState.Disconnected -> {
            baseIcon = StudioIcons.Avd.RUN
            trackActionEnabled(handle.activationAction)
          }
          is DeviceState.Connected -> {
            baseIcon = StudioIcons.Avd.STOP
            trackActionEnabled(handle.deactivationAction)
          }
          else -> {}
        }
      }
    }
  }
}
