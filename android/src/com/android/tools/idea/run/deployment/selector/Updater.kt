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

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.execution.common.DeployableToDevice.deploysToLocalDevice
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import icons.StudioIcons
import org.jetbrains.android.util.AndroidUtils

internal class Updater(
  private val project: Project,
  private val presentation: Presentation,
  private val place: String,
  private val devicesSelectedService: DevicesSelectedService,
  private val devices: List<Device>,
  private val configurationAndSettings: RunnerAndConfigurationSettings?,
) {
  fun update() {
    if (!AndroidUtils.hasAndroidFacets(project)) {
      presentation.setVisible(false)
      return
    }
    presentation.setVisible(true)
    updateDependingOnConfiguration()
    when (place) {
      ActionPlaces.MAIN_TOOLBAR,
      ActionPlaces.NAVIGATION_BAR_TOOLBAR ->
        if (devicesSelectedService.isMultipleDevicesSelectedInComboBox) {
          updateInToolbarForMultipleDevices()
        } else {
          updateInToolbarForSingleDevice()
        }
      else -> {
        presentation.setIcon(null)
        presentation.text = "Select Device..."
      }
    }
  }

  private fun updateDependingOnConfiguration() {
    if (configurationAndSettings == null) {
      presentation.setEnabled(false)
      presentation.setDescription("Add a run/debug configuration")
      return
    }
    val configuration = configurationAndSettings.configuration
    if (deploysToLocalDevice(configuration)) {
      presentation.setEnabled(true)
      presentation.setDescription(null as String?)
      return
    }
    presentation.setEnabled(false)
    if (IdeInfo.getInstance().isAndroidStudio) {
      presentation.setDescription(
        "Not applicable for the \"" + configuration.name + "\" configuration"
      )
    } else {
      presentation.setVisible(false)
    }
  }

  /**
   * Given that we are in the "multiple devices" selection mode, updates the set of selected devices
   * based on the currently-existing devices. For example, this might reduce the count of devices
   * when a physical device is unplugged.
   */
  private fun updateInToolbarForMultipleDevices() {
    val selectedTargets =
      devicesSelectedService.getTargetsSelectedWithDialog(devices).toMutableSet()
    val targets = devices.flatMap { it.targets }.toSet()
    if (selectedTargets.retainAll(targets)) {
      devicesSelectedService.setTargetsSelectedWithDialog(selectedTargets)
    }
    if (selectedTargets.isEmpty()) {
      // Our multiple targets all went away; revert back to single-target mode.
      devicesSelectedService.isMultipleDevicesSelectedInComboBox = false
      val selectedTarget =
        devicesSelectedService.getTargetSelectedWithComboBox(devices).orElse(null)
      devicesSelectedService.setTargetSelectedWithComboBox(selectedTarget)
      updateInToolbarForSingleDevice()
      return
    }
    presentation.setIcon(StudioIcons.DeviceExplorer.MULTIPLE_DEVICES)
    presentation.putClientProperty(DeviceAndSnapshotComboBoxAction.LAUNCH_COMPATIBILITY_KEY, null)
    presentation.setText("Multiple Devices (" + selectedTargets.size + ")")
  }

  private fun updateInToolbarForSingleDevice() {
    if (devices.isEmpty()) {
      presentation.setIcon(null)
      presentation.text = "No Devices"
      return
    }
    val target =
      devicesSelectedService.getTargetSelectedWithComboBox(devices).orElseThrow { AssertionError() }
    val key = target.deviceKey
    val device = checkNotNull(devices.find { it.key == key })
    presentation.setIcon(device.icon)
    presentation.putClientProperty(
      DeviceAndSnapshotComboBoxAction.LAUNCH_COMPATIBILITY_KEY,
      device.launchCompatibility
    )
    presentation.setText(getText(device, target), false)
  }

  /**
   * Returns the text to display in the drop down button. It usually indicates the device selected
   * by the user. If there's another device in the drop down with the same name as the selected
   * device, this method appends the selected device's key (serial number) to the text to
   * disambiguate it. If it's appropriate to display the boot option (Cold Boot, Quick Boot, the
   * name of the snapshot for a snapshot boot), this method appends it to the text. If the
   * underlying machinery has determined a reason why a device isn't valid, this method appends that
   * too.
   *
   * @param device the device selected by the user
   * @param target responsible for the boot option text if it's appropriate to display it
   */
  private fun getText(device: Device, target: Target): String {
    val key = if (Devices.containsAnotherDeviceWithSameName(devices, device)) device.key else null
    val bootOption = Devices.getBootOption(device, target)
    return Devices.getText(device, key, bootOption)
  }
}
