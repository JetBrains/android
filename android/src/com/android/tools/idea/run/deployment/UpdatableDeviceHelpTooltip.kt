/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.deployment

import com.android.tools.idea.run.LaunchCompatibility
import com.intellij.ide.HelpTooltip
import org.jetbrains.android.util.AndroidBundle
import javax.swing.JComponent

internal class UpdatableDeviceHelpTooltip {
  private var myCompatibility: LaunchCompatibility? = null
  private var owner: JComponent? = null

  fun installOn(component: JComponent) {
    owner = component
  }

  fun updateTooltip(device: Device) {
    val compatibility = device.launchCompatibility
    if (owner == null || compatibility == myCompatibility) {
      return
    }

    cancel()
    myCompatibility = compatibility

    val helpTooltip = HelpTooltip()
    if (updateTooltip(device, helpTooltip)) {
      helpTooltip.installOn(owner!!)
    }
  }

  fun cancel() {
    myCompatibility = null
    if (owner != null) {
      HelpTooltip.dispose(owner!!)
    }
  }
}

internal fun updateTooltip(device: Device, helpTooltip: HelpTooltip): Boolean {
  val title = when (device.launchCompatibility.state) {
    LaunchCompatibility.State.OK -> return false
    LaunchCompatibility.State.WARNING -> AndroidBundle.message("warning.level.title")
    LaunchCompatibility.State.ERROR -> AndroidBundle.message("error.level.title")
  }

  helpTooltip.setTitle(title)
  helpTooltip.setDescription(device.launchCompatibility.reason)

  return true
}
