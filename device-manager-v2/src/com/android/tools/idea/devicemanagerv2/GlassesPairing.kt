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
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.adtui.actions.componentToRestoreFocusTo
import com.android.tools.idea.deviceprovisioner.GlassesInteractivePairableDeviceHandle
import com.android.tools.idea.deviceprovisioner.deviceHandle
import com.android.tools.idea.deviceprovisioner.launchCatchingDeviceActionException
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.wearpairing.WearPairingManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Launches the Glasses Pairing wizard. */
class PairGlassesAction : DumbAwareAction("Pair Glasses") {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    if (StudioFlags.AI_GLASSES_PHONE_EMULATOR_PAIRING_WIZARD_ENABLED.get()) {
      val handle = e.deviceHandle() as? GlassesInteractivePairableDeviceHandle
      if (handle?.state?.properties?.deviceType == DeviceType.AI_GLASSES && handle.state.properties.pairedPhoneId == null) {
        e.presentation.isVisible = true
        e.presentation.isEnabled = handle.isPairGlassesEnabled()
        return
      }
    }
    e.presentation.isEnabledAndVisible = false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val deviceHandle = e.deviceHandle() as? GlassesInteractivePairableDeviceHandle ?: return

    deviceHandle.launchCatchingDeviceActionException(project = e.project) {
      deviceHandle.pairGlasses(e.componentToRestoreFocusTo(), e.project)
    }
  }
}

/** Unpairs the glasses from a companion device. */
class UnpairGlassesAction : DumbAwareAction("Unpair Glasses") {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    if (StudioFlags.AI_GLASSES_PAIRING_RECONCILIATION_ENABLED.get()) {
      val handle = e.deviceHandle() as? GlassesInteractivePairableDeviceHandle
      if (handle?.state?.properties?.deviceType == DeviceType.AI_GLASSES && handle.state.properties.pairedPhoneId != null) {
        e.presentation.isVisible = true
        e.presentation.isEnabled = handle.isUnpairGlassesEnabled()
        return
      }
    }
    e.presentation.isEnabledAndVisible = false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val deviceHandle = e.deviceHandle() as? GlassesInteractivePairableDeviceHandle ?: return

    deviceHandle.launchCatchingDeviceActionException(project = e.project) { deviceHandle.unpairGlasses(e.componentToRestoreFocusTo()) }
  }
}

private fun createPairingStatus(pairedId: DeviceId, allDevices: List<DeviceHandle>): PairingStatus {
  val pairedHandle = allDevices.find { it.id == pairedId }
  val isOnline = pairedHandle?.state?.isOnline() == true
  return PairingStatus(
    id = pairedId.toString(),
    displayName = pairedHandle?.state?.properties?.title ?: "Unknown Device",
    state = if (isOnline) WearPairingManager.PairingState.CONNECTED else WearPairingManager.PairingState.OFFLINE,
  )
}

private fun DeviceHandle.getGlassesPairings(allDevices: List<DeviceHandle>): List<PairingStatus> {
  val props = state.properties
  return when (props.deviceType) {
    DeviceType.AI_GLASSES -> {
      props.pairedPhoneId?.let { phoneId -> listOf(createPairingStatus(phoneId, allDevices)) } ?: emptyList()
    }
    DeviceType.HANDHELD -> {
      val directPairings = props.pairedGlassesInfos.map { glassesInfo -> createPairingStatus(glassesInfo.id, allDevices) }
      val indirectPairings =
        allDevices
          .filter { it.state.properties.deviceType == DeviceType.AI_GLASSES && it.state.properties.pairedPhoneId == id }
          .map { glasses -> createPairingStatus(glasses.id, allDevices) }
      (directPairings + indirectPairings).distinctBy { it.id }
    }
    else -> emptyList()
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<List<DeviceHandle>>.glassesPairedDevicesFlow(): Flow<Map<String, List<PairingStatus>>> = flatMapLatest { allDevices ->
  if (allDevices.isEmpty()) {
    flowOf(emptyMap())
  } else {
    combine(allDevices.map { it.stateFlow }) { _ ->
      allDevices.associate { device -> device.id.toString() to device.getGlassesPairings(allDevices) }.filterValues { it.isNotEmpty() }
    }
  }
}
