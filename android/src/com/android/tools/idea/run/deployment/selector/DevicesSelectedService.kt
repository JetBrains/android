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

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * The central coordination point between the UI, the DevicesService, and the persistent state.
 *
 * This class accepts updates to the selection based on user actions and persists them via
 * SelectedTargetStateService.
 *
 * It receives device list updates from the DevicesService, adjusts the selection based on it, and
 * supplies a consistent view of the available and selected devices to other components in the
 * DevicesAndTargets class. Note that we can only persist TargetIds; returning a Target based on a
 * persisted TargetId requires resolving it to an available Device.
 *
 * The actual selected device(s) are computed based on the most recent user input and the current
 * device state. Note that, perhaps surprisingly, a newly connected device takes precedence over any
 * device that was previously explicitly selected prior to the connection time. However, these
 * devices are *not* persisted in SelectedTargetStateService -- only user selections are stored.
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
  private val coroutineScope = AndroidCoroutineScope(this, coroutineContext)

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

  /**
   * Explicit updates to the selection by [setTargetSelectedWithComboBox] or
   * [setTargetsSelectedWithDialog] are sent through this flow; the updates propagate through to the
   * [devicesAndTargetsFlow].
   */
  private val selectionStateUpdateFlow =
    MutableSharedFlow<SelectionState>(
      extraBufferCapacity = 1,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

  /**
   * The current selection state can change either because the run configuration has changed, or
   * because the user updated the selection.
   */
  private val selectionStateFlow =
    merge(
        runConfigurationFlow.map { selectedTargetStateService.getState(it?.configuration) },
        selectionStateUpdateFlow,
      )
      .stateIn(coroutineScope, SharingStarted.Eagerly, SelectionState())

  /**
   * The primary output of this class, which is the result of combining the current set of devices
   * and the persisted selection to determine a set of selected targets.
   */
  internal val devicesAndTargetsFlow =
    devicesFlow
      .combine(selectionStateFlow, ::updateState)
      .stateIn(
        coroutineScope,
        // Note that nothing collects this flow at present, so it must be eager for it to be updated
        SharingStarted.Eagerly,
        DevicesAndTargets(emptyList(), false, emptyList()),
      )

  init {
    coroutineScope.launch {
      devicesAndTargetsFlow
        .map { it.selectedTargets }
        .distinctUntilChanged()
        .collect { ActivityTracker.getInstance().inc() }
    }
  }

  internal val devicesAndTargets: DevicesAndTargets
    get() = devicesAndTargetsFlow.firstValue()

  private fun updateSelectionState(selectionState: SelectionState) {
    selectedTargetStateService.updateState(selectionState)
    selectionStateUpdateFlow.tryEmit(selectionState)
  }

  private fun updateState(
    presentDevices: List<DeploymentTargetDevice>,
    selectionState: SelectionState,
  ): DevicesAndTargets {
    val presentDevices = presentDevices.sortedWith(DeviceComparator)
    val selectedTargets: List<DeploymentTarget>
    when (selectionState.selectionMode) {
      SelectionMode.DROPDOWN -> {
        selectedTargets =
          listOfNotNull(
            updateSingleSelection(
              presentDevices,
              selectionState.dropdownSelection?.target,
              selectionState.dropdownSelection?.timestamp,
            )
          )
      }
      SelectionMode.DIALOG -> {
        selectedTargets =
          selectionState.dialogSelection.targets.mapNotNull { it.resolve(presentDevices) }
        if (selectedTargets.isEmpty()) {
          // TODO: Here, without explicit user action, we switch the mode from multiple to single
          // selection. Is this really what we want?
          val dropdownState = selectionState.copy(selectionMode = SelectionMode.DROPDOWN)
          // This doesn't take immediate effect, it has to come back around via the flows
          updateSelectionState(dropdownState)
        }
      }
    }
    return DevicesAndTargets(
      presentDevices.sortedWith(DeviceComparator),
      selectionState.selectionMode == SelectionMode.DIALOG,
      selectedTargets,
    )
  }

  /**
   * Given that we are in single-device mode, with the given devices present, determine the device
   * to select.
   */
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
    } else
      lastSelectedTarget
        ?: latestConnectedDevice?.defaultTarget
        ?: presentDevices.firstOrNull()?.defaultTarget
  }

  fun getSelectedTargets(): List<DeploymentTarget> = devicesAndTargets.selectedTargets

  fun setTargetSelectedWithComboBox(targetSelectedWithComboBox: DeploymentTarget?) {
    updateSelectionState(
      selectionStateFlow.value.copy(
        selectionMode = SelectionMode.DROPDOWN,
        dropdownSelection =
          targetSelectedWithComboBox?.let {
            DropdownSelection(target = it.id, timestamp = clock.now())
          },
      )
    )
  }

  fun getTargetsSelectedWithDialog(): List<DeploymentTarget> {
    return selectionStateFlow.value.dialogSelection.targets.mapNotNull {
      it.resolve(devicesAndTargets.allDevices)
    }
  }

  /** Updates the currently-persisted selected device state with the new set of selected targets. */
  fun setTargetsSelectedWithDialog(targetsSelectedWithDialog: List<DeploymentTarget>) {
    updateSelectionState(
      selectionStateFlow.value.copy(
        // Update the dialog selection, but if nothing is selected in the dialog, set the mode to
        // dropdown.
        dialogSelection = DialogSelection(targets = targetsSelectedWithDialog.map { it.id }),
        selectionMode =
          if (targetsSelectedWithDialog.isEmpty()) SelectionMode.DROPDOWN else SelectionMode.DIALOG,
      )
    )
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): DevicesSelectedService {
      return project.service<DevicesSelectedService>()
    }
  }
}

internal data class DevicesAndTargets(
  val allDevices: List<DeploymentTargetDevice>,
  val isMultipleSelectionMode: Boolean,
  val selectedTargets: List<DeploymentTarget>,
)

/**
 * Given a serialized target ID and a list of present devices, we want to find the best acceptable
 * match. Note that the ID and the devices may both either be templates or handles.
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
      devices.firstOrNull { !it.id.isTemplate && it.templateId == templateId }
        ?: devices.firstOrNull { it.id == deviceId }
    else
      devices.firstOrNull { it.id == deviceId }
        ?: templateId?.let {
          devices.firstOrNull { !it.id.isTemplate && it.templateId == templateId }
            ?: devices.firstOrNull { it.templateId == templateId }
        }
  return device?.let { DeploymentTarget(it, bootOption) }
}
