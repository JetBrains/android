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

import com.android.adblib.ClosedSessionException
import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceState
import com.android.adblib.ShellCommandOutputElement
import com.android.adblib.scope
import com.android.adblib.selector
import com.android.adblib.shellAsLines
import com.android.adblib.shellAsText
import com.android.annotations.concurrency.UiThread
import com.android.repository.io.recursiveSize
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.LocalEmulatorProperties
import com.android.sdklib.internal.avd.ConfigKey
import com.android.tools.adtui.device.ScreenDiagram
import com.android.tools.adtui.util.getHumanizedSize
import com.android.tools.idea.concurrency.AndroidDispatchers.diskIoThread
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.ibm.icu.number.NumberFormatter
import com.ibm.icu.util.MeasureUnit
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.IOException
import java.text.Collator
import java.time.Duration
import java.util.Formatter
import java.util.Locale
import java.util.TreeMap
import javax.swing.GroupLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.LayoutStyle
import javax.swing.plaf.basic.BasicGraphicsUtils
import kotlin.reflect.KProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

  val densityLabel = LabeledValue("Density")
  var density by densityLabel

  val abiListLabel = LabeledValue("ABI list")
  var abiList by abiListLabel

  val availableStorageLabel = LabeledValue("Available storage")
  var availableStorage by availableStorageLabel

  val sizeOnDiskLabel = LabeledValue("Size on disk").also { it.isVisible = false }
  var sizeOnDisk by sizeOnDiskLabel

  val summarySection =
    InfoSection(
      "Summary",
      listOf(
        apiLevelLabel,
        powerLabel,
        resolutionLabel,
        resolutionDpLabel,
        densityLabel,
        abiListLabel,
        availableStorageLabel,
        sizeOnDiskLabel,
      ),
    )

  var copyPropertiesButton: JComponent = JPanel()
    @UiThread
    set(value) {
      layout.replace(field, value)
      field = value
    }

  var propertiesSection: JComponent = JPanel()
    @UiThread
    set(value) {
      layout.replace(field, value)
      field = value
    }

  val screenDiagram = ScreenDiagram()

  val layout = GroupLayout(this)

  init {
    val horizontalGroup: GroupLayout.Group = layout.createParallelGroup()
    val verticalGroup = layout.createSequentialGroup()

    horizontalGroup
      .addGroup(
        layout
          .createSequentialGroup()
          .addComponent(summarySection)
          .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
          .addComponent(screenDiagram)
      )
      .addComponent(copyPropertiesButton)
      .addComponent(propertiesSection)
    verticalGroup
      .addGroup(
        layout.createParallelGroup().addComponent(summarySection).addComponent(screenDiagram)
      )
      .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
      .addComponent(copyPropertiesButton)
      .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
      .addComponent(propertiesSection)

    layout.autoCreateContainerGaps = true
    layout.setHorizontalGroup(horizontalGroup)
    layout.setVerticalGroup(verticalGroup)

    setLayout(layout)
  }
}

internal class InfoSection(heading: String, private val labeledValues: List<LabeledValue>) :
  JBPanel<InfoSection>() {
  private val headingLabel = headingLabel(heading)

  init {
    isOpaque = false

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

  fun writeTo(buffer: StringBuilder) {
    val formatter = Formatter(buffer)
    formatter.format("%s%n", headingLabel.text)

    val maxLength = labeledValues.maxOfOrNull { it.label.text.length } ?: 1
    val format = "%-" + maxLength + "s %s%n"

    for (labeledValue in labeledValues) {
      formatter.format(format, labeledValue.label.text, labeledValue.value.text)
    }
  }
}

/**
 * A pair of JBLabels: a label and a value, e.g. "API level: ", "33".
 *
 * It can be used as a property delegate, such that the value is tied to the property.
 */
@UiThread
class LabeledValue(label: String) {
  constructor(label: String, value: String) : this(label) {
    this.value.text = value
  }

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
  abiList = properties.primaryAbi?.toString() ?: "Unknown"
  resolution = properties.resolution?.toString() ?: "Unknown"
  val resolutionDp = properties.resolutionDp
  this.resolutionDp = resolutionDp?.toString() ?: "Unknown"
  density = properties.density?.let { "$it dpi" } ?: "Unknown"
  screenDiagram.setDimensions(resolutionDp?.width ?: 0, resolutionDp?.height ?: 0)

  if (properties is LocalEmulatorProperties) {
    val avdConfigProperties =
      properties.avdConfigProperties.filterNotTo(TreeMap(Collator.getInstance())) {
        it.key in EXCLUDED_LOCAL_AVD_PROPERTIES
      }
    if (avdConfigProperties.isNotEmpty()) {
      val values = avdConfigProperties.map { LabeledValue(it.key, it.value) }
      val section = InfoSection("Properties", values)
      copyPropertiesButton = createCopyPropertiesButton(section)
      propertiesSection = section
    }
  }
}

internal suspend fun DeviceInfoPanel.populateSizeOnDiskLabel(properties: DeviceProperties) {
  if (properties is LocalEmulatorProperties) {
    try {
      sizeOnDisk =
        withContext(diskIoThread) { getHumanizedSize(properties.avdPath.recursiveSize()) }
    } catch (e: IOException) {
      logger<DeviceInfoPanel>().warn("Unable to compute size of device ${properties.avdName}")
    }
    sizeOnDiskLabel.isVisible = true
  }
}

private fun createCopyPropertiesButton(infoSection: InfoSection) =
  JButton("Copy properties to clipboard", AllIcons.Actions.Copy).apply {
    border = null
    isContentAreaFilled = false
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED

    val gap = iconTextGap
    val size = BasicGraphicsUtils.getPreferredButtonSize(this, gap)

    maximumSize = size
    minimumSize = size
    preferredSize = size

    addActionListener {
      val text = StringBuilder().also { infoSection.writeTo(it) }.toString()
      Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
  }

private val EXCLUDED_LOCAL_AVD_PROPERTIES =
  setOf(
    ConfigKey.ABI_TYPE,
    ConfigKey.CPU_ARCH,
    ConfigKey.SKIN_NAME,
    ConfigKey.SKIN_PATH,
    ConfigKey.SDCARD_SIZE,
    ConfigKey.SDCARD_PATH,
    ConfigKey.IMAGES_2,
  )

/** Launches a coroutine to monitor the device properties and update details when they change. */
internal fun DeviceInfoPanel.trackDeviceProperties(scope: CoroutineScope, handle: DeviceHandle) {
  scope.launch(uiThread) {
    handle.stateFlow.map { it.properties }.distinctUntilChanged().collect { populateDeviceInfo(it) }
  }
}

/** Launches coroutines to monitor the state of the device power, storage, and size on disk. */
internal fun DeviceInfoPanel.trackDevicePowerAndStorage(
  scope: CoroutineScope,
  handle: DeviceHandle,
) {
  scope.launch(uiThread) {
    handle.stateFlow
      .distinctUntilChangedBy { it.connectedDevice }
      .collectLatest { state ->
        populateSizeOnDiskLabel(state.properties)

        val device = state.connectedDevice
        if (device != null) {
          device.scope.launch(uiThread) {
            device.deviceInfoFlow
              .map { it.deviceState == DeviceState.ONLINE }
              .distinctUntilChanged()
              .collectLatest { isOnline ->
                val isPhysical = state.properties.isVirtual == false
                powerLabel.isVisible = isOnline && isPhysical
                if (isOnline) {
                  if (isPhysical) {
                    powerLabel.update { readDevicePower(device) }
                  }
                  availableStorageLabel.update { readDeviceStorage(device) }
                }
              }
          }
        } else {
          powerLabel.isVisible = false
        }
      }
  }
}

private suspend fun LabeledValue.update(updater: suspend () -> String) {
  value.text =
    runCatching { updater() }
      .onFailure { e ->
        if (e !is ClosedSessionException) {
          logger<DeviceInfoPanel>().warn("Failed to read ${value.text.lowercase()}", e)
        }
      }
      .getOrDefault("Unknown")
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
    output.contains("USB powered: true") -> "USB"
    output.contains("AC powered: true") -> "AC"
    else ->
      Regex("level: (\\d+)").find(output)?.groupValues?.get(1)?.let { "Battery: $it" } ?: "Unknown"
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
