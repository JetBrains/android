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
package com.android.tools.idea.run.deployment.selector

import com.android.tools.idea.concurrency.createCoroutineScope
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import kotlin.collections.map
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The central coordination point between the UI, the DevicesService, and the persistent state.
 *
 * This class accepts updates to the selection based on user actions and persists them via SelectedTargetStateService.
 *
 * It receives device list updates from the DevicesService, adjusts the selection based on it, and supplies a consistent view of the
 * available and selected devices to other components in the DevicesAndTargets class. Note that we can only persist TargetIds; returning a
 * Target based on a persisted TargetId requires resolving it to an available Device.
 *
 * The actual selected device(s) are computed based on the most recent user input and the current device state. Note that, perhaps
 * surprisingly, a newly connected device takes precedence over any device that was previously explicitly selected prior to the connection
 * time. However, these devices are *not* persisted in SelectedTargetStateService -- only user selections are stored.
 */
@Service(Service.Level.PROJECT)
class DevicesSelectedService
@VisibleForTesting
@NonInjectable
internal constructor(
  private val runConfigurationFlow: Flow<RunnerAndConfigurationSettings?>,
  private val selectedTargetStateService: SelectedTargetStateService,
  private val devicesFlow: Flow<List<DeploymentTargetDevice>>,
  private val clock: Clock,
  coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : Disposable {
  private val coroutineScope = createCoroutineScope(coroutineContext)

  @Suppress("unused")
  private constructor(
    project: Project
  ) : this(
    runConfigurationFlow(project),
    project.service<SelectedTargetStateService>(),
    project.service<DeploymentTargetDevicesService>().loadedDevices,
    Clock.System,
  )

  override fun dispose() {}

  private val messageChannel = Channel<DeviceSelectionMessage>(4)

  /**
   * The core logic of this class: an actor flow which processes events serially, updates its internal state, and emits the resulting device
   * and target selection. This flow emits additional internal state used only by [getTargetsSelectedWithDialog].
   */
  private val internalDevicesAndTargetsFlow: StateFlow<Pair<SelectionState, DevicesAndTargets>> =
    flow {
        var currentSelectionState = SelectionState()
        var presentDevices: List<DeploymentTargetDevice> = emptyList()
        var selectedTargets: List<DeploymentTarget> = emptyList()
        for (message in messageChannel) {
          when (message) {
            is RunConfigUpdate -> {
              if (message.runConfig != null) {
                val savedSelectionState = selectedTargetStateService.getState(message.runConfig)
                if (savedSelectionState.isEmpty()) {
                  // This is a new run config with no saved selection. Assign the existing selection to the run config.
                  currentSelectionState = currentSelectionState.copy(runConfigName = message.runConfig.name)
                  selectedTargetStateService.updateState(currentSelectionState)
                } else {
                  currentSelectionState = savedSelectionState
                }
              }
            }
            is DeviceListUpdate -> {
              presentDevices = message.devices.sortedWith(DeviceComparator)
            }
            is UserComboboxSelection -> {
              currentSelectionState =
                currentSelectionState.copy(
                  selectionMode = SelectionMode.DROPDOWN,
                  dropdownSelection = DropdownSelection(target = message.target.id, timestamp = clock.now()),
                )
              selectedTargetStateService.updateState(currentSelectionState)
            }
            is UserDialogSelection -> {
              currentSelectionState =
                currentSelectionState.copy(
                  // Update the dialog selection, but if nothing is selected in the dialog, set the mode to
                  // dropdown.
                  dialogSelection = DialogSelection(targets = message.targets.map { it.id }),
                  selectionMode = if (message.targets.isEmpty()) SelectionMode.DROPDOWN else SelectionMode.DIALOG,
                )
              selectedTargetStateService.updateState(currentSelectionState)
            }
          }

          // Selection or devices updated; now resolve the selection against the present devices

          if (currentSelectionState.selectionMode == SelectionMode.DIALOG) {
            selectedTargets = currentSelectionState.dialogSelection.targets.mapNotNull { it.resolve(presentDevices) }
            if (selectedTargets.isEmpty()) {
              // When none of the selected devices are present, we switch the persisted selection from multiple to single selection.
              // This is longstanding behavior, though questionable.
              currentSelectionState = currentSelectionState.copy(selectionMode = SelectionMode.DROPDOWN)
              selectedTargetStateService.updateState(currentSelectionState)
            }
          }
          // We may have just changed the mode in the previous if statement
          if (currentSelectionState.selectionMode == SelectionMode.DROPDOWN) {
            selectedTargets =
              listOfNotNull(
                updateSingleSelection(
                  presentDevices,
                  currentSelectionState.dropdownSelection?.target,
                  currentSelectionState.dropdownSelection?.timestamp,
                )
              )
          }
          emit(
            Pair(
              currentSelectionState,
              DevicesAndTargets(presentDevices, currentSelectionState.selectionMode == SelectionMode.DIALOG, selectedTargets),
            )
          )
        }
      }
      .stateIn(coroutineScope, SharingStarted.Eagerly, Pair(SelectionState(), DevicesAndTargets(emptyList(), false, emptyList())))

  /**
   * The primary output of this class, which is the result of combining the current set of devices and the persisted selection to determine
   * a set of selected targets.
   */
  internal val devicesAndTargetsFlow: StateFlow<DevicesAndTargets> =
    internalDevicesAndTargetsFlow
      .map { it.second }
      // Note that nothing collects this flow at present, so it must be eager for it to be updated
      .stateIn(coroutineScope, SharingStarted.Eagerly, DevicesAndTargets(emptyList(), false, emptyList()))

  init {
    coroutineScope.launch { runConfigurationFlow.collect { messageChannel.send(RunConfigUpdate(it?.configuration)) } }
    coroutineScope.launch { devicesFlow.collect { messageChannel.send(DeviceListUpdate(it)) } }
    coroutineScope.launch {
      devicesAndTargetsFlow.map { it.selectedTargets }.distinctUntilChanged().collect { ActivityTracker.getInstance().inc() }
    }
  }

  internal val devicesAndTargets: DevicesAndTargets
    get() = devicesAndTargetsFlow.firstValue()

  /** Given that we are in single-device mode, with the given devices present, determine the device to select. */
  private fun updateSingleSelection(
    presentDevices: List<DeploymentTargetDevice>,
    lastSelectedTargetId: TargetId?,
    selectionTime: Instant?,
  ): DeploymentTarget? {
    val lastSelectedTarget = lastSelectedTargetId?.resolve(presentDevices)
    // This relies on presentDevices being sorted by connection time (see DeviceComparator)
    val latestConnectedDevice = presentDevices.firstOrNull()?.takeIf { it.connectionTime != null }

    return if (latestConnectedDevice != null && lastSelectedTarget != null) {
      val connectionTime = latestConnectedDevice.connectionTime!!
      when {
        lastSelectedTarget.device == latestConnectedDevice -> lastSelectedTarget
        selectionTime == null -> latestConnectedDevice.defaultTarget
        selectionTime > connectionTime -> lastSelectedTarget
        else -> latestConnectedDevice.defaultTarget
      }
    } else lastSelectedTarget ?: latestConnectedDevice?.defaultTarget ?: presentDevices.firstOrNull()?.defaultTarget
  }

  fun getSelectedTargets(): List<DeploymentTarget> = devicesAndTargets.selectedTargets

  fun setTargetSelectedWithComboBox(targetSelectedWithComboBox: DeploymentTarget) {
    messageChannel.trySendBlocking(UserComboboxSelection(targetSelectedWithComboBox))
  }

  fun getTargetsSelectedWithDialog(): List<DeploymentTarget> {
    return internalDevicesAndTargetsFlow.value.first.dialogSelection.targets.mapNotNull { it.resolve(devicesAndTargets.allDevices) }
  }

  /** Updates the currently-persisted selected device state with the new set of selected targets. */
  fun setTargetsSelectedWithDialog(targetsSelectedWithDialog: List<DeploymentTarget>) {
    messageChannel.trySendBlocking(UserDialogSelection(targetsSelectedWithDialog))
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): DevicesSelectedService {
      return project.service<DevicesSelectedService>()
    }
  }
}

private fun SelectionState.isEmpty() = dropdownSelection == null && dialogSelection.targets.isEmpty()

internal data class DevicesAndTargets(
  val allDevices: List<DeploymentTargetDevice>,
  val isMultipleSelectionMode: Boolean,
  val selectedTargets: List<DeploymentTarget>,
)

/**
 * Given a serialized target ID and a list of present devices, we want to find the best acceptable match. Note that the ID and the devices
 * may both either be templates or handles.
 *
 * If the serialized ID is a template, our order of preference is:
 * 1. An active device based on the template
 * 2. The template itself
 *
 * If the serialized ID is a device, our order of preference is:
 * 1. The device with that ID
 * 2. Another active device that came from the same template
 * 3. The template that it came from
 */
internal fun TargetId.resolve(devices: List<DeploymentTargetDevice>): DeploymentTarget? {
  val device =
    if (deviceId.isTemplate)
      devices.firstOrNull { !it.id.isTemplate && it.templateId == templateId } ?: devices.firstOrNull { it.id == deviceId }
    else
      devices.firstOrNull { it.id == deviceId }
        ?: templateId?.let {
          devices.firstOrNull { !it.id.isTemplate && it.templateId == templateId } ?: devices.firstOrNull { it.templateId == templateId }
        }
  return device?.let { DeploymentTarget(it, bootOption) }
}

private sealed class DeviceSelectionMessage

private class RunConfigUpdate(val runConfig: RunConfiguration?) : DeviceSelectionMessage()

private class DeviceListUpdate(val devices: List<DeploymentTargetDevice>) : DeviceSelectionMessage()

private class UserComboboxSelection(val target: DeploymentTarget) : DeviceSelectionMessage()

private class UserDialogSelection(val targets: List<DeploymentTarget>) : DeviceSelectionMessage()
