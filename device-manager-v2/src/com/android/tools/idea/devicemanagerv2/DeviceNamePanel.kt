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

import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.Reservation
import com.android.sdklib.getReleaseNameAndDetails
import com.android.tools.adtui.categorytable.IconLabel
import com.android.tools.idea.wearpairing.AndroidWearPairingBundle.Companion.message
import com.android.tools.idea.wearpairing.WearPairingManager.PairingState
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.Color
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.swing.GroupLayout
import javax.swing.LayoutStyle.ComponentPlacement
import kotlin.math.min
import org.jetbrains.annotations.VisibleForTesting

/**
 * A panel that renders the name of the device, along with its wear pairing status and a second line
 * to indicate more details, such as its Android version or an error state.
 */
internal class DeviceNamePanel : JBPanel<DeviceNamePanel>(null) {
  internal val deviceIcon = IconLabel(null)
  internal val twoLineLabel = TwoLineLabel()
  internal val pairedLabel = IconLabel(StudioIcons.LayoutEditor.Toolbar.INSERT_HORIZ_CHAIN)

  init {
    isOpaque = false

    val layout = GroupLayout(this)
    val horizontalGroup =
      layout
        .createSequentialGroup()
        .addComponent(deviceIcon)
        .addPreferredGap(ComponentPlacement.RELATED)
        .addComponent(twoLineLabel)
        .addPreferredGap(ComponentPlacement.RELATED, GroupLayout.DEFAULT_SIZE, Int.MAX_VALUE)
        .addComponent(pairedLabel)
        .addGap(JBUI.scale(4))

    val verticalGroup =
      layout
        .createParallelGroup(GroupLayout.Alignment.CENTER)
        .addComponent(deviceIcon)
        .addGroup(
          layout
            .createSequentialGroup()
            .addContainerGap(GroupLayout.DEFAULT_SIZE, Int.MAX_VALUE)
            .addComponent(twoLineLabel)
            .addContainerGap(GroupLayout.DEFAULT_SIZE, Int.MAX_VALUE)
        )
        .addComponent(pairedLabel)

    layout.setHorizontalGroup(horizontalGroup)
    layout.setVerticalGroup(verticalGroup)

    this.layout = layout
  }

  fun update(deviceRowData: DeviceRowData) {
    deviceIcon.baseIcon = deviceRowData.icon
    twoLineLabel.line1Label.text = deviceRowData.name
    twoLineLabel.line2Label.text = deviceRowData.toLine2Text()
    updatePairingState(deviceRowData.pairingStatus)
  }

  private fun updatePairingState(pairList: List<PairingStatus>) {
    pairedLabel.isVisible = pairList.isNotEmpty()
    if (pairList.isNotEmpty()) {
      pairedLabel.baseIcon =
        when {
          pairList.any { it.state == PairingState.CONNECTED } ->
            StudioIcons.DeviceExplorer.DEVICE_PAIRED_AND_CONNECTED
          else -> StudioIcons.LayoutEditor.Toolbar.INSERT_HORIZ_CHAIN
        }
    }

    fun PairingStatus.pairingStatusString() =
      when (state) {
        PairingState.CONNECTED ->
          message("wear.assistant.device.list.accessible.description.status.connected", displayName)
        else ->
          message("wear.assistant.device.list.accessible.description.status.paired", displayName)
      }

    pairedLabel.accessibleContext.accessibleDescription =
      when {
        pairList.isEmpty() -> null
        else -> pairList.joinToString(separator = "\n") { it.pairingStatusString() }
      }
  }

  /**
   * Returns the appropriate text for the second line of the device cell, using the following in
   * order:
   * 1. The status message, if the device state is transitioning and one is present.
   * 2. The error message, if there's an error.
   * 3. Reservation information, if present.
   * 4. Android version
   */
  private fun DeviceRowData.toLine2Text() =
    stateTransitionText() ?: errorText() ?: reservationText() ?: androidVersionText()

  private fun DeviceRowData.errorText() = error?.message

  private fun DeviceRowData.reservationText() = handle?.state?.reservation?.line2Text()

  private fun DeviceRowData.androidVersionText() =
    when (androidVersion) {
      null -> ""
      else -> androidVersion.toLabelText() + (abi?.cpuArch?.let { " | $it" } ?: "")
    }
}

internal fun AndroidVersion.toLabelText(): String {
  val (name, details) = getReleaseNameAndDetails(includeCodeName = true)
  return name + (details?.let { " ($details)" } ?: "")
}

/**
 * Makes this color closer to the background color (lighter in light theme, darker in dark theme).
 */
@VisibleForTesting
internal fun Color.lighten() =
  JBColor.lazy {
    // Color.brigher() on black only takes us from 0x000000 to 0x030303; even +50 is rather subtle.
    val red = min(red + 50, 255)
    val green = min(green + 50, 255)
    val blue = min(blue + 50, 255)
    JBColor(Color(red, green, blue), darker())
  }

internal fun DeviceRowData.stateTransitionText() =
  handle?.state?.takeIf { it.isTransitioning }?.status?.takeIf { it.isNotEmpty() }

internal fun Reservation.line2Text(zoneId: ZoneId = ZoneId.systemDefault()): String? =
  when {
    endTime != null -> {
      val formattedDate =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zoneId).format(endTime)
      when {
        stateMessage.isEmpty() -> "Device will expire at $formattedDate"
        else -> "${stateMessage}; device will expire at $formattedDate"
      }
    }
    stateMessage.isNotEmpty() -> stateMessage
    else -> null
  }
