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

import com.android.tools.idea.run.deployment.selector.Devices.getBootOption
import com.intellij.ui.ColorUtil
import com.intellij.ui.SimpleTextAttributes

internal class SelectMultipleDevicesDialogTableModelRow(val device: Device, val target: Target) {
  var isSelected = false

  val deviceCellText: String
    get() {
      val greyColor = ColorUtil.toHtmlColor(SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor)
      return device.launchCompatibility.reason?.let { reason ->
        "<html>$device<br><font size=-2 color=$greyColor>$reason</font></html>"
      } ?: device.name
    }

  val bootOption: String
    get() = getBootOption(device, target) ?: ""
}
