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

import com.android.adblib.ConnectedDevice
import com.android.adblib.ShellCommandOutputElement
import com.android.adblib.isOnline
import com.android.adblib.selector
import com.android.adblib.shellAsLines
import com.android.adblib.shellAsText
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.tools.adtui.device.ScreenDiagram
import com.ibm.icu.number.NumberFormatter
import com.ibm.icu.util.MeasureUnit
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import java.awt.Font
import java.time.Duration
import java.util.Locale
import javax.swing.GroupLayout
import javax.swing.LayoutStyle
import kotlin.reflect.KProperty
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

/** A panel within the [DeviceDetailsPanel] that shows a table of information about the device. */
internal class DeviceInfoPanel : JBPanel<DeviceInfoPanel>() {
  val apiLevelLabel = LabeledValue("API level")
  var apiLevel by apiLevelLabel

  val powerLabel = LabeledValue("Power")
  var power by powerLabel

  val resolutionLabel = LabeledValue("Resolution (px)")
  var resolution by resolutionLabel

  val resolutionDpLabel = LabeledValue("Resolution (dp)")
  var resolutionDp by resolutionDpLabel

  val abiListLabel = LabeledValue("ABI list")
  var abiList by abiListLabel

  val availableStorageLabel = LabeledValue("Available storage")
  var availableStorage by availableStorageLabel

  val summarySection =
    InfoSection(
      "Summary",
      listOf(
        apiLevelLabel,
        powerLabel,
        resolutionLabel,
        resolutionDpLabel,
        abiListLabel,
        availableStorageLabel
      )
    )

  val screenDiagram = ScreenDiagram()

  init {
    val layout = GroupLayout(this)

    val horizontalGroup: GroupLayout.Group = layout.createParallelGroup()
    val verticalGroup = layout.createSequentialGroup()

    horizontalGroup.addGroup(
      layout
        .createSequentialGroup()
        .addComponent(summarySection)
        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
        .addComponent(screenDiagram)
    )
    verticalGroup.addGroup(
      layout.createParallelGroup().addComponent(summarySection).addComponent(screenDiagram)
    )

    layout.autoCreateContainerGaps = true
    layout.setHorizontalGroup(horizontalGroup)
    layout.setVerticalGroup(verticalGroup)

    setLayout(layout)
  }
}

internal class InfoSection(heading: String, labeledValues: List<LabeledValue>) :
  JBPanel<InfoSection>() {
  init {
    isOpaque = false

    val headingLabel = headingLabel(heading)

    val labels = labeledValues.map { it.label }.toTypedArray()

    val layout = GroupLayout(this)
    layout.linkSize(*labels)

    val horizontalGroup = layout.createParallelGroup().addComponent(headingLabel)
    val verticalGroup = layout.createSequentialGroup().addComponent(headingLabel)

    for (labeledValue in labeledValues) {
      val label = labeledValue.label
      val value = labeledValue.value
      horizontalGroup.addGroup(
        layout.createSequentialGroup().addComponent(label).addComponent(value)
      )
      verticalGroup.addGroup(layout.createParallelGroup().addComponent(label).addComponent(value))
    }

    layout.autoCreateGaps = true
    layout.setHorizontalGroup(horizontalGroup)
    layout.setVerticalGroup(verticalGroup)

    this.layout = layout
  }
}

/**
 * A pair of JBLabels: a label and a value, e.g. "API level: ", "33".
 *
 * It can be used as a property delegate, such that the value is tied to the property.
 */
class LabeledValue(label: String) {
  operator fun getValue(container: Any, property: KProperty<*>): String {
    return value.text
  }

  operator fun setValue(container: Any, property: KProperty<*>, value: String) {
    this.value.text = value
  }

  val label = JBLabel(label)
  val value = JBLabel()

  var isVisible: Boolean
    get() = label.isVisible
    set(visible) {
      label.isVisible = visible
      value.isVisible = visible
    }
}

internal fun DeviceInfoPanel.populateDeviceInfo(properties: DeviceProperties) {
  apiLevel = properties.androidVersion?.apiStringWithExtension ?: "Unknown"
  abiList = properties.abi?.toString() ?: "Unknown"
  resolution = properties.resolution?.toString() ?: "Unknown"
  val resolutionDp = properties.resolutionDp
  this.resolutionDp = resolutionDp?.toString() ?: "Unknown"
  screenDiagram.setDimensions(resolutionDp?.width ?: 0, resolutionDp?.height ?: 0)
}

internal suspend fun populateDeviceInfo(deviceInfoPanel: DeviceInfoPanel, handle: DeviceHandle) =
  coroutineScope {
    val state = handle.state
    val properties = state.properties
    val device = state.connectedDevice?.takeIf { it.isOnline }

    deviceInfoPanel.populateDeviceInfo(properties)

    launch {
      if (device != null && properties.isVirtual == false) {
        deviceInfoPanel.powerLabel.isVisible = true
        deviceInfoPanel.power = readDevicePower(device)
      } else {
        deviceInfoPanel.powerLabel.isVisible = false
      }
    }

    launch {
      if (device != null) {
        deviceInfoPanel.availableStorage = readDeviceStorage(device)
      }
    }
  }

private suspend fun readDeviceStorage(device: ConnectedDevice): String {
  val output = device.shellStdoutLines("df /data")
  val kilobytes =
    DF_OUTPUT_REGEX.matchEntire(output[1])?.groupValues?.get(1)?.toIntOrNull() ?: return "Unknown"
  return MB_FORMATTER.format(kilobytes / 1024).toString()
}

private suspend fun readDevicePower(device: ConnectedDevice): String {
  val output =
    device.session.deviceServices
      .shellAsText(device.selector, "dumpsys battery", commandTimeout = Duration.ofSeconds(5))
      .stdout
      .trim()

  return when {
    output.contains("Wireless powered: true") -> "Wireless"
    output.contains("AC powered: true") -> "AC"
    output.contains("USB powered: true") -> "USB"
    else -> Regex("level: (\\d+)").find(output)?.groupValues?.get(1)?.let { "Battery: $it" }
        ?: "Unknown"
  }
}

private suspend fun ConnectedDevice.shellStdoutLines(command: String): List<String> =
  session.deviceServices
    .shellAsLines(selector, command, commandTimeout = Duration.ofSeconds(5))
    .transform {
      when (it) {
        is ShellCommandOutputElement.StdoutLine -> emit(it.contents)
        else -> {}
      }
    }
    .toList()

internal fun headingLabel(heading: String) =
  JBLabel(heading).apply { font = font.deriveFont(Font.BOLD) }

private val DF_OUTPUT_REGEX = Regex(""".+\s+\d+\s+\d+\s+(\d+)\s+.+\s+.+""")
private val MB_FORMATTER = NumberFormatter.withLocale(Locale.US).unit(MeasureUnit.MEGABYTE)
