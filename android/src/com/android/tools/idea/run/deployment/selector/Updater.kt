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

import com.intellij.openapi.actionSystem.Presentation
import icons.StudioIcons

internal class Updater(
  private val presentation: Presentation,
  private val devicesAndTargets: DevicesAndTargets,
) {
  fun update() {
    if (devicesAndTargets.isMultipleSelectionMode) {
      presentation.setIcon(StudioIcons.DeviceExplorer.MULTIPLE_DEVICES)
      presentation.putClientProperty(DeviceAndSnapshotComboBoxAction.LAUNCH_COMPATIBILITY_KEY, null)
      presentation.setText("Multiple Devices (" + devicesAndTargets.selectedTargets.size + ")")
    } else {
      val target = devicesAndTargets.selectedTargets.firstOrNull()
      if (target == null) {
        presentation.setIcon(null)
        presentation.text = "No Devices"
      } else {
        presentation.setIcon(target.device.icon)
        presentation.putClientProperty(
          DeviceAndSnapshotComboBoxAction.LAUNCH_COMPATIBILITY_KEY,
          target.device.launchCompatibility
        )
        presentation.setText(target.displayText(), false)
      }
    }
  }

  /**
   * Returns the text to display in the drop down button. It usually indicates the device selected
   * by the user. If there's another device in the drop down with the same name as the selected
   * device, this method appends the selected device's disambiguator to the text, if available. If
   * it's appropriate to display the boot option (Cold Boot, Quick Boot, the name of the snapshot
   * for a snapshot boot), this method appends it to the text. If the underlying machinery has
   * determined a reason why a device isn't valid, this method appends that too.
   *
   * @param device the device selected by the user
   * @param target responsible for the boot option text if it's appropriate to display it
   */
  private fun DeploymentTarget.displayText(): String {
    val disambiguatedName = device.disambiguatedName(devicesAndTargets.allDevices)
    return when {
      bootOption is BootSnapshot -> "$disambiguatedName - ${bootOption.text}}"
      else -> disambiguatedName
    }
  }
}
