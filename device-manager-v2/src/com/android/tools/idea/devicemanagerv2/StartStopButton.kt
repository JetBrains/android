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

import com.android.sdklib.deviceprovisioner.ActivationAction
import com.android.sdklib.deviceprovisioner.DeactivationAction
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceState.Disconnected
import com.android.sdklib.deviceprovisioner.RepairDeviceAction
import com.android.tools.adtui.categorytable.IconButton
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.deviceprovisioner.runCatchingDeviceActionException
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind.VIRTUAL_LAUNCH_ACTION
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind.VIRTUAL_STOP_ACTION
import icons.StudioIcons
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A button for starting and stopping a device. Requires an ActivationAction and a
 * DeactivationAction.
 */
internal class StartStopButton(
  private val handle: DeviceHandle,
  activationAction: ActivationAction,
  deactivationAction: DeactivationAction,
  repairDeviceAction: RepairDeviceAction?,
) : IconButton(StudioIcons.Avd.RUN) {
  init {
    val activationPresentation = activationAction.presentation
    val deactivationPresentation = deactivationAction.presentation
    val repairPresentation = repairDeviceAction?.presentation

    addActionListener {
      val project = projectFromComponentContext(this@StartStopButton)
      when (baseIcon) {
        activationPresentation.value.icon ->
          handle.scope.launch {
            if (handle.state.properties.isVirtual == true) {
              DeviceManagerUsageTracker.logDeviceManagerEvent(VIRTUAL_LAUNCH_ACTION)
            }
            runCatchingDeviceActionException(project, handle.state.properties.title) {
              activationAction.activate()
            }
          }
        deactivationPresentation.value.icon ->
          handle.scope.launch {
            if (handle.state.properties.isVirtual == true) {
              DeviceManagerUsageTracker.logDeviceManagerEvent(VIRTUAL_STOP_ACTION)
            }
            runCatchingDeviceActionException(project, handle.state.properties.title) {
              deactivationAction.deactivate()
            }
          }
        repairPresentation?.value?.icon -> {
          handle.scope.launch {
            runCatchingDeviceActionException(project, handle.state.properties.title) {
              repairDeviceAction?.repair()
            }
          }
        }
        else -> {}
      }
    }

    handle.scope.launch {
      handle.stateFlow.collectLatest { state ->
        // If only one action is enabled, show it. (This should be the usual case.) Otherwise, favor
        // the deactivation action when we're connected, and the activation action when we're
        // disconnected.
        combine(
            when {
              state.error != null && repairPresentation != null ->
                listOf(activationPresentation, repairPresentation, deactivationPresentation)
              state is Disconnected -> listOf(activationPresentation, deactivationPresentation)
              else -> listOf(deactivationPresentation, activationPresentation)
            }
          ) {
            it.firstOrNull { it.enabled } ?: it.first()
          }
          .distinctUntilChanged()
          .collect {
            withContext(uiThread) {
              toolTipText = if (it.enabled) it.label else it.detail
              baseIcon = it.icon
              isEnabled = it.enabled
            }
          }
      }
    }
  }
}
