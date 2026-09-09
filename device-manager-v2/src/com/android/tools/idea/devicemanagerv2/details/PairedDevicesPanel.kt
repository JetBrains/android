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
import com.android.sdklib.deviceprovisioner.SetChange
import com.android.sdklib.deviceprovisioner.trackSetChanges
import com.android.tools.adtui.categorytable.RowKey
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.idea.devicemanagerv2.PairingStatus
import com.android.tools.idea.wearpairing.WearPairingManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.JBMenuItem
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import javax.swing.Box
import javax.swing.JPanel
import kotlin.coroutines.CoroutineContext
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

/** Launches a coroutine for every DeviceHandle that arrives on the devicesFlow. Cancels it when the device is removed from the flow. */
private suspend fun trackDevices(devicesFlow: Flow<List<DeviceHandle>>, tracker: suspend (DeviceHandle) -> Unit) = coroutineScope {
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
  private val pairingDelegates: List<PairingDelegate>,
  scope: CoroutineScope,
  private val uiContext: CoroutineContext,
  val handle: DeviceHandle,
) : JPanel() {

  val addButton =
    CommonButton(AllIcons.General.Add).also { button ->
      button.toolTipText = "Add"
      button.addActionListener {
        val supportedDelegates = pairingDelegates.filter { it.isPairDeviceWizardSupported(handle) }
        when (supportedDelegates.size) {
          0 -> {}
          1 -> scope.launch(uiContext) { supportedDelegates.single().showPairDeviceWizard(this@PairedDevicesPanel, handle) }
          else -> {
            val popup = JBPopupMenu()
            for (delegate in supportedDelegates) {
              val menuItem =
                JBMenuItem(delegate.addMenuItemTitle).apply {
                  addActionListener { scope.launch(uiContext) { delegate.showPairDeviceWizard(this@PairedDevicesPanel, handle) } }
                }
              popup.add(menuItem)
            }
            popup.show(button, 0, button.height)
          }
        }
      }
    }

  val removeButton =
    CommonButton(AllIcons.General.Remove).also {
      it.toolTipText = "Remove"
      it.isEnabled = false
      it.addActionListener { removeSelectedPairing() }
    }

  val pairingsTable = PairedDevicesTable.create(uiContext)
  val scrollPane = JBScrollPane(pairingsTable)

  init {
    layout = BorderLayout()
    pairingsTable.addToScrollPane(scrollPane)
    add(
      Box.createHorizontalBox().apply {
        add(addButton)
        add(removeButton)
      },
      BorderLayout.NORTH,
    )
    add(scrollPane)

    scope.launch(uiContext) {
      handle.stateFlow.collect {
        val supportedCount = pairingDelegates.count { delegate -> delegate.isPairDeviceWizardSupported(handle) }
        addButton.isEnabled = supportedCount > 0
      }
    }

    scope.launch(uiContext) {
      pairingsTable.selection.asFlow().collect { selectedKeys ->
        val pairedDevice = (selectedKeys.firstOrNull() as? RowKey.ValueRowKey)?.key as? DeviceHandle
        val delegate = pairedDevice?.let { getDelegateForPairedDevice(it) }
        val presentation = if (pairedDevice != null && delegate != null) delegate.getRemovePresentation(handle, pairedDevice) else null
        removeButton.isEnabled = presentation?.isEnabled == true
      }
    }
  }

  private fun getDelegateForPairedDevice(pairedDevice: DeviceHandle): PairingDelegate? = pairingDelegates.find {
    it.isDelegateForDevice(handle, pairedDevice)
  }

  fun updatePairedDeviceData(pairedDeviceData: PairedDeviceData) {
    when (pairedDeviceData.pairingState) {
      WearPairingManager.PairingState.UNKNOWN -> pairingsTable.removeRowByKey(pairedDeviceData.handle)
      else -> pairingsTable.addOrUpdateRow(pairedDeviceData)
    }
  }

  fun removeDevice(handle: DeviceHandle) {
    pairingsTable.removeRowByKey(handle)
  }

  fun removeSelectedPairing() {
    val selected = pairingsTable.selection.selectedKeys().firstOrNull() ?: return
    // We don't enable grouping so this will always be a ValueRowKey
    val pairedDevice = ((selected as? RowKey.ValueRowKey)?.key as? DeviceHandle) ?: return
    val delegate = getDelegateForPairedDevice(pairedDevice) ?: return

    val presentation = delegate.getRemovePresentation(handle, pairedDevice)
    if (!presentation.isEnabled) return

    val disconnect =
      MessageDialogBuilder.okCancel(presentation.confirmationTitle, presentation.confirmationMessage)
        .asWarning()
        .yesText("Disconnect")
        .ask(this)

    if (disconnect) {
      handle.scope.launch { delegate.removeDevice(this@PairedDevicesPanel, handle, pairedDevice) }
    }
  }

  /** Updates the PairedDevicesPanel based on the provided flows. */
  private suspend fun trackPairedDevices(
    devicesFlow: Flow<List<DeviceHandle>>,
    combinedPairedDevicesFlow: Flow<Map<String, List<PairingStatus>>>,
  ) {
    trackDevices(devicesFlow) { device ->
      try {
        combinedPairedDevicesFlow
          .map { statusMap ->
            val delegate = pairingDelegates.find { it.isDelegateForDevice(handle, device) }
            val subjectId = delegate?.getPairingId(handle)
            val targetId = delegate?.getPairingId(device)
            if (subjectId != null && targetId != null) {
              statusMap[subjectId]?.find { it.id == targetId }
            } else null
          }
          .distinctUntilChanged()
          .combine(device.stateFlow) { pairingStatus, deviceState ->
            PairedDeviceData.create(device, deviceState, pairingStatus?.state ?: WearPairingManager.PairingState.UNKNOWN)
          }
          .distinctUntilChanged()
          .collect { withContext(uiContext) { updatePairedDeviceData(it) } }
      } finally {
        withContext(NonCancellable + uiContext) { removeDevice(device) }
      }
    }
  }

  companion object {
    fun create(
      pairingDelegates: List<PairingDelegate>,
      scope: CoroutineScope,
      uiContext: CoroutineContext,
      handle: DeviceHandle,
      devicesFlow: Flow<List<DeviceHandle>>,
    ): PairedDevicesPanel {
      val flows = pairingDelegates.map { it.pairedDevicesFlow(handle, devicesFlow) }
      val combinedFlow: Flow<Map<String, List<PairingStatus>>> =
        combine(flows) { maps ->
          buildMap {
            for (map in maps) {
              for ((key, statusList) in map) {
                val existing = get(key)
                if (existing == null) {
                  put(key, statusList)
                } else {
                  put(key, existing + statusList)
                }
              }
            }
          }
        }

      return PairedDevicesPanel(pairingDelegates, scope, uiContext, handle).also {
        scope.launch { it.trackPairedDevices(devicesFlow, combinedFlow) }
      }
    }
  }
}
