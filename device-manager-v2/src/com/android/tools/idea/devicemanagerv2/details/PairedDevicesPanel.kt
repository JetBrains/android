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
package com.android.tools.idea.devicemanagerv2.details

import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.SetChange
import com.android.sdklib.deviceprovisioner.trackSetChanges
import com.android.tools.adtui.categorytable.RowKey
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.idea.devicemanagerv2.DeviceManagerBundle
import com.android.tools.idea.devicemanagerv2.PairingStatus
import com.android.tools.idea.wearpairing.WearDevicePairingWizard
import com.android.tools.idea.wearpairing.WearPairingManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.ui.components.JBScrollPane
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import javax.swing.Box
import javax.swing.JPanel

/**
 * Launches a coroutine for every DeviceHandle that arrives on the devicesFlow. Cancels it when the
 * device is removed from the flow.
 */
private suspend fun trackDevices(
  devicesFlow: Flow<List<DeviceHandle>>,
  tracker: suspend (DeviceHandle) -> Unit
) = coroutineScope {
  val trackers = mutableMapOf<DeviceHandle, Job>()
  devicesFlow
    .map { it.toSet() }
    .trackSetChanges()
    .collect {
      when (it) {
        is SetChange.Add -> trackers[it.value] = launch { tracker(it.value) }
        is SetChange.Remove -> trackers.remove(it.value)?.cancel()
      }
    }
}

internal class PairedDevicesPanel
private constructor(
  private val pairingManager: PairingManager,
  scope: CoroutineScope,
  private val uiDispatcher: CoroutineDispatcher,
  val handle: DeviceHandle,
  private val subjectWearPairingId: String,
) : JPanel() {

  val addButton =
    CommonButton(AllIcons.General.Add).also {
      it.toolTipText = "Add"
      it.addActionListener { pairingManager.showPairDeviceWizard(subjectWearPairingId) }
    }

  val removeButton =
    CommonButton(AllIcons.General.Remove).also {
      it.toolTipText = "Remove"
      it.isEnabled = false
      it.addActionListener { removeSelectedPairing() }
    }

  val pairingsTable = PairedDevicesTable.create(uiDispatcher)
  val scrollPane = JBScrollPane(pairingsTable)

  init {
    layout = BorderLayout()
    pairingsTable.addToScrollPane(scrollPane)
    add(
      Box.createHorizontalBox().apply {
        add(addButton)
        add(removeButton)
      },
      BorderLayout.NORTH
    )
    add(scrollPane)

    scope.launch(uiDispatcher) {
      pairingsTable.selection.asFlow().collect { removeButton.isEnabled = it.isNotEmpty() }
    }
  }

  fun updatePairedDeviceData(pairedDeviceData: PairedDeviceData) {
    when (pairedDeviceData.state) {
      WearPairingManager.PairingState.UNKNOWN ->
        pairingsTable.removeRowByKey(pairedDeviceData.handle)
      else -> pairingsTable.addOrUpdateRow(pairedDeviceData)
    }
  }

  fun removeDevice(handle: DeviceHandle) {
    pairingsTable.removeRowByKey(handle)
  }

  fun removeSelectedPairing() {
    val selected = pairingsTable.selection.selectedKeys().firstOrNull() ?: return
    // We don't enable grouping so this will always be a ValueRowKey
    val selectedHandle = ((selected as RowKey.ValueRowKey).key as DeviceHandle)
    val (phoneHandle, wearHandle) =
      when (selectedHandle.state.properties.deviceType) {
        DeviceType.WEAR -> Pair(handle, selectedHandle)
        else -> Pair(selectedHandle, handle)
      }
    val phoneName = phoneHandle.state.properties.title
    val wearName = wearHandle.state.properties.title

    val disconnect =
      MessageDialogBuilder.okCancel(
          DeviceManagerBundle.message("pairedDevices.remove.title", wearName, phoneName),
          DeviceManagerBundle.message("pairedDevices.remove.message", wearName, phoneName),
        )
        .asWarning()
        .yesText("Disconnect")
        .ask(this)

    if (disconnect) {
      phoneHandle.scope.launch {
        pairingManager.removeDevice(
          phoneHandle.state.properties.wearPairingId ?: return@launch,
          wearHandle.state.properties.wearPairingId ?: return@launch,
        )
      }
    }
  }

  /** Updates the PairedDevicesPanel based on the provided flows. */
  private suspend fun trackPairedDevices(
    devicesFlow: Flow<List<DeviceHandle>>,
    pairedDevicesFlow: Flow<Map<String, List<PairingStatus>>>
  ) {
    trackDevices(devicesFlow) { device ->
      try {
        val pairingId = device.state.properties.wearPairingId
        pairedDevicesFlow
          .map { it[subjectWearPairingId]?.find { it.id == pairingId } }
          .distinctUntilChanged()
          .combine(device.stateFlow) { pairingStatus, deviceState ->
            PairedDeviceData.create(
              device,
              deviceState,
              pairingStatus?.state ?: WearPairingManager.PairingState.UNKNOWN
            )
          }
          .distinctUntilChanged()
          .collect { withContext(uiDispatcher) { updatePairedDeviceData(it) } }
      } catch (e: CancellationException) {
        withContext(NonCancellable + uiDispatcher) { removeDevice(device) }
      }
    }
  }

  /** Interface for injecting dependencies to PairedDevicesPanel. */
  interface PairingManager {
    fun showPairDeviceWizard(pairingId: String)

    suspend fun removeDevice(phonePairingId: String, wearPairingId: String)
  }

  class StudioPairingManager(val project: Project?) : PairingManager {
    override fun showPairDeviceWizard(pairingId: String) {
      WearDevicePairingWizard().show(project, pairingId)
    }

    override suspend fun removeDevice(phonePairingId: String, wearPairingId: String) {
      WearPairingManager.getInstance().removePairedDevices(phonePairingId, wearPairingId, true)
    }
  }

  companion object {
    fun create(
      pairingManager: PairingManager,
      scope: CoroutineScope,
      uiDispatcher: CoroutineDispatcher,
      handle: DeviceHandle,
      devicesFlow: Flow<List<DeviceHandle>>,
      pairedDevicesFlow: Flow<Map<String, List<PairingStatus>>>,
    ): PairedDevicesPanel {
      val subjectDevicePairingId = checkNotNull(handle.state.properties.wearPairingId)

      return PairedDevicesPanel(pairingManager, scope, uiDispatcher, handle, subjectDevicePairingId)
        .also { scope.launch { it.trackPairedDevices(devicesFlow, pairedDevicesFlow) } }
    }
  }
}
